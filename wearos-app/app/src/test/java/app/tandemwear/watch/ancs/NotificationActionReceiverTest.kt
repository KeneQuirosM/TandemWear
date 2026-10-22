package app.tandemwear.watch.ancs

import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** SDK 34: Robolectric's SDK 35 image needs JDK 21. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationActionReceiverTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `action intents name the receiver explicitly`() {
        for (action in listOf(NotificationActionReceiver.ACTION_POSITIVE, NotificationActionReceiver.ACTION_NEGATIVE)) {
            val intent = NotificationActionReceiver.intent(context, action, uid = 42)
            assertEquals(ComponentName(context, NotificationActionReceiver::class.java), intent.component)
            assertEquals(action, intent.action)
            assertEquals(42L, intent.getLongExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_UID, -1))
        }
    }

    @Test
    fun `explicit intent resolves to the manifest receiver`() {
        val intent = NotificationActionReceiver.intent(context, NotificationActionReceiver.ACTION_NEGATIVE, uid = 1)
        val receivers = context.packageManager.queryBroadcastReceivers(intent, 0)
        assertTrue(receivers.any { it.activityInfo.name == NotificationActionReceiver::class.java.name })
    }
}
