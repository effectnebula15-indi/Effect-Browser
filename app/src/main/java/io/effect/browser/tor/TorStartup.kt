package io.effect.browser.tor

import android.content.Context
import androidx.startup.AppInitializer
import androidx.startup.Initializer

/**
 * Runs kmp-tor's `androidx.startup` initializer by hand in the `:tor` process.
 *
 * kmp-tor captures its application Context and locates `libtor.so` from startup Initializers
 * registered as meta-data on `androidx.startup.InitializationProvider`. That provider is declared
 * by the AndroidX AAR with no `android:process`, so Android only instantiates it in the app's
 * default process — and a ContentProvider cannot be declared twice (the manifest merger keys
 * providers on class name, so a second element with another authority is rejected).
 *
 * Left alone, `TorServiceConfig.newEnvironment()` in the `:tor` process would throw because its
 * captured Context is null. Initialising the component explicitly gets the same effect through
 * public API; [AppInitializer.initializeComponent] also pulls in the initializer's declared
 * dependencies, which is what locates the native library.
 *
 * The class is looked up reflectively only because kmp-tor marks it `internal`, so Kotlin will
 * not let us name it directly even though it is public in the bytecode.
 */
internal object TorStartup {

    private const val INITIALIZER =
        "io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig\$Initializer"

    /**
     * @throws IllegalStateException if kmp-tor's initializer cannot be found or run, which would
     *   otherwise surface later as an opaque failure inside [TorController].
     */
    fun ensureInitialized(context: Context) {
        try {
            @Suppress("UNCHECKED_CAST")
            val component = Class.forName(INITIALIZER) as Class<out Initializer<Any>>
            AppInitializer.getInstance(context).initializeComponent(component)
        } catch (t: Throwable) {
            throw IllegalStateException(
                "Could not initialise kmp-tor in the tor process. If kmp-tor moved or renamed " +
                    "$INITIALIZER, this lookup needs updating.",
                t,
            )
        }
    }
}
