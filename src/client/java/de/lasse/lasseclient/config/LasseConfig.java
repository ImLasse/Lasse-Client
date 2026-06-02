package de.lasse.lasseclient.config;

import com.teamresourceful.resourcefulconfig.api.annotations.Category;
import com.teamresourceful.resourcefulconfig.api.annotations.Comment;
import com.teamresourceful.resourcefulconfig.api.annotations.Config;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigButton;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigInfo;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigObject;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigOption;
import com.teamresourceful.resourcefulconfig.api.types.entries.Observable;
import de.lasse.lasseclient.client.modules.debug.DebugActions;

import java.util.ArrayList;
import java.util.List;

/**
 * Root Resourceful Config definition for Lasse Client.
 *
 * <p>The in-game screen is Resourceful Config's own (default theme). To keep navigation shallow,
 * the hierarchy is only one level deep: one top-level category per module group (Visual /
 * Notifications / Utilities). A group's page lists each of its modules as a {@code @Separator}
 * header + an {@code enabled} toggle (and any list settings), with the rest of that module's
 * settings tucked inside a {@link ConfigObject} that opens in an "Edit" popup. So a module is one
 * click away and its detail one more.
 *
 * <p>The per-module {@code @ConfigObject} holder classes keep their original short field names
 * (each object is its own id namespace), so the module code reading them via its {@code Cfg} alias
 * is unchanged. Only the {@code enabled} toggles and List settings live at the group level.
 */
@ConfigInfo(
        title = "Lasse Client",
        description = "Hypixel SkyBlock quality-of-life features."
)
@Config(
        value = "lasseclient",
        categories = {
                LasseConfig.Visual.class,
                LasseConfig.Notifications.class,
                LasseConfig.Utilities.class,
                LasseConfig.Debug.class,
        }
)
@SuppressWarnings("unused")
public final class LasseConfig {

    private LasseConfig() {}

    // ===========================================================================================
    // VISUAL
    // ===========================================================================================
    @ConfigInfo(title = "Visual")
    @Category("visual")
    public static final class Visual {

        private Visual() {}

        // ---- Mob Highlighter ------------------------------------------------------------------
        @ConfigOption.Separator(value = "Mob Highlighter")
        @ConfigEntry(id = "mob_highlighter_enabled", translation = "lasseclient.config.mob_highlighter.enabled")
        @Comment("Highlight SkyBlock mobs whose nametag matches the list below.")
        public static Observable<Boolean> mobHighlighterEnabled = Observable.of(false);

        @ConfigEntry(id = "mob_highlighter_mob_names", translation = "lasseclient.config.mob_highlighter.mob_names")
        @Comment("Substrings matched against nametags (case-insensitive).")
        public static final List<String> mobHighlighterNames =
                new ArrayList<>(List.of("Mage", "Archer", "Berserker", "Healer", "Tank"));

        @ConfigEntry(id = "mob_highlighter_settings", translation = "lasseclient.config.mob_highlighter.settings")
        @Comment("ESP style for highlighted mobs.")
        public static final MobHighlighter mobHighlighterSettings = new MobHighlighter();

        @ConfigObject
        public static final class MobHighlighter {

            @ConfigEntry(id = "mode", translation = "lasseclient.config.common.mode")
            @Comment("Box outline or filled semi-transparent box.")
            public static HighlightMode mode = HighlightMode.BOX;

            @ConfigEntry(id = "color", translation = "lasseclient.config.common.color")
            @ConfigOption.Color(alpha = true)
            @Comment("Color of the ESP box.")
            public static int color = 0xFFFF4040;

            @ConfigEntry(id = "line_width", translation = "lasseclient.config.common.line_width")
            @ConfigOption.Range(min = 1, max = 5)
            @Comment("Box outline thickness.")
            public static double lineWidth = 2.0;

            @ConfigEntry(id = "fill_opacity", translation = "lasseclient.config.common.fill_opacity")
            @ConfigOption.Slider
            @ConfigOption.Range(min = 0, max = 100)
            @Comment("Filled-box opacity (%).")
            public static int fillOpacity = 30;

