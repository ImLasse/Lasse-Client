package de.lasse.lasseclient.client.event

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment

/**
 * Fired (read-only) when an entity's CUSTOM_NAME tracked-data slot is set.
 *
 * `name` is the raw display string (with `§` formatting codes still in it — strip them
 * yourself if you're matching against plain text). Empty if the entity has no name set.
 */
@Environment(EnvType.CLIENT)
data class NameChangeEvent(
    val entityId: Int,
    val name: String,
)
