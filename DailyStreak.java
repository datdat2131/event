package me.astrax.event;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/** Điểm danh hằng ngày với chuỗi đăng nhập liên tiếp. Dữ liệu lưu ở data.yml. */
public final class DailyStreak implements Listener {

    private final AstraxEventPlugin plugin;
    private final File file;
    private YamlConfiguration data;

    public DailyStreak(AstraxEventPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        this.data = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("daily.enabled", true);
    }

    private LocalDate today() {
        return LocalDate.now(plugin.manager().zone());
    }

    public int streak(UUID id) {
        return data.getInt("players." + id + ".streak", 0);
    }

    public boolean claimedToday(UUID id) {
        String last = data.getString("players." + id + ".last", "");
        return last.equals(today().toString());
    }

    public void claim(Player p) {
        if (!enabled()) {
            plugin.msg().send(p, "daily.disabled");
            return;
        }
        String base = "players." + p.getUniqueId();
        LocalDate today = today();
        int streak = data.getInt(base + ".streak", 0);
        String lastText = data.getString(base + ".last", "");

        if (!lastText.isEmpty()) {
            try {
                long diff = ChronoUnit.DAYS.between(LocalDate.parse(lastText), today);
                if (diff <= 0) {
                    plugin.msg().send(p, "daily.already", "streak", streak);
                    return;
                }
                int grace = plugin.getConfig().getInt("daily.grace-days", 0);
                streak = diff <= 1 + grace ? streak + 1 : 1;
            } catch (Exception e) {
                streak = 1;
            }
        } else {
            streak = 1;
        }

        data.set(base + ".name", p.getName());
        data.set(base + ".streak", streak);
        data.set(base + ".last", today.toString());
        data.set(base + ".total", data.getInt(base + ".total", 0) + 1);
        saveAsync();

        int cycle = Math.max(1, plugin.getConfig().getInt("daily.cycle", 30));
        int day = ((streak - 1) % cycle) + 1;

        plugin.msg().send(p, "daily.claimed", "streak", streak, "day", day, "cycle", cycle);
        Util.sound(p, plugin.getConfig().getString("daily.sound", ""));

        ConfigurationSection rs = plugin.getConfig().getConfigurationSection("daily.rewards");
        if (rs == null) return;
        ConfigurationSection s = rs.getConfigurationSection("day-" + day);
        if (s == null) s = rs.getConfigurationSection("default");
        if (s != null) {
            Reward.from(s, plugin).give(plugin, p,
                    Map.of("streak", String.valueOf(streak), "day", String.valueOf(day)));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!enabled() || !plugin.getConfig().getBoolean("daily.remind-on-join", true)) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || claimedToday(p.getUniqueId())) return;
            if (plugin.getConfig().getBoolean("daily.auto-claim-on-join", false)) {
                claim(p);
                return;
            }
            Component c = Util.color(plugin.msg().text("daily.reminder")).clickEvent(ClickEvent.runCommand("/diemdanh"));
            p.sendMessage(c);
        }, 100L);
    }

    private void saveAsync() {
        String content = data.saveToString();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(content));
    }

    public void saveNow() {
        write(data.saveToString());
    }

    private void write(String content) {
        synchronized (file) {
            try {
                file.getParentFile().mkdirs();
                Files.writeString(file.toPath(), content);
            } catch (IOException ex) {
                plugin.getLogger().warning("Không ghi được data.yml: " + ex.getMessage());
            }
        }
    }
}
