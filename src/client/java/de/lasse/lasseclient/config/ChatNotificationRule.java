package de.lasse.lasseclient.config;

import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigObject;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigOption;
import com.teamresourceful.resourcefulconfig.api.types.info.ListEntryInfoProvider;
import net.minecraft.text.Text;

/**
 * One chat-notification rule, edited as an entry in the Chat Notifications list.
 *
 * <p>When an incoming chat line (color codes stripped) contains {@link #match} (case-insensitive),
 * the module flashes {@link #message} on the HUD in {@link #color} and plays {@link #sound}. The
 * message may contain {@code &}-style color codes for inline formatting.
 *
 * <p>Resourceful Config requires list element classes to be public, {@code @ConfigObject}, with a
 * public no-arg constructor and public non-final value fields. Implementing
 * {@link ListEntryInfoProvider} gives each entry a readable title in the list UI.
 */
@ConfigObject
public final class ChatNotificationRule implements ListEntryInfoProvider {

    @ConfigEntry(id = "match", translation = "lasseclient.config.chat_notification.match")
    public String match = "";

    @ConfigEntry(id = "message", translation = "lasseclient.config.chat_notification.message")
    public String message = "";

    @ConfigEntry(id = "color", translation = "lasseclient.config.chat_notification.color")
    @ConfigOption.Color(alpha = true)
    public int color = 0xFFFFFF55;

    @ConfigEntry(id = "sound", translation = "lasseclient.config.chat_notification.sound")
    public NotificationSound sound = NotificationSound.PLING;

    public ChatNotificationRule() {}

    @Override
    public Text getTitle(int index) {
        String label = match.isBlank() ? message : match;
        if (label.isBlank()) label = "Rule #" + (index + 1);
        return Text.literal(label);
    }
}
