package com.ewbible.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ewbible.app.data.ai.AiRepository
import com.ewbible.app.data.database.AppDatabase
import com.ewbible.app.data.preferences.AppPreferences
import com.ewbible.app.data.sync.SyncManager
import com.ewbible.app.ui.screens.HomeScreen
import com.ewbible.app.ui.theme.EWBibleTheme
import com.ewbible.app.ui.viewmodel.ChatViewModel
import com.ewbible.app.ui.viewmodel.HomeViewModel
import com.ewbible.app.ui.viewmodel.HomeViewModelFactory

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        CrashReporter.breadcrumb(this, "Notification permission granted=$granted")
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun isCurrentlyOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        askNotificationPermission()

        CrashReporter.breadcrumb(this, "MainActivity.onCreate")

        val savedCrash = CrashReporter.getLastCrash(this)
            ?: CrashReporter.getLastNonFatal(this)
        if (savedCrash != null) {
            CrashReporter.breadcrumb(this, "App restarted after crash")
        }

        val preferences = AppPreferences(applicationContext)
        val aiRepository = AiRepository()
        val syncManager = SyncManager()

        var dbError: String? = null
        val dao = try {
            AppDatabase.getInstance(applicationContext).bibleDao()
        } catch (e: Throwable) {
            dbError = e.toString() + "\n" + (e.cause?.toString() ?: "")
            CrashReporter.recordNonFatal(this, "MainActivity-DB-Init", e)
            CrashReporter.breadcrumb(this, "DB INIT FAILED: ${e.message}")
            null
        }

        setContent {
            val isDarkMode by preferences.isDarkMode.collectAsState(initial = null)
            val useDarkTheme = isDarkMode ?: false

            var showCrashDialog by remember { mutableStateOf(savedCrash != null) }
            var crashText by remember { mutableStateOf(savedCrash ?: "") }

            EWBibleTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (dbError != null) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Database failed to open.\n\n$dbError\n\nTake a screenshot, or copy this from the crash report.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else if (dao != null) {
                        val homeFactory = remember(dao) {
                            HomeViewModelFactory(
                                dao = dao,
                                preferences = preferences,
                                syncManager = syncManager
                            )
                        }
                        val chatFactory = remember(dao, aiRepository) {
                            object : ViewModelProvider.Factory {
                                @Suppress("UNCHECKED_CAST")
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                                        return ChatViewModel(
                                            bibleDao = dao,
                                            geminiRepository = aiRepository,
                                            syncManager = syncManager
                                        ) as T
                                    }
                                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                                }
                            }
                        }

                        val homeViewModel: HomeViewModel = viewModel(factory = homeFactory)
                        val chatViewModel: ChatViewModel = viewModel(factory = chatFactory)

                        // ✅ FIX: Tell HomeViewModel when network changes
                        // This is what makes SettingsPanel show "Not yet synced" offline
                        DisposableEffect(homeViewModel) {
                            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

                            // Set initial state immediately
                            homeViewModel.setOnline(isCurrentlyOnline())

                            val callback = object : ConnectivityManager.NetworkCallback() {
                                override fun onAvailable(network: Network) {
                                    homeViewModel.setOnline(true)
                                }
                                override fun onLost(network: Network) {
                                    homeViewModel.setOnline(false)
                                }
                                override fun onCapabilitiesChanged(
                                    network: Network,
                                    networkCapabilities: NetworkCapabilities
                                ) {
                                    val hasInternet = networkCapabilities.hasCapability(
                                        NetworkCapabilities.NET_CAPABILITY_VALIDATED
                                    )
                                    homeViewModel.setOnline(hasInternet)
                                }
                            }

                            val request = NetworkRequest.Builder()
                                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                                .build()
                            cm.registerNetworkCallback(request, callback)

                            onDispose {
                                runCatching { cm.unregisterNetworkCallback(callback) }
                            }
                        }

                        HomeScreen(
                            viewModel = homeViewModel,
                            chatViewModel = chatViewModel
                        )
                    }

                    if (showCrashDialog) {
                        CrashReportDialog(
                            crashText = crashText,
                            onCopy = { },
                            onClear = {
                                CrashReporter.clear(this@MainActivity)
                                crashText = ""
                                showCrashDialog = false
                            },
                            onDismiss = { showCrashDialog = false }
                        )
                    }
                }
            }
        }
    }
}