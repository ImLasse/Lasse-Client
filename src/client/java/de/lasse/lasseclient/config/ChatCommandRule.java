package de.lasse.lasseclient.config;

import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigObject;
import com.teamresourceful.resourcefulconfig.api.types.info.ListEntryInfoProvider;
import net.minecraft.text.Text;

/**
 * One chat-triggered command rule, edited as an entry in the Chat Commands list.
 *
 * <p>When an incoming chat line (color codes stripped) contains {@link #match} (case-insensitive,
 * {@code contains} not exact), the module runs {@link #command} as if the player typed it. The
 * leading slash is optional. When {@link #announce} is on, a client-side line like
 * {@code "<match> detected, executing '/<command>'"} is printed to chat.
 *
 * <p>Same Resourceful Config rules as {@link ChatNotificationRule}: public {@code @ConfigObject}
 * with a public no-arg constructor and public non-final value fields.
 */
@ConfigObject
public final class ChatCommandRule implements ListEntryInfoProvider {

    @ConfigEntry(id = "match", translation = "lasseclient.config.chat_command.match")
    public String match = "";

    @ConfigEntry(id = "command", translation = "lasseclient.config.chat_command.command")
    public String command = "";

    @ConfigEntry(id = "announce", translation = "lasseclient.config.chat_command.announce")
    public boolean announce = true;

    public ChatCommandRule() {}

    @Override
    public Text getTitle(int index) {
        String label = match.isBlank() ? command : match;
        if (label.isBlank()) label = "Rule #" + (index + 1);
        return Text.literal(label);
    }
}
