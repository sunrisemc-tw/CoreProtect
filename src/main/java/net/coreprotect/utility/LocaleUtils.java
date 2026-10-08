package net.coreprotect.utility;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Translates vanilla block/item/entity identifiers into the viewer's language
 * by resolving FoxLocale's PlaceholderAPI expansion ({@code %locale_...%}).
 *
 * <p>PlaceholderAPI (and FoxLocale) are soft dependencies: everything is
 * accessed via reflection so CoreProtect still works when they are absent.
 * When translation is unavailable the original English identifier is returned
 * unchanged.</p>
 */
public final class LocaleUtils {

    private LocaleUtils() {
        throw new IllegalStateException("Utility class");
    }

    private static volatile Boolean papiPresent = null;
    private static volatile Method setPlaceholders = null;

    // cache: locale + "|" + placeholder -> translated text
    private static final ConcurrentHashMap<String, String> CACHE = new ConcurrentHashMap<>();

    private static boolean ensurePapi() {
        if (papiPresent != null) {
            return papiPresent;
        }
        synchronized (LocaleUtils.class) {
            if (papiPresent != null) {
                return papiPresent;
            }
            try {
                if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
                    papiPresent = Boolean.FALSE;
                    return false;
                }
                Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                setPlaceholders = papi.getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class);
                papiPresent = Boolean.TRUE;
            }
            catch (Throwable t) {
                papiPresent = Boolean.FALSE;
            }
            return papiPresent;
        }
    }

    /**
     * Translate a block/item identifier (e.g. {@code oak_log}) for the viewer.
     * Returns the original identifier if translation is unavailable.
     */
    public static String translateMaterial(CommandSender sender, String identifier) {
        return translate(sender, identifier, "material_");
    }

    /**
     * Translate an entity identifier (e.g. {@code zombie}) for the viewer.
     * Returns the original identifier if translation is unavailable.
     */
    public static String translateEntity(CommandSender sender, String identifier) {
        return translate(sender, identifier, "entity_");
    }

    private static String translate(CommandSender sender, String identifier, String prefix) {
        if (identifier == null || identifier.isEmpty()) {
            return identifier;
        }
        // Only players carry a client locale; console / non-players keep English.
        if (!(sender instanceof Player)) {
            return identifier;
        }
        if (!ensurePapi()) {
            return identifier;
        }

        Player player = (Player) sender;
        // Strip namespace if present; FoxLocale expects the bare id.
        String id = identifier;
        if (id.contains(":")) {
            id = id.substring(id.indexOf(':') + 1);
        }
        id = id.toLowerCase(Locale.ROOT);

        String localeTag;
        try {
            localeTag = player.locale() != null ? player.locale().toString() : "";
        }
        catch (Throwable t) {
            localeTag = "";
        }

        String placeholder = "%locale_" + prefix + id + "%";
        String cacheKey = localeTag + "|" + placeholder;
        String cached = CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String result;
        try {
            result = (String) setPlaceholders.invoke(null, player, placeholder);
        }
        catch (Throwable t) {
            result = null;
        }

        // Unresolved: PAPI returns the placeholder verbatim, or FoxLocale returns null -> "".
        if (result == null || result.isEmpty() || result.equals(placeholder)) {
            result = identifier;
        }

        CACHE.put(cacheKey, result);
        return result;
    }
}
