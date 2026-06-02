package de.lasse.lasseclient.config;

/**
 * Sound options offered per chat-notification rule. Kept free of Minecraft classes so it can live
 * with the rest of the config; the owning module maps each constant to a concrete
 * {@code SoundEvent} (see {@code ChatNotification}).
 */
public enum NotificationSound {
    NONE,
    PLING,
    BELL,
    CHIME,
    HARP,
    ANVIL,
    LEVEL_UP,
    ORB,
    EXPLODE
}
