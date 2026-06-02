package de.lasse.lasseclient.client.module

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment

@Environment(EnvType.CLIENT)
object ModuleManager {
    private val modulesInternal: MutableList<Module> = mutableListOf()
    val modules: List<Module> get() = modulesInternal

    fun register(module: Module): Module {
        modulesInternal += module
        return module
    }

    fun byCategory(category: Category): List<Module> =
        modulesInternal.filter { it.category == category }

    fun byName(name: String): Module? =
        modulesInternal.firstOrNull { it.name.equals(name, ignoreCase = true) }
}
