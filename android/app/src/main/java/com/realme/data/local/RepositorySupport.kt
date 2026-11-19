package com.realme.data.local

import kotlinx.coroutines.CancellationException

internal suspend inline fun <T> repositoryResult(
    crossinline block: suspend () -> T,
): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: Exception) {
    Result.failure(error)
}
