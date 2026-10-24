package me.astrax.event;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/** Chỉ gọi khi PlaceholderAPI có mặt (xem AstraxEventPlugin#hasPapi). */
final class PapiHook {

    private PapiHook() {}

    static String parse(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
