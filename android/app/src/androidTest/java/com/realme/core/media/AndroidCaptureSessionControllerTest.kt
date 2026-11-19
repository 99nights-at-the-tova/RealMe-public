package com.realme.core.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidCaptureSessionControllerTest {
    @Test
    fun stoppedOrNotRunningSessionCompletesImmediately() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(AndroidCaptureSessionController(context).stopAndAwait())
    }

    @Test
    fun activeDebugSessionIsStoppedBeforeControllerReturns() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        CaptureSessionState.startTestSession()
        try {
            assertTrue(AndroidCaptureSessionController(context).stopAndAwait())
            assertFalse(CaptureSessionState.active.value)
            assertTrue(
                "Pending capture cleanup must finish before stop returns",
                CaptureSessionState.testCleanupCompleted,
            )
        } finally {
            CaptureSessionState.detach()
        }
    }
}
