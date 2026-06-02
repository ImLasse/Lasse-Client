package de.lasse.lasseclient.client.event

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Minimal event bus. Listeners register via [on], publishers call [fire].
 *
 * Listeners are stored per-type in a copy-on-write list so dispatch is lock-free.
 * Exceptions thrown by a listener are caught and logged so one bad subscriber does
 * not kill the whole dispatch chain (especially important when we're firing from a
 * mixin on the network thread).
 */
@Environment(EnvType.CLIENT)
object EventBus {

    private val listeners: MutableMap<Class<*>, CopyOnWriteArrayList<(Any) -> Unit>> = HashMap()

    inline fun <reified T : Any> on(noinline handler: (T) -> Unit) {
        register(T::class.java, handler)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> register(type: Class<T>, handler: (T) -> Unit) {
        val list = synchronized(listeners) {
            listeners.getOrPut(type) { CopyOnWriteArrayList() }
        }
        list += handler as (Any) -> Unit
    }

    /** Dispatch an event to all registered listeners of its concrete type. */
    fun fire(event: Any) {
        val list = listeners[event.javaClass] ?: return
        for (handler in list) {
            try {
                handler(event)
            } catch (t: Throwable) {
                System.err.println("[LasseClient] EventBus listener threw: ${t.message}")
                t.printStackTrace()
            }
        }
    }
}