            @ConfigEntry(id = "through_walls", translation = "lasseclient.config.common.through_walls")
            @Comment("Render even when occluded.")
            public static boolean throughWalls = true;

            @ConfigEntry(id = "range", translation = "lasseclient.config.common.range")
            @ConfigOption.Slider
            @ConfigOption.Range(min = 8, max = 300)
            @Comment("Maximum distance (blocks) from the player.")
            public static int range = 64;

            @ConfigEntry(id = "tracers", translation = "lasseclient.config.common.tracers")
            @Comment("Draw lines from the crosshair to highlighted mobs.")
            public static boolean tracers = false;

            @ConfigEntry(id = "tracer_color", translation = "lasseclient.config.common.tracer_color")
            @ConfigOption.Color(alpha = true)
            public static int tracerColor = 0xFFFFFFFF;

            @ConfigEntry(id = "tracer_width", translation = "lasseclient.config.common.tracer_width")
            @ConfigOption.Range(min = 1, max = 5)
            public static double tracerWidth = 1.0;

            @ConfigEntry(id = "max_id_offset", translation = "lasseclient.config.common.max_id_offset")
            @ConfigOption.Slider
            @ConfigOption.Range(min = 1, max = 6)
            @Comment("How far to scan backwards from a nametag's entity id to find the mob beneath it.")
            public static int maxIdOffset = 4;

            @ConfigEntry(id = "hud_x")
            @ConfigOption.Hidden
            @ConfigOption.Range(min = 0, max = 1)
            public static double hudX = 0.40;

            @ConfigEntry(id = "hud_y")
            @ConfigOption.Hidden
            @ConfigOption.Range(min = 0, max = 1)
            public static double hudY = 0.30;

            @ConfigEntry(id = "hud_scale")
            @ConfigOption.Hidden
            @ConfigOption.Range(min = 0.5, max = 4)
            public static double hudScale = 1.0;
        }

        // ---- Pest ESP -------------------------------------------------------------------------
        @ConfigOption.Separator(value = "Pest ESP")
        @ConfigEntry(id = "pest_esp_enabled", translation = "lasseclient.config.pest_esp.enabled")
        @Comment("Highlight SkyBlock Garden pests by nametag.")
        public static Observable<Boolean> pestEspEnabled = Observable.of(false);

        @ConfigEntry(id = "pest_esp_settings", translation = "lasseclient.config.pest_esp.settings")
        @Comment("ESP style for highlighted pests.")
        public static final PestEsp pestEspSettings = new PestEsp();

        @ConfigObject
        public static final class PestEsp {

            @ConfigEntry(id = "mode", translation = "lasseclient.config.common.mode")
            public static HighlightMode mode = HighlightMode.BOX;

            @ConfigEntry(id = "color", translation = "lasseclient.config.common.color")
            @ConfigOption.Color(alpha = true)
            public static int color = 0xFFFF4040;

            @ConfigEntry(id = "line_width", translation = "lasseclient.config.common.line_width")
            @ConfigOption.Range(min = 1, max = 5)
            public static double lineWidth = 2.0;

            @ConfigEntry(id = "fill_opacity", translation = "lasseclient.config.common.fill_opacity")
            @ConfigOption.Slider
            @ConfigOption.Range(min = 0, max = 100)
            public static int fillOpacity = 30;

            @ConfigEntry(id = "through_walls", translation = "lasseclient.config.common.through_walls")
            public static boolean throughWalls = true;

            @ConfigEntry(id = "range", translation = "lasseclient.config.common.range")
            @ConfigOption.Slider
            @ConfigOption.Range(min = 8, max = 300)
            public static int range = 64;

            @ConfigEntry(id = "tracers", translation = "lasseclient.config.common.tracers")
            public static boolean tracers = false;

            @ConfigEntry(id = "tracer_color", translation = "lasseclient.config.common.tracer_color")
            @ConfigOption.Color(alpha = true)
            public static int tracerColor = 0xFFFFFFFF;

            @ConfigEntry(id = "tracer_width", translation = "lasseclient.config.common.tracer_width")
            @ConfigOption.Range(min = 1, max = 5)
            public static double tracerWidth = 1.0;

            @ConfigEntry(id = "max_id_offset", translation = "lasseclient.config.common.max_id_offset")
            @ConfigOption.Slider
            @ConfigOption.Range(min = 1, max = 6)
            public static int maxIdOffset = 4;
        }

        // ---- Mineshaft Utils ------------------------------------------------------------------
        @ConfigOption.Separator(value = "Mineshaft Utils")
        @ConfigEntry(id = "mineshaft_utils_enabled", translation = "lasseclient.config.mineshaft_utils.enabled")
        @Comment("Glacite Mineshaft helpers: corpse highlighting and fossil finding (Mineshafts only).")
        public static Observable<Boolean> mineshaftUtilsEnabled = Observable.of(false);

        @ConfigEntry(id = "mineshaft_utils_settings", translation = "lasseclient.config.mineshaft_utils.settings")
        @Comment("Corpse Finder and Fossil Finder options.")
        public static final MineshaftUtils mineshaftUtilsSettings = new MineshaftUtils();

        @ConfigObject
        public static final class MineshaftUtils {

            // ========================= Corpse Finder =========================
            @ConfigOption.Separator(value = "Corpse Finder")
            @ConfigEntry(id = "corpse_enabled", translation = "lasseclient.config.mineshaft_utils.corpse_enabled")
            @Comment("Highlight Glacite Mineshaft corpses through walls.")
            public static boolean corpseEnabled = true;

            @ConfigEntry(id = "mode", translation = "lasseclient.config.common.mode")
            @Comment("Box outline or filled semi-transparent box.")
            public static HighlightMode mode = HighlightMode.FILLED_BOX;

            @ConfigEntry(id = "line_width", translation = "lasseclient.config.common.line_width")
            @ConfigOption.Range(min = 1, max = 5)
            @Comment("Box outline thickness.")
            public static double lineWidth = 2.0;

            @ConfigEntry(id = "fill_opacity", translation = "lasseclient.config.common.fill_opacity")
            @ConfigOption.Slider
            @ConfigOption.Range(min = 0, max = 100)
            @Comment("Filled-box opacity (%).")
            public static int fillOpacity = 30;

            @ConfigEntry(id = "through_walls", translation = "lasseclient.config.common.through_walls")
            @Comment("Render even when occluded.")
            public static boolean throughWalls = true;

            @ConfigEntry(id = "range", translation = "lasseclient.config.common.range")
            @ConfigOption.Slider
            @ConfigOption.Range(min = 8, max = 300)
            @Comment("Maximum distance (blocks) from the player. Mineshafts are large, so this is high.")
            public static int range = 300;

            @ConfigEntry(id = "hide_opened", translation = "lasseclient.config.corpse_finder.hide_opened")
            @Comment("Stop highlighting a corpse once you've opened it (detected from the loot message).")
            public static boolean hideOpened = true;

            @ConfigEntry(id = "share_to_party", translation = "lasseclient.config.corpse_finder.share_to_party")
            @Comment("Announce each found corpse's coordinates in party chat (/pc). Only fires when "
                    + "another real player is in the mineshaft with you, so solo runs stay quiet.")
            public static boolean shareToParty = false;

            @ConfigEntry(id = "tracers", translation = "lasseclient.config.common.tracers")
            @Comment("Draw lines from the crosshair to highlighted corpses (uses the corpse's color).")
            public static boolean tracers = false;

            @ConfigEntry(id = "tracer_width", translation = "lasseclient.config.common.tracer_width")
            @ConfigOption.Range(min = 1, max = 5)
            public static double tracerWidth = 1.0;

            @ConfigOption.Separator(value = "Lapis Corpse")
            @ConfigEntry(id = "lapis_enabled", translation = "lasseclient.config.corpse_finder.lapis_enabled")
            @Comment("Highlight Lapis corpses.")
            public static boolean lapisEnabled = true;

            @ConfigEntry(id = "lapis_color", translation = "lasseclient.config.corpse_finder.lapis_color")
            @ConfigOption.Color(alpha = true)
            public static int lapisColor = 0xFF5555FF;

            @ConfigOption.Separator(value = "Tungsten Corpse")
            @ConfigEntry(id = "tungsten_enabled", translation = "lasseclient.config.corpse_finder.tungsten_enabled")
            @Comment("Highlight Tungsten (Mineral) corpses.")
            public static boolean tungstenEnabled = true;

            @ConfigEntry(id = "tungsten_color", translation = "lasseclient.config.corpse_finder.tungsten_color")
            @ConfigOption.Color(alpha = true)
            public static int tungstenColor = 0xFFAAAAAA;

            @ConfigOption.Separator(value = "Umber Corpse")
            @ConfigEntry(id = "umber_enabled", translation = "lasseclient.config.corpse_finder.umber_enabled")
            @Comment("Highlight Umber (Yog) corpses.")
            public static boolean umberEnabled = true;

            @ConfigEntry(id = "umber_color", translation = "lasseclient.config.corpse_finder.umber_color")
            @ConfigOption.Color(alpha = true)
            public static int umberColor = 0xFFFFAA00;

            @ConfigOption.Separator(value = "Vanguard Corpse")
            @ConfigEntry(id = "vanguard_enabled", translation = "lasseclient.config.corpse_finder.vanguard_enabled")
            @Comment("Highlight Vanguard corpses.")
            public static boolean vanguardEnabled = true;

            @ConfigEntry(id = "vanguard_color", translation = "lasseclient.config.corpse_finder.vanguard_color")
            @ConfigOption.Color(alpha = true)
            public static int vanguardColor = 0xFFFF55FF;

            // ========================= Fossil Finder =========================
            @ConfigOption.Separator(value = "Fossil Finder")
            @ConfigEntry(id = "fossil_enabled", translation = "lasseclient.config.mineshaft_utils.fossil_enabled")
            @Comment("Scan the mineshaft for clusters of quartz blocks/slabs (fossils) and highlight them.")
            public static boolean fossilEnabled = false;

            @ConfigEntry(id = "fossil_mode", translation = "lasseclient.config.common.mode")
            @Comment("Box outline or filled semi-transparent box.")
            public static HighlightMode fossilMode = HighlightMode.FILLED_BOX;

            @ConfigEntry(id = "fossil_color", translation = "lasseclient.config.common.color")
            @ConfigOption.Color(alpha = true)
            @Comment("Highlight color for fossils.")
            public static int fossilColor = 0xFF55FFFF;

            @ConfigEntry(id = "fossil_line_width", translation = "lasseclient.config.common.line_width")
            @ConfigOption.Range(min = 1, max = 5)
            public static double fossilLineWidth = 2.0;

            @ConfigEntry(id = "fossil_fill_opacity", translation = "lasseclient.config.common.fill_opacity")
            @ConfigOption.Slider
            @ConfigOption.Range(min = 0, max = 100)
            public static int fossilFillOpacity = 25;

            @ConfigEntry(id = "fossil_through_walls", translation = "lasseclient.config.common.through_walls")
            @Comment("Render even when occluded.")
            public static boolean fossilThroughWalls = true;

            @ConfigEntry(id = "fossil_range", translation = "lasseclient.config.common.range")
            @ConfigOption.Slider
            @ConfigOption.Range(min = 16, max = 96)
            @Comment("Radius (blocks) of the one-shot scan done once when you enter a mineshaft. "
                    + "Higher values scan more at once (a brief one-time cost on entry); it never "
                    + "re-scans until the next mineshaft.")
            public static int fossilRange = 48;

            @ConfigEntry(id = "fossil_min_cluster", translation = "lasseclient.config.mineshaft_utils.fossil_min_cluster")
            @ConfigOption.Slider
            @ConfigOption.Range(min = 2, max = 20)
            @Comment("Minimum number of quartz blocks (after merging) for a cluster to count as a fossil.")
            public static int fossilMinCluster = 4;

