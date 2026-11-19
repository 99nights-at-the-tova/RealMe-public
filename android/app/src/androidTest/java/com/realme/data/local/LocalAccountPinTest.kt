package com.realme.data.local

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.realme.core.util.TimeProvider
import com.realme.feature.auth.AuthViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LocalAccountPinTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun pinChangeAttemptsShareTheLockoutAndCannotBypassIt() = withRepository { repository, clock ->
        val account = repository.signInAnonymously("Settings lock").getOrThrow()
        repository.setAccountPin(account.id, null, "1234").getOrThrow()
        repeat(5) { assertTrue(repository.setAccountPin(account.id, "0000", "5678").isFailure) }
        assertTrue(repository.setAccountPin(account.id, "1234", null).isFailure)
        assertTrue(repository.verifyPin(account.id, "1234").isFailure)
        clock.advanceSeconds(31)
        repository.setAccountPin(account.id, "1234", null).getOrThrow()
        assertFalse(repository.requiresPin(account.id))
    }

    @Test
    fun repeatedWrongPinsTemporarilyLockAccount() = withRepository { repository, clock ->
        val account = repository.signInAnonymously("Locked").getOrThrow()
        repository.setAccountPin(account.id, null, "1234").getOrThrow()
        repository.signOut()

        repeat(4) {
            assertTrue(repository.signInExisting(account.id, "0000").isFailure)
        }
        val lockFailure = repository.signInExisting(account.id, "0000").exceptionOrNull()
        assertTrue(lockFailure?.message?.contains("30秒") == true)
        assertTrue(repository.signInExisting(account.id, "1234").isFailure)

        clock.advanceSeconds(31)
        assertEquals(account.id, repository.signInExisting(account.id, "1234").getOrThrow().id)
    }

    @Test
    fun activeSessionWithPinRequiresUnlockOnNewViewModel() = withRepository { repository, _ ->
        val account = repository.signInAnonymously("Session").getOrThrow()
        repository.setAccountPin(account.id, null, "1234").getOrThrow()
        val viewModel = AuthViewModel(repository)
        val locked = withTimeout(5_000) {
            viewModel.uiState.first { it.lockedAccount != null }
        }
        assertEquals(account.id, locked.lockedAccount?.id)
        assertEquals(account.id, locked.session?.userId)

        viewModel.unlock("1234")
        val unlocked = withTimeout(5_000) {
            viewModel.uiState.first { it.lockedAccount == null && !it.isCheckingSession }
        }
        assertEquals(account.id, unlocked.session?.userId)
    }

    @Test
    fun concurrentPinChangesCannotBothUseTheOldPin() = withRepository { repository, _ ->
        val account = repository.signInAnonymously("Concurrent").getOrThrow()
        repository.setAccountPin(account.id, null, "1234").getOrThrow()
        val results = coroutineScope {
            val first = async(Dispatchers.Default) { repository.setAccountPin(account.id, "1234", "5678") }
            val second = async(Dispatchers.Default) { repository.setAccountPin(account.id, "1234", "9876") }
            listOf(first.await(), second.await())
        }
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, results.count { it.isFailure })
        assertTrue(repository.verifyPin(account.id, "1234").isFailure)
        assertEquals(1, listOf("5678", "9876").count { repository.verifyPin(account.id, it).isSuccess })
    }

    @Test
    fun accountWithPinRequiresCorrectPinAndCanChangeOrRemoveIt() = withRepository { repository, _ ->
        val first = repository.signInAnonymously("First").getOrThrow()
        repository.setAccountPin(first.id, null, "1234").getOrThrow()
        assertTrue(repository.requiresPin(first.id))
        assertTrue(first.id in repository.pinProtectedAccountIds.first())
        repository.signOut()
        val second = repository.signInAnonymously("Second").getOrThrow()
        repository.signOut()

        assertTrue(repository.signInExisting(first.id).isFailure)
        assertTrue(repository.signInExisting(first.id, "9999").isFailure)
        assertEquals(first.id, repository.signInExisting(first.id, "1234").getOrThrow().id)
        repository.signOut()

        assertTrue(repository.setAccountPin(first.id, "0000", "5678").isFailure)
        repository.setAccountPin(first.id, "1234", "5678").getOrThrow()
        assertTrue(repository.verifyPin(first.id, "1234").isFailure)
        repository.verifyPin(first.id, "5678").getOrThrow()
        repository.setAccountPin(first.id, "5678", null).getOrThrow()
        assertFalse(repository.requiresPin(first.id))
        assertEquals(second.id, repository.signInExisting(second.id).getOrThrow().id)
    }

    private fun withRepository(block: suspend (LocalAuthRepository, TestClock) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(context.cacheDir, "realme-pin-test-${UUID.randomUUID()}.preferences_pb")
        }
        val clock = TestClock()
        val repository = LocalAuthRepository(LocalAppStore(context, dataStore), clock)
        try {
            runBlocking { block(repository, clock) }
        } finally {
            scope.cancel()
        }
    }

    private class TestClock : TimeProvider {
        private var current = Instant.parse("2026-11-15T10:00:00Z")
        override fun now(): Instant = current
        fun advanceSeconds(seconds: Long) {
            current = current.plusSeconds(seconds)
        }
    }
}
