package app.tandemwear.watch.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.wear.ambient.AmbientLifecycleObserver
import app.tandemwear.watch.ui.home.AmbientScreen
import app.tandemwear.watch.ui.nav.TandemNavHost
import app.tandemwear.watch.ui.theme.TandemWearTheme
import app.tandemwear.watch.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Ambient (always-on): null when interactive
    private val ambientDetails = mutableStateOf<AmbientLifecycleObserver.AmbientDetails?>(null)
    private val ambientTick = mutableIntStateOf(0)
    private val ambientObserver = AmbientLifecycleObserver(
        this,
        object : AmbientLifecycleObserver.AmbientLifecycleCallback {
            override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
                this@MainActivity.ambientDetails.value = ambientDetails
            }

            override fun onUpdateAmbient() {
                ambientTick.intValue++
            }

            override fun onExitAmbient() {
                ambientDetails.value = null
            }
        }
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            viewModel.startService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycle.addObserver(ambientObserver)
        requestPermissionsIfNeeded()

        setContent {
            TandemWearTheme {
                Box {
                    // The navigation stays composed under the ambient view, so state survives
                    TandemNavHost(viewModel = viewModel)
                    ambientDetails.value?.let { details ->
                        val status by viewModel.homeStatus.collectAsState()
                        AmbientScreen(status, details.burnInProtectionRequired, ambientTick.intValue)
                    }
                }
            }
        }
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.POST_NOTIFICATIONS,
        )

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isEmpty()) {
            viewModel.startService()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}