            @ConfigEntry(id = "fossil_merge_distance", translation = "lasseclient.config.mineshaft_utils.fossil_merge_distance")
            @ConfigOption.Slider
            @ConfigOption.Range(min = 0, max = 16)
            @Comment("Quartz clumps within this many blocks of each other are merged into one fossil "
                    + "waypoint (0 = only merge directly-touching blocks).")
            public static int fossilMergeDistance = 5;

            @ConfigEntry(id = "fossil_tracers", translation = "lasseclient.config.common.tracers")
            @Comment("Draw lines from the crosshair to fossils (acts as a waypoint).")
            public static boolean fossilTracers = true;

            @ConfigEntry(id = "fossil_tracer_width", translation = "lasseclient.config.common.tracer_width")
            @ConfigOption.Range(min = 1, max = 5)
            public static double fossilTracerWidth = 1.0;
        }

        // ---- Lily Pad Helper ------------------------------------------------------------------
        @ConfigOption.Separator(value = "Lily Pad Helper")
        @ConfigEntry(id = "lilypad_helper_enabled", translation = "lasseclient.config.lilypad_helper.enabled")
        @Comment("Alerts before a Garden lily pad explodes and can highlight all lily pads.")
        public static Observable<Boolean> lilypadHelperEnabled = Observable.of(false);

        @ConfigEntry(id = "lilypad_helper_settings", translation = "lasseclient.config.lilypad_helper.settings")
        @Comment("Alert and highlight settings.")
        public static final LilypadHelper lilypadHelperSettings = new LilypadHelper();

        @ConfigObject
        public static final class LilypadHelper {

            @ConfigOption.Separator(value = "Explosion alert")
            @ConfigEntry(id = "alert", translation = "lasseclient.config.lilypad_helper.alert")
            @Comment("Warn + red box/tracer when a pad is about to explode.")
            public static boolean alertOnBig = true;

            @ConfigEntry(id = "alert_size", translation = "lasseclient.config.lilypad_helper.alert_size")
            @ConfigOption.Range(min = 2, max = 9)
            @Comment("Scale at which to start warning (explodes near 8.2).")
            public static double alertThreshold = 6.5;

            @ConfigEntry(id = "alert_duration", translation = "lasseclient.config.lilypad_helper.alert_duration")
            @ConfigOption.Range(min = 1, max = 10)
            @Comment("How long the HUD warning stays up (seconds).")
            public static double alertDuration = 3.0;

            @ConfigOption.Separator(value = "Highlight all")
            @ConfigEntry(id = "highlight_all", translation = "lasseclient.config.lilypad_helper.highlight_all")
            @Comment("Box/tracer every lily pad, not just exploding ones.")
            public static boolean highlightAll = false;

            @ConfigEntry(id = "highlight_mode", translation = "lasseclient.config.lilypad_helper.highlight_mode")
            public static HighlightMode highlightMode = HighlightMode.BOX;

            @ConfigEntry(id = "highlight_color", translation = "lasseclient.config.lilypad_helper.highlight_color")
            @ConfigOption.Color(alpha = true)
            public static int highlightColor = 0xFF55FF55;

            @ConfigEntry(id = "line_width", translation = "lasseclient.config.common.line_width")
            @ConfigOption.Range(min = 1, max = 6)
            public static double lineWidth = 2.0;

            @ConfigEntry(id = "fill_opacity", translation = "lasseclient.config.common.fill_opacity")
            @ConfigOption.Slider
            @ConfigOption.Range(min = 0, max = 100)
            public static int fillOpacity = 35;

            @ConfigEntry(id = "highlight_tracers", translation = "lasseclient.config.lilypad_helper.highlight_tracers")
            @Comment("Draw tracers to highlighted lily pads.")
            public static boolean highlightTracers = false;

            @ConfigEntry(id = "tracer_color", translation = "lasseclient.config.common.tracer_color")
            @ConfigOption.Color(alpha = true)
            public static int tracerColor = 0xFF55FF55;

