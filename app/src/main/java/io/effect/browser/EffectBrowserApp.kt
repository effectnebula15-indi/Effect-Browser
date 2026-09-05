package io.effect.browser

import android.app.Application
import io.effect.browser.di.ServiceLocator

/**
 * Runs in **both** processes. [ServiceLocator.init] inspects which one it is in and builds the
 * matching graph — that is where the direct/tor split actually happens.
 */
class EffectBrowserApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
