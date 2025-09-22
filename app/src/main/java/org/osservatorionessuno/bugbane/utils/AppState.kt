package org.osservatorionessuno.bugbane.utils

// AppStates represent the high-level device configuration and permissions status;
// state ordering/requisites are defined in the ViewModel (see ConfigurationViewModel).
// An AppState requires user interaction to change to a different state.
enum class AppState(val index: Int) {
    NeedWelcomeScreen(0), // one-time consent
    NeedNotificationConfiguration(1),
    NeedWifi(2),
    NeedDeveloperOptions(3),
    NeedWirelessDebuggingAndPair(4), // sub-states are handled by AdbManager
    AdbConnectedFinishOnboarding(5), // one-time onboarding complete
    AdbConnected(6),
    TryAutoConnect(7),
    AdbConnecting(8),
    AdbScanning(9),
    AdbConnectionError(10),
    NeedWirelessDebugging(11),
    DeviceUnsupported(12);

    companion object {
        fun valuesInOrder(): List<AppState> = listOf(
            NeedWelcomeScreen,
            NeedNotificationConfiguration,
            NeedWifi,
            NeedDeveloperOptions,
            NeedWirelessDebuggingAndPair,
            TryAutoConnect,
            AdbConnecting,
            AdbConnectedFinishOnboarding,
            AdbConnected,
            AdbScanning,
            DeviceUnsupported,
            )

        // These aren't shown in the slideshow
        fun hiddenStates(): List<AppState> = listOf(
            AdbScanning,
            AdbConnected,
            TryAutoConnect,
            AdbConnectionError // for now
        )
        // Error states have different UI implications
        fun isErrorState(state: AppState): Boolean {
            return (state in arrayOf(DeviceUnsupported, AdbConnectionError))
        }
    }
}