            @ConfigEntry(id = "tracer_width", translation = "lasseclient.config.common.tracer_width")
            @ConfigOption.Range(min = 1, max = 8)
            public static double tracerWidth = 3.0;

            @ConfigEntry(id = "range", translation = "lasseclient.config.common.range")
            @ConfigOption.Slider
            @ConfigOption.Range(min = 8, max = 300)
            public static int range = 64;

            @ConfigEntry(id = "hud_x")
            @ConfigOption.Hidden
            @ConfigOption.Range(min = 0, max = 1)
            public static double hudX = 0.40;

            @ConfigEntry(id = "hud_y")
            @ConfigOption.Hidden
            @ConfigOption.Range(min = 0, max = 1)
            public static double hudY = 0.30;

            @ConfigEntry(id = "hud_scale")
            @ConfigOption.Hidden
            @ConfigOption.Range(min = 0.5, max = 4)
            public static double hudScale = 1.0;
        }

        // ---- No Visual Effects ----------------------------------------------------------------
        @ConfigOption.Separator(value = "No Visual Effects")
        @ConfigEntry(id = "no_visual_effects_enabled", translation = "lasseclient.config.no_visual_effects.enabled")
        @Comment("Hide blindness, nausea and darkness at the render layer (no packets, effect kept).")
        public static Observable<Boolean> noVisualEffectsEnabled = Observable.of(false);

        @ConfigEntry(id = "no_visual_effects_settings", translation = "lasseclient.config.no_visual_effects.settings")
        @Comment("Which effects to hide.")
        public static final NoVisualEffects noVisualEffectsSettings = new NoVisualEffects();

        @ConfigObject
        public static final class NoVisualEffects {

            @ConfigEntry(id = "blindness", translation = "lasseclient.config.no_visual_effects.blindness")
            @Comment("Hide the blindness fog.")
            public static Observable<Boolean> blindness = Observable.of(true);

            @ConfigEntry(id = "nausea", translation = "lasseclient.config.no_visual_effects.nausea")
            @Comment("Hide the nausea screen wobble.")
            public static Observable<Boolean> nausea = Observable.of(true);

            @ConfigEntry(id = "darkness", translation = "lasseclient.config.no_visual_effects.darkness")
            @Comment("Hide the darkness pulsing dimming.")
            public static Observable<Boolean> darkness = Observable.of(true);
        }
    }

    // ===========================================================================================
    // NOTIFICATIONS
    // ===========================================================================================
    @ConfigInfo(title = "Notifications")
    @Category("notifications")
    public static final class Notifications {

        private Notifications() {}

        // ---- Spawn Notification ---------------------------------------------------------------
        @ConfigOption.Separator(value = "Spawn Notification")
        @ConfigEntry(id = "spawn_notification_enabled", translation = "lasseclient.config.spawn_notification.enabled")
        @Comment("Flash a HUD notification + pings on rare mob spawns.")
        public static Observable<Boolean> spawnNotificationEnabled = Observable.of(false);

        @ConfigEntry(id = "spawn_notification_mob_names", translation = "lasseclient.config.spawn_notification.mob_names")
        @Comment("Mob nametags that trigger \"RARE MOB SPAWNED IN\" + highlight.")
        public static final List<String> spawnNotificationMobNames = new ArrayList<>();

        @ConfigEntry(id = "spawn_notification_settings", translation = "lasseclient.config.spawn_notification.settings")
        @Comment("Notification timing and the rare-mob highlight style.")
        public static final SpawnNotification spawnNotificationSettings = new SpawnNotification();

        @ConfigObject
        public static final class SpawnNotification {

            @ConfigEntry(id = "duration", translation = "lasseclient.config.spawn_notification.duration")
            @ConfigOption.Range(min = 1, max = 10)
            @Comment("How long the notification stays on screen (seconds).")
            public static double duration = 3.0;

