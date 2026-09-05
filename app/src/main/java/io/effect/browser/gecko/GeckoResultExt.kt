package io.effect.browser.gecko

import kotlinx.coroutines.suspendCancellableCoroutine
import org.mozilla.geckoview.GeckoResult
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Bridges GeckoView's [GeckoResult] callback type into a suspending call. */
suspend fun <T> GeckoResult<T>.await(): T? = suspendCancellableCoroutine { cont ->
    accept(
        { value -> if (cont.isActive) cont.resume(value) },
        { error ->
            if (cont.isActive) {
                cont.resumeWithException(error ?: IllegalStateException("GeckoResult failed"))
            }
        },
    )
    cont.invokeOnCancellation { cancel() }
}
