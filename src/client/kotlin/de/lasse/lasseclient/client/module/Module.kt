package de.lasse.lasseclient.client.module

import com.teamresourceful.resourcefulconfig.api.types.entries.Observable
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment

/**
 * A toggleable client feature.
 *
 * Enabled state is owned by Resourceful Config: each module's category opens with an
 * `Observable<Boolean>` that the module binds to via [bindEnabled]. Flipping the toggle in the
 * config screen fires the observable, which drives [onEnable]/[onDisable]; calling [setEnabled]
 * in code writes back through the observable so the screen and persistence stay in sync.
 */
@Environment(EnvType.CLIENT)
abstract class Module(
    val name: String,
    val description: String,
    val category: Category,
) {
    var enabled: Boolean = false
        private set

    private var observable: Observable<Boolean>? = null

    /**
     * Bind this module's enabled flag to its Resourceful Config toggle. Applies the loaded value
     * immediately (firing [onEnable] if saved as on) and reacts to future flips from the screen.
     */
    fun bindEnabled(observable: Observable<Boolean>) {
        this.observable = observable
        observable.addListener { _, newValue -> applyEnabled(newValue) }
        applyEnabled(observable.get())
    }

    fun toggle() = setEnabled(!enabled)

    fun setEnabled(value: Boolean) {
        val obs = observable
        if (obs != null) obs.accept(value) else applyEnabled(value)
    }

    private fun applyEnabled(value: Boolean) {
        if (value == enabled) return
        enabled = value
        try {
            if (value) onEnable() else onDisable()
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    protected open fun onEnable() {}
    protected open fun onDisable() {}
}
