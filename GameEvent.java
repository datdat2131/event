package me.astrax.event;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Khung chung cho mọi event: lịch, thanh boss bar, đếm ngược, xếp hạng. */
public abstract class GameEvent implements Listener {

    public enum Reason { COMPLETED, TIMEOUT, ADMIN, SHUTDOWN }

    protected final AstraxEventPlugin plugin;
    protected final String id;
    protected ConfigurationSection cfg;
    protected boolean running;
    protected int timeLeft;
    protected int totalTime;
    protected String arg;
    private BossBar bar;

    protected GameEvent(AstraxEventPlugin plugin, String id) {
        this.plugin = plugin;
        this.id = id;
        reload();
    }

    // ---------- Các hàm event con phải cài đặt ----------

    protected abstract String defaultName();

    /** Trả về false nếu không thể bắt đầu (phải tự dọn dẹp trước khi trả về false). */
    protected abstract boolean onStart();

    protected abstract void onTick();

    protected abstract void onStop(Reason reason);

    protected abstract String barTitle();

    protected float progress() {
        return totalTime <= 0 ? 0f : (float) timeLeft / totalTime;
    }

    protected String barColorName() {
        return cfg.getString("bossbar.color", "RED");
    }

    public String detail() {
        return "";
    }

    /** Thông tin vị trí cho /event where (null nếu không có). */
    public String where() {
        return null;
    }

    // ---------- Cấu hình ----------

    public final void reload() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("events." + id);
        cfg = s != null ? s : new MemoryConfiguration();
    }

    public String id() { return id; }
    public boolean isRunning() { return running; }
    public int timeLeft() { return timeLeft; }
    public boolean enabled() { return cfg.getBoolean("enabled", true); }
    public String displayName() { return cfg.getString("display-name", defaultName()); }

    public int minPlayers() {
        return cfg.getInt("min-players", plugin.getConfig().getInt("settings.min-players", 1));
    }

    public List<LocalTime> times() {
        List<LocalTime> out = new ArrayList<>();
        for (String s : cfg.getStringList("schedule.times")) {
            try {
                String[] p = s.trim().split(":");
                out.add(LocalTime.of(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())));
            } catch (Exception ignored) {
                // giờ sai định dạng -> bỏ qua
            }
        }
        return out;
    }

    public Set<String> days() {
        Set<String> out = new HashSet<>();
        for (String s : cfg.getStringList("schedule.days")) out.add(s.trim().toUpperCase(Locale.ROOT));
        return out;
    }

    // ---------- Vòng đời ----------

    public final boolean start(String arg) {
        if (running) return false;
        this.arg = arg;
        totalTime = Math.max(10, (int) Math.round(cfg.getDouble("duration-minutes", 15) * 60));
        timeLeft = totalTime;
        running = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        boolean ok;
        try {
            ok = onStart();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Lỗi khi bắt đầu event " + id, t);
            ok = false;
        }
        if (!ok) {
            running = false;
            HandlerList.unregisterAll(this);
            return false;
        }
        createBar();
        return true;
    }

    public final void tick() {
        if (!running) return;
        timeLeft--;
        try {
            onTick();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Lỗi trong event " + id + ", đang dừng event.", t);
            stop(Reason.ADMIN);
            return;
        }
        if (!running) return;
        if (timeLeft <= 0) {
            stop(Reason.TIMEOUT);
            return;
        }
        updateBar();
    }

    public final void stop(Reason reason) {
        if (!running) return;
        running = false;
        HandlerList.unregisterAll(this);
        try {
            onStop(reason);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Lỗi khi kết thúc event " + id, t);
        }
        if (bar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
            bar = null;
        }
    }

    // ---------- Boss bar ----------

    private void createBar() {
        BossBar.Color color;
        try {
            color = BossBar.Color.valueOf(barColorName().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            color = BossBar.Color.RED;
        }
        bar = BossBar.bossBar(Util.color(barTitle()), clamp(progress()), color, BossBar.Overlay.PROGRESS);
        if (cfg.getBoolean("bossbar.enabled", true)) {
            for (Player p : Bukkit.getOnlinePlayers()) p.showBossBar(bar);
        }
    }

    private void updateBar() {
        if (bar == null) return;
        bar.name(Util.color(barTitle()));
        bar.progress(clamp(progress()));
    }

    public void showBarTo(Player p) {
        if (running && bar != null && cfg.getBoolean("bossbar.enabled", true)) p.showBossBar(bar);
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    // ---------- Tiện ích dùng chung ----------

    protected String coords(Location l) {
        return plugin.msg().text("common.coords", "world", l.getWorld().getName(),
                "x", l.getBlockX(), "y", l.getBlockY(), "z", l.getBlockZ());
    }

    /**
     * Công bố top 3 và trao thưởng: rank-N cho từng hạng, nếu không có thì participation.
     * bonusKey (nếu có) là phần thưởng thêm cho MỌI người đủ điểm tối thiểu.
     */
    protected void finishRanking(Map<UUID, ? extends Number> scores, RewardTable table,
                                 double minScore, String bonusKey) {
        List<Map.Entry<UUID, Double>> rank = Util.ranking(scores);
        rank.removeIf(e -> e.getValue() < minScore);
        if (rank.isEmpty()) {
            plugin.msg().broadcast("common.no-participants", "event", displayName());
            return;
        }
        plugin.msg().broadcast("common.result-header", "event", displayName());
        for (int i = 0; i < Math.min(3, rank.size()); i++) {
            plugin.msg().broadcast("common.rank-line", "rank", i + 1,
                    "player", Util.name(rank.get(i).getKey()), "score", Math.round(rank.get(i).getValue()));
        }
        Reward bonus = bonusKey == null ? null : table.get(bonusKey);
        Reward participation = table.get("participation");
        for (int i = 0; i < rank.size(); i++) {
            Player p = Bukkit.getPlayer(rank.get(i).getKey());
            if (p == null) continue;
            Map<String, String> vars = Map.of("rank", String.valueOf(i + 1), "event", displayName());
            Reward r = table.rank(i + 1);
            if (r == null) r = participation;
            if (r != null) r.give(plugin, p, vars);
            if (bonus != null) bonus.give(plugin, p, vars);
        }
    }
}
