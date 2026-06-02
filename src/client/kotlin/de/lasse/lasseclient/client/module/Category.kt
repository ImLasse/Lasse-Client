package de.lasse.lasseclient.client.module

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment

@Environment(EnvType.CLIENT)
enum class Category(val displayName: String, val icon: String) {
    VISUAL("Visual", "👁"),
    DUNGEONS("Dungeons", "⚔"),
    FISHING("Fishing", "🎣"),
    KUUDRA("Kuudra", "🔥"),
    NOTIFICATIONS("Notifications", "🔔"),
    UTILITIES("Utilities", "⚙");
}
