package me.astrax.event;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/** Đọc thông báo từ messages.yml. Biến dạng {ten} được thay bằng cặp key/value truyền vào. */
public final class Msg {

    private final AstraxEventPlugin plugin;
    private YamlConfiguration cfg;

    public Msg(AstraxEventPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "messages.yml");
        if (!f.exists()) plugin.saveResource("messages.yml", false);
        cfg = YamlConfiguration.loadConfiguration(f);
    }

    public List<String> lines(String key, Object... kv) {
        String raw;
        if (cfg.isList(key)) {
            raw = String.join("\n", cfg.getStringList(key));
        } else {
            raw = cfg.getString(key, "&c[thiếu thông báo: " + key + "]");
        }
        raw = raw.replace("{prefix}", cfg.getString("prefix", ""));
        for (int i = 0; i + 1 < kv.length; i += 2) {
            raw = raw.replace("{" + kv[i] + "}", String.valueOf(kv[i + 1]));
        }
        return Arrays.asList(raw.split("\n", -1));
    }

    public String text(String key, Object... kv) {
        return String.join("\n", lines(key, kv));
    }

    public void send(CommandSender to, String key, Object... kv) {
        for (String line : lines(key, kv)) {
            to.sendMessage(Util.color(line));
        }
    }

    public void broadcast(String key, Object... kv) {
        for (String line : lines(key, kv)) {
            Bukkit.broadcast(Util.color(line));
        }
    }
}