            @ConfigOption.Separator(value = "Rare mob highlight")
            @ConfigEntry(id = "mode", translation = "lasseclient.config.common.mode")
            public static HighlightMode mode = HighlightMode.FILLED_BOX;

            @ConfigEntry(id = "color", translation = "lasseclient.config.common.color")
            @ConfigOption.Color(alpha = true)
            public static int color = 0xFFAA00FF;

            @ConfigEntry(id = "line_width", translation = "lasseclient.config.common.line_width")
            @ConfigOption.Range(min = 1, max = 5)
            public static double lineWidth = 3.0;

            @ConfigEntry(id = "fill_opacity", translation = "lasseclient.config.common.fill_opacity")
            @ConfigOption.Slider
            @ConfigOption.Range(min = 0, max = 100)
            public static int fillOpacity = 100;

            @ConfigEntry(id = "through_walls", translation = "lasseclient.config.common.through_walls")
            public static boolean throughWalls = true;

            @ConfigEntry(id = "range", translation = "lasseclient.config.common.range")
            @ConfigOption.Slider
            @ConfigOption.Range(min = 8, max = 300)
            public static int range = 64;

            @ConfigEntry(id = "tracers", translation = "lasseclient.config.common.tracers")
            public static boolean tracers = true;

            @ConfigEntry(id = "tracer_color", translation = "lasseclient.config.common.tracer_color")
            @ConfigOption.Color(alpha = true)
            public static int tracerColor = 0xFFAA00FF;

            @ConfigEntry(id = "tracer_width", translation = "lasseclient.config.common.tracer_width")
            @ConfigOption.Range(min = 1, max = 5)
            public static double tracerWidth = 5.0;

            @ConfigEntry(id = "max_id_offset", translation = "lasseclient.config.common.max_id_offset")
            @ConfigOption.Slider
            @ConfigOption.Range(min = 1, max = 6)
            public static int maxIdOffset = 4;

            @ConfigEntry(id = "hud_x")
            @ConfigOption.Hidden
            @ConfigOption.Range(min = 0, max = 1)
            public static double hudX = 0.40;

            @ConfigEntry(id = "hud_y")
            @ConfigOption.Hidden
            @ConfigOption.Range(min = 0, max = 1)
            public static double hudY = 0.25;

            @ConfigEntry(id = "hud_scale")
            @ConfigOption.Hidden
            @ConfigOption.Range(min = 0.5, max = 4)
            public static double hudScale = 1.0;
        }

        // ---- Chat Notifications ---------------------------------------------------------------
        @ConfigOption.Separator(value = "Chat Notifications")
        @ConfigEntry(id = "chat_notification_enabled", translation = "lasseclient.config.chat_notification.enabled")
        @Comment("Flash a HUD message + play a sound when a chat line matches one of your rules.")
        public static Observable<Boolean> chatNotificationEnabled = Observable.of(false);

        @ConfigEntry(id = "chat_notification_rules", translation = "lasseclient.config.chat_notification.rules")
        @Comment("Each rule: a substring to look for in chat, the message to show, its color, and a sound.")
        public static final List<ChatNotificationRule> chatNotificationRules = new ArrayList<>();

        @ConfigEntry(id = "chat_notification_settings", translation = "lasseclient.config.chat_notification.settings")
        @Comment("How long the notification stays on screen.")
        public static final ChatNotification chatNotificationSettings = new ChatNotification();

        @ConfigObject
        public static final class ChatNotification {

            @ConfigEntry(id = "duration", translation = "lasseclient.config.chat_notification.duration")
            @ConfigOption.Range(min = 1, max = 10)
            @Comment("How long the notification stays on screen (seconds).")
            public static double duration = 3.0;

            @ConfigEntry(id = "hud_x")
            @ConfigOption.Hidden
            @ConfigOption.Range(min = 0, max = 1)
            public static double hudX = 0.40;

            @ConfigEntry(id = "hud_y")
            @ConfigOption.Hidden
            @ConfigOption.Range(min = 0, max = 1)
            public static double hudY = 0.20;

