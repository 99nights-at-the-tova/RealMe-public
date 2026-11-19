package com.realme.core.util

import java.time.Instant
import java.time.LocalDate
import com.realme.core.model.JST

interface TimeProvider {
    fun now(): Instant
    fun todayJst(): LocalDate = now().atZone(JST).toLocalDate()
}

object SystemTimeProvider : TimeProvider {
    override fun now(): Instant = Instant.now()
}
