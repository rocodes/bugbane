package org.osservatorionessuno.bugbane.utils

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "ConfigurationManager"

/**
 * Observe system configuration (developer mode, ADB, wireless debug) and emit values as StateFlow.
 * Also observes state of notification permissions, but those can't be subscribed to except in
 * a Composable class (with rememberPermission), so expose a handler to subscribe for updates.
 *
 * Content observers observe in a coroutine off the main thread, and emit updates on the main
 * thread so that viewmodel can react.
 *
 * Requires initialization and cleanup due to registering ContentObservers.
 */
object ConfigurationManager {

    private lateinit var appContext: Context
    private val contentResolver get() = appContext.contentResolver

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _developerOptionsEnabled = MutableStateFlow(false)
    val developerOptionsEnabled: StateFlow<Boolean> = _developerOptionsEnabled.asStateFlow()

    private val _wirelessDebuggingEnabled = MutableStateFlow(false)
    val wirelessDebuggingEnabled: StateFlow<Boolean> = _wirelessDebuggingEnabled.asStateFlow()

    // note: this is included but not used right now because it's a supported API
    // while the adb_wifi_enabled one is more accurate but not supported.
    private val _adbEnabled = MutableStateFlow(false)
    val adbEnabled: StateFlow<Boolean> = _adbEnabled.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(false)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private var developerOptsObserver: ContentObserver? = null
    private var wirelessDebugObserver: ContentObserver? = null

    // we don't need this one as much if we listen directly for wireless debug,
    // but that is a non officially supported check, while this has a supported api.
    private var adbObserver: ContentObserver? = null

    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var _appProgress: MutableStateFlow<AppProgress>
    lateinit var appProgress: StateFlow<AppProgress>
    private var sharedPrefsListener: SharedPreferences.OnSharedPreferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            AppProgress.KEY_HAS_SEEN_HOMEPAGE, AppProgress.KEY_HAS_SEEN_WELCOME_SCREEN -> {
                appProgressCheck()
            }
        }
    }


    fun initialize(context: Context) {
        Log.d(TAG, "Initializing")
        if (!::appContext.isInitialized) {
            appContext = context.applicationContext
            sharedPrefs = context.getSharedPreferences(AppProgress.Keys.PREFS_NAME, Context.MODE_PRIVATE)
            _appProgress = MutableStateFlow(
                AppProgress(
                    hasCompletedOnboarding = sharedPrefs.getBoolean(AppProgress.Keys.KEY_HAS_SEEN_HOMEPAGE, false),
                    hasSeenWelcomeScreen = sharedPrefs.getBoolean(AppProgress.Keys.KEY_HAS_SEEN_WELCOME_SCREEN, false)
                )
            )
            appProgress = _appProgress.asStateFlow()
            Log.d(TAG, "appProgress=${_appProgress.value}")
            registerObservers()
            checkAll()
        } else {
            Log.e(TAG, "Failed to initialize ConfigurationManager.")
            // TODO raise
        }
    }

    data class AppProgress(val hasCompletedOnboarding: Boolean, val hasSeenWelcomeScreen: Boolean) {
        companion object Keys {
            const val PREFS_NAME = "app_prefs"

            // Skips the logo/splashscreen page after the first onboarding flow
            const val KEY_HAS_SEEN_WELCOME_SCREEN = "has_seen_welcome_screen"

            // Skips the "Get Started" page after the first onboarding flow
            const val KEY_HAS_SEEN_HOMEPAGE = "has_seen_homepage"
        }
    }

    /**
     * Register ContentObservers for specific system preferences
     * (https://developer.android.com/reference/kotlin/android/database/ContentObserver)
     */
    private fun registerObservers() {
        developerOptsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = developerOptionsCheck()
        }.also {
            contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
                false,
                it
            )
        }

        wirelessDebugObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = wirelessDebugCheck()
        }.also {
            contentResolver.registerContentObserver(
                Settings.Global.getUriFor("adb_wifi_enabled"),
                false,
                it
            )
        }

        adbObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = adbObserverCheck()
        }.also {
            contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ADB_ENABLED),
                false,
                it
            )
        }

        sharedPrefs = appContext.getSharedPreferences(AppProgress.PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.registerOnSharedPreferenceChangeListener(sharedPrefsListener)

    }

    private fun checkAll() {
        developerOptionsCheck()
        wirelessDebugCheck()
        notificationsCheck()
        adbObserverCheck()
        appProgressCheck()
    }

    private fun developerOptionsCheck() {
        scope.launch {
            val enabled = Settings.Global.getInt(
                contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
            ) == 1
            _developerOptionsEnabled.emit(enabled)
        }
    }

    // A bit dirty:
    // https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/provider/Settings.java;l=13465?q=adb_wifi_enabled&ss=android%2Fplatform%2Fsuperproject%2Fmain
    private fun wirelessDebugCheck() {
        scope.launch {
            val enabled = Settings.Global.getInt(
                contentResolver,
                "adb_wifi_enabled", 0
            ) == 1
            _wirelessDebuggingEnabled.emit(enabled)
        }
    }

    private fun adbObserverCheck() {
        scope.launch {
            val enabled =
                Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
            _adbEnabled.emit(enabled)
        }
    }

    private fun notificationsCheck() {
        val enabled = NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        _notificationsEnabled.value = enabled
    }

    private fun appProgressCheck() {
        val newState = AppProgress(
            hasCompletedOnboarding = sharedPrefs.getBoolean(AppProgress.Keys.KEY_HAS_SEEN_HOMEPAGE, false),
            hasSeenWelcomeScreen = sharedPrefs.getBoolean(AppProgress.Keys.KEY_HAS_SEEN_WELCOME_SCREEN, false)
        )
        if (_appProgress.value != newState) {
            _appProgress.value = newState
        }
    }


    // To make notifications changes also update dynamically, a composable needs to rememberPermissionState
    // and invoke this when the value changes.
    fun setHasNotificationPermission(granted: Boolean) {
        _notificationsEnabled.value = granted
    }

    fun openDeveloperOptions(context: Context) {
        // Open the developer options settings
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
        }
    }

    fun cleanup() {
        developerOptsObserver?.let {
            contentResolver.unregisterContentObserver(it)
            developerOptsObserver = null
        }

        wirelessDebugObserver?.let {
            contentResolver.unregisterContentObserver(it)
            wirelessDebugObserver = null
        }

        adbObserver?.let {
            contentResolver.unregisterContentObserver(it)
            adbObserver = null
        }

        sharedPrefs.unregisterOnSharedPreferenceChangeListener(sharedPrefsListener)

        scope.cancel()
    }

}