            @ConfigEntry(id = "hud_scale")
            @ConfigOption.Hidden
            @ConfigOption.Range(min = 0.5, max = 4)
            public static double hudScale = 1.0;
        }
    }

    // ===========================================================================================
    // UTILITIES
    // ===========================================================================================
    @ConfigInfo(title = "Utilities")
    @Category("utilities")
    public static final class Utilities {

        private Utilities() {}

        // ---- Display Entity Debug -------------------------------------------------------------
        @ConfigOption.Separator(value = "Display Entity Debug")
        @ConfigEntry(id = "display_entity_debug_enabled", translation = "lasseclient.config.display_entity_debug.enabled")
        @Comment("Inspect lily-pad / display entities and report their live scale.")
        public static Observable<Boolean> displayEntityDebugEnabled = Observable.of(false);

        @ConfigEntry(id = "display_entity_debug_settings", translation = "lasseclient.config.display_entity_debug.settings")
        @Comment("Scan and readout settings.")
        public static final DisplayEntityDebug displayEntityDebugSettings = new DisplayEntityDebug();

        @ConfigObject
        public static final class DisplayEntityDebug {

            @ConfigEntry(id = "radius", translation = "lasseclient.config.display_entity_debug.radius")
            @ConfigOption.Slider
            @ConfigOption.Range(min = 4, max = 64)
            @Comment("Blocks around you to scan.")
            public static int radius = 24;

            @ConfigEntry(id = "lily_only", translation = "lasseclient.config.display_entity_debug.lily_only")
            @Comment("On: only block/item displays showing a lily pad. Off: every display entity.")
            public static boolean lilyPadsOnly = true;

            @ConfigEntry(id = "render_box", translation = "lasseclient.config.display_entity_debug.render_box")
            @Comment("Draw a box around each candidate (grows with scale).")
            public static boolean renderBox = true;

            @ConfigEntry(id = "tracer", translation = "lasseclient.config.display_entity_debug.tracer")
            @Comment("Draw a line from your crosshair to each candidate.")
            public static boolean showTracer = false;

            @ConfigEntry(id = "log_chat", translation = "lasseclient.config.display_entity_debug.log_chat")
            @Comment("Dump candidate details to chat once per second.")
            public static boolean logToChat = true;
        }

        // ---- Chat Commands --------------------------------------------------------------------
        @ConfigOption.Separator(value = "Chat Commands")
        @ConfigEntry(id = "chat_command_enabled", translation = "lasseclient.config.chat_command.enabled")
        @Comment("Run a command automatically when a chat line matches one of your rules.")
        public static Observable<Boolean> chatCommandEnabled = Observable.of(false);

        @ConfigEntry(id = "chat_command_rules", translation = "lasseclient.config.chat_command.rules")
        @Comment("Each rule: a substring to look for in chat, the command to run, and whether to announce it.")
        public static final List<ChatCommandRule> chatCommandRules = new ArrayList<>();

        @ConfigEntry(id = "chat_command_settings", translation = "lasseclient.config.chat_command.settings")
        @Comment("Shared options for chat commands.")
        public static final ChatCommand chatCommandSettings = new ChatCommand();

        @ConfigObject
        public static final class ChatCommand {

            @ConfigEntry(id = "cooldown", translation = "lasseclient.config.chat_command.cooldown")
            @ConfigOption.Range(min = 0, max = 60)
            @Comment("Minimum seconds between two runs of the same rule (prevents spam loops).")
            public static double cooldown = 5.0;
        }
    }

    // ===========================================================================================
    // DEBUG
    // ===========================================================================================
    @ConfigInfo(title = "Debug")
    @Category("debug")
    public static final class Debug {

        private Debug() {}

        // ---- Location -------------------------------------------------------------------------
        @ConfigOption.Separator(value = "Location")
        @ConfigButton(title = "lasseclient.config.debug.print_location", text = "Print")
        @Comment("Print the current Hypixel location (mode/map/server) into chat. Warp to an island "
                + "and click this to find the mode string for gating a feature.")
        public static final Runnable printLocation = DebugActions::printLocation;
    }
}
