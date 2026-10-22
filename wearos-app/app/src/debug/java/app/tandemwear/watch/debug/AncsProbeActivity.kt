package app.tandemwear.watch.debug

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.tandemwear.watch.ancs.AncsService
import app.tandemwear.watch.battery.BatteryPolicy
import app.tandemwear.watch.data.BatteryModeStore
import app.tandemwear.watch.data.CallDebugStore
import app.tandemwear.watch.data.PairedPhoneStore
import app.tandemwear.watch.diag.DiagnosticsLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Debug-only end-to-end check of the real pairing + ANCS pipeline (not shipped in release).
 *
 * "Pair" opens the service's pairing window (the watch advertises); pair from iPhone ›
 * Settings › Bluetooth. The screen mirrors [DiagnosticsLog]: advertising, inbound link,
 * bonding and identity address, ANCS subscription, and the first real notification.
 * Same lines in logcat: `adb logcat -s TandemWear:I`, and in files/diag/tandemwear.log*
 * ("Exportar log" copies them to Download/).
 */
class AncsProbeActivity : Activity() {

    companion object {
        private const val PERMISSION_REQUEST = 1
        private val HIGHLIGHTS = listOf("SESSION ACTIVE", "FIRST NOTIFICATION", "REPAIR NEEDED", "FAILED")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var logView: TextView
    private lateinit var summaryView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val pad = (resources.displayMetrics.density * 24).toInt()
        summaryView = TextView(this).apply {
            textSize = 12f
            gravity = Gravity.CENTER
        }
        logView = TextView(this).apply { textSize = 10f }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad, pad, pad * 2)
            addView(TextView(this@AncsProbeActivity).apply {
                text = "TandemWear ANCS probe"
                textSize = 14f
                gravity = Gravity.CENTER
            })
            addView(summaryView)
            addView(Button(this@AncsProbeActivity).apply {
                text = "Pair (advertise)"
                setOnClickListener { sendToService(AncsService.ACTION_START_PAIRING) }
            })
            addView(Button(this@AncsProbeActivity).apply {
                text = "Reconnect"
                setOnClickListener { sendToService(AncsService.ACTION_START) }
            })
            addView(Button(this@AncsProbeActivity).apply {
                // Isolate who reconnects: the iPhone (watch advertises) or the watch (autoConnect)
                val store = PairedPhoneStore(this@AncsProbeActivity)
                text = "Reconnect via: ${store.reconnectStrategy}"
                setOnClickListener {
                    val all = PairedPhoneStore.ReconnectStrategy.entries
                    val next = all[(all.indexOf(store.reconnectStrategy) + 1) % all.size]
                    store.reconnectStrategy = next
                    text = "Reconnect via: $next"
                    DiagnosticsLog.log("probe: reconnect strategy = $next")
                    sendToService(AncsService.ACTION_START)
                }
            })
            addView(Button(this@AncsProbeActivity).apply {
                // Measure what the watch's Telecom adds to incoming calls before removing it
                val calls = CallDebugStore(this@AncsProbeActivity)
                text = "Calls via Telecom: ${if (calls.useTelecom) "ON" else "OFF"}"
                setOnClickListener {
                    calls.useTelecom = !calls.useTelecom
                    text = "Calls via Telecom: ${if (calls.useTelecom) "ON" else "OFF"}"
                    DiagnosticsLog.log("probe: calls via Telecom = ${calls.useTelecom}")
                }
            })
            addView(Button(this@AncsProbeActivity).apply {
                // If subscribing to the iPhone's battery buzzes the watch, switch to polling (15 min)
                val battery = BatteryModeStore(this@AncsProbeActivity)
                text = "iPhone battery: ${battery.mode}"
                setOnClickListener {
                    val all = BatteryPolicy.Mode.entries
                    battery.mode = all[(all.indexOf(battery.mode) + 1) % all.size]
                    text = "iPhone battery: ${battery.mode}"
                    DiagnosticsLog.log("probe: battery mode = ${battery.mode} (next connection)")
                }
            })
            addView(Button(this@AncsProbeActivity).apply {
                // Saves files/diag/tandemwear.log* (oldest first) to Download/ on the watch
                text = "Exportar log"
                setOnClickListener { exportLog() }
            })
            addView(Button(this@AncsProbeActivity).apply {
                text = "Clear log"
                setOnClickListener { DiagnosticsLog.clear() }
            })
            addView(logView)
        }
        setContentView(ScrollView(this).apply { addView(column) })

        scope.launch {
            DiagnosticsLog.lines.collect { lines ->
                logView.text = lines.joinToString("\n")
                val milestones = lines.filter { line -> HIGHLIGHTS.any { line.contains(it) } }
                summaryView.text = milestones.lastOrNull() ?: "Tap Pair, then pair from the iPhone"
                summaryView.setTextColor(
                    when {
                        milestones.lastOrNull()?.contains("FIRST NOTIFICATION") == true -> Color.GREEN
                        milestones.lastOrNull()?.contains("SESSION ACTIVE") == true -> Color.CYAN
                        milestones.isNotEmpty() -> Color.YELLOW
                        else -> Color.LTGRAY
                    }
                )
            }
        }

        val missing = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.POST_NOTIFICATIONS
        ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), PERMISSION_REQUEST)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun exportLog() {
        scope.launch {
            val result = withContext(Dispatchers.IO) { LogExporter.exportToDownloads(this@AncsProbeActivity) }
            DiagnosticsLog.log("probe: $result")
        }
    }

    private fun sendToService(action: String) {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            DiagnosticsLog.log("probe: grant the Bluetooth permissions first")
            return
        }
        startForegroundService(Intent(this, AncsService::class.java).setAction(action))
    }
}
