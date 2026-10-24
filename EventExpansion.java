package me.astrax.event;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Placeholder:
 *  %astraxevent_current%   tên event đang diễn ra
 *  %astraxevent_time%      thời gian còn lại (mm:ss)
 *  %astraxevent_detail%    chi tiết (HP boss, đợt khe nứt...)
 *  %astraxevent_next%      event kế tiếp trong lịch
 *  %astraxevent_<id>_next% giờ chạy kế tiếp của 1 event (awakening|boss|rift|treasure)
 *  %astraxevent_streak%    chuỗi điểm danh của người chơi
 *  %astraxevent_daily%     trạng thái điểm danh hôm nay
 */
public final class EventExpansion extends PlaceholderExpansion {

    private final AstraxEventPlugin plugin;

    public EventExpansion(AstraxEventPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "astraxevent";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Astrax";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        EventManager m = plugin.manager();
        GameEvent a = m.active();
        String p = params.toLowerCase(Locale.ROOT);
        switch (p) {
            case "current":
                return a == null ? plugin.msg().text("common.none") : Util.strip(a.displayName());
            case "time":
                return a == null ? "--:--" : Util.time(a.timeLeft());
            case "detail":
                return a == null ? "" : Util.strip(a.detail());
            case "next":
                return m.nextOverall();
            case "streak":
                return player == null ? "0" : String.valueOf(plugin.daily().streak(player.getUniqueId()));
            case "daily":
                if (player == null) return "";
                return plugin.msg().text(plugin.daily().claimedToday(player.getUniqueId())
                        ? "daily.state-claimed" : "daily.state-unclaimed");
            default:
                if (p.endsWith("_next")) {
                    GameEvent e = m.get(p.substring(0, p.length() - 5));
                    return e == null ? null : Util.strip(m.nextText(e));
                }
                return null;
        }
    }
}
