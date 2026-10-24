package me.astrax.event;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Quản lý lịch chạy tự động và event đang hoạt động (mỗi lúc chỉ có 1 event). */
public final class EventManager implements Listener {

    private final AstraxEventPlugin plugin;
    private final Map<String, GameEvent> events = new LinkedHashMap<>();
    private GameEvent active;
    private BukkitTask task;
    private String lastMinute = "";

    public EventManager(AstraxEventPlugin plugin) {
        this.plugin = plugin;
        register(new AwakeningEvent(plugin));
        register(new WorldBossEvent(plugin));
        register(new RiftEvent(plugin));
        register(new TreasureRainEvent(plugin));
    }

    private void register(GameEvent e) {
        events.put(e.id(), e);
    }

    public void startScheduler() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::second, 20L, 20L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        GameEvent a = active();
        if (a != null) a.stop(GameEvent.Reason.SHUTDOWN);
        active = null;
    }

    public void reload() {
        for (GameEvent e : events.values()) e.reload();
    }

    // ---------- Truy cập ----------

    public GameEvent active() {
        if (active != null && !active.isRunning()) active = null;
        return active;
    }

    public GameEvent get(String id) {
        return id == null ? null : events.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<GameEvent> all() {
        return events.values();
    }

    public ZoneId zone() {
        try {
            return ZoneId.of(plugin.getConfig().getString("settings.timezone", "Asia/Ho_Chi_Minh"));
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }

    // ---------- Điều khiển ----------

    /** Trả về null nếu thành công, ngược lại là thông báo lỗi (đã định dạng). */
    public String start(String id, String arg, boolean force) {
        GameEvent e = get(id);
        if (e == null) return plugin.msg().text("common.unknown-event", "id", id);
        GameEvent a = active();
        if (a != null) return plugin.msg().text("common.already-running", "event", a.displayName());
        if (!force && Bukkit.getOnlinePlayers().size() < e.minPlayers()) {
            return plugin.msg().text("common.not-enough-players", "min", e.minPlayers());
        }
        if (!e.start(arg)) return plugin.msg().text("common.start-failed", "event", e.displayName());
        active = e;
        return null;
    }

    public boolean stopActive() {
        GameEvent a = active();
        if (a == null) return false;
        a.stop(GameEvent.Reason.ADMIN);
        active = null;
        return true;
    }

    // ---------- Vòng lặp mỗi giây ----------

    private void second() {
        GameEvent a = active();
        if (a != null) a.tick();
        checkSchedule();
    }

    private void checkSchedule() {
        ZonedDateTime now = ZonedDateTime.now(zone());
        String hm = String.format(Locale.ROOT, "%02d:%02d", now.getHour(), now.getMinute());
        String key = now.toLocalDate() + " " + hm;
        if (key.equals(lastMinute)) return;
        lastMinute = key;

        LocalTime cur = LocalTime.of(now.getHour(), now.getMinute());
        String dow = now.getDayOfWeek().name();
        int warn = plugin.getConfig().getInt("settings.warn-minutes-before", 5);

        for (GameEvent e : events.values()) {
            if (!e.enabled()) continue;
            Set<String> days = e.days();
            if (!days.isEmpty() && !days.contains(dow)) continue;
            for (LocalTime t : e.times()) {
                if (t.equals(cur)) {
                    if (active() != null) {
                        plugin.getLogger().info("Bỏ qua lịch " + e.id() + " vì đang có event khác chạy.");
                        continue;
                    }
                    String err = start(e.id(), null, false);
                    if (err != null) plugin.getLogger().info("Không tự bắt đầu " + e.id() + ": " + Util.strip(err));
                } else if (warn > 0 && t.minusMinutes(warn).equals(cur) && active() == null) {
                    plugin.msg().broadcast("common.warn", "event", e.displayName(), "minutes", warn);
                    Util.soundAll(plugin.getConfig().getString("settings.sound-warn", ""));
                }
            }
        }
    }

    // ---------- Giờ chạy kế tiếp ----------

    public ZonedDateTime nextTime(GameEvent e) {
        ZoneId z = zone();
        ZonedDateTime now = ZonedDateTime.now(z);
        ZonedDateTime best = null;
        Set<String> days = e.days();
        for (int d = 0; d <= 7; d++) {
            LocalDate date = now.toLocalDate().plusDays(d);
            if (!days.isEmpty() && !days.contains(date.getDayOfWeek().name())) continue;
            for (LocalTime t : e.times()) {
                ZonedDateTime c = ZonedDateTime.of(date, t, z);
                if (c.isAfter(now) && (best == null || c.isBefore(best))) best = c;
            }
        }
        return best;
    }

    public String nextText(GameEvent e) {
        if (!e.enabled()) return plugin.msg().text("common.disabled");
        ZonedDateTime t = nextTime(e);
        return t == null ? "—" : fmt(t);
    }

    public String nextOverall() {
        GameEvent bestEvent = null;
        ZonedDateTime bestTime = null;
        for (GameEvent e : events.values()) {
            if (!e.enabled()) continue;
            ZonedDateTime t = nextTime(e);
            if (t != null && (bestTime == null || t.isBefore(bestTime))) {
                bestTime = t;
                bestEvent = e;
            }
        }
        if (bestEvent == null) return "—";
        return Util.strip(bestEvent.displayName()) + " " + fmt(bestTime);
    }

    private String fmt(ZonedDateTime t) {
        long mins = Duration.between(ZonedDateTime.now(zone()), t).toMinutes();
        return String.format(Locale.ROOT, "%02d:%02d (còn %s)", t.getHour(), t.getMinute(), Util.humanMinutes(mins));
    }

    // ---------- Sự kiện Bukkit ----------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        GameEvent a = active();
        if (a != null) a.showBarTo(e.getPlayer());
    }
}
