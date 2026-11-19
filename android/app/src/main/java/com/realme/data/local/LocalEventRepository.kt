package com.realme.data.local

import com.realme.core.model.DailyEvent
import com.realme.core.model.DailyEventFactory
import com.realme.core.model.PostStatus
import com.realme.data.repository.EventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class LocalEventRepository(
    private val store: LocalAppStore,
    private val onDemoEvent: (String, DailyEvent) -> Unit = { _, _ -> },
) : EventRepository {
    override fun observeEvent(date: LocalDate): Flow<DailyEvent> = store.snapshots.map { snapshot ->
        snapshot.events.firstOrNull { it.eventDateJst == date } ?: DailyEventFactory.forDate(date)
    }

    override suspend fun ensureEvent(date: LocalDate): DailyEvent {
        var event = DailyEventFactory.forDate(date)
        store.update { snapshot ->
            val existing = snapshot.events.firstOrNull { it.eventDateJst == date }
            if (existing != null) {
                event = existing
                snapshot
            } else {
                snapshot.copy(events = snapshot.events + event)
            }
        }
        return event
    }

    suspend fun deliverReminderOnce(userId: String, event: DailyEvent, deliver: () -> Boolean): Boolean {
        val key = "$userId|${event.eventDateJst}|${event.notificationAt.epochSecond}"
        var delivered = false
        store.update { snapshot ->
            val current = snapshot.events.firstOrNull { it.id == event.id }
            if (snapshot.session?.userId != userId || current != event || key in snapshot.deliveredCaptureReminders) {
                snapshot
            } else if (deliver()) {
                delivered = true
                snapshot.copy(deliveredCaptureReminders = snapshot.deliveredCaptureReminders + key)
            } else {
                snapshot
            }
        }
        return delivered
    }

    suspend fun hasDeliveredReminder(userId: String, event: DailyEvent): Boolean =
        "$userId|${event.eventDateJst}|${event.notificationAt.epochSecond}" in store.current().deliveredCaptureReminders

    override suspend fun startDemoEvent(
        date: LocalDate,
        status: PostStatus,
        now: Instant,
    ): Result<DailyEvent> = repositoryResult {
        require(status == PostStatus.ON_TIME || status == PostStatus.LATE) {
            "デモはON TIMEまたはLATEを選択してください"
        }
        val offset = if (status == PostStatus.ON_TIME) Duration.ofMinutes(2) else Duration.ofMinutes(10)
        val event = DailyEventFactory.forDate(date, now.minus(offset))
        val updated = store.update { snapshot ->
            snapshot.copy(events = snapshot.events.filterNot { it.eventDateJst == date } + event)
        }
        updated.session?.userId?.let { onDemoEvent(it, event) }
        event
    }

    override suspend fun startNotificationNow(date: LocalDate, now: Instant): Result<DailyEvent> =
        repositoryResult {
            val event = DailyEventFactory.forDate(date, now)
            store.update { snapshot ->
                snapshot.copy(events = snapshot.events.filterNot { it.eventDateJst == date } + event)
            }
            event
        }
}
