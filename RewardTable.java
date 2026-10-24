package me.astrax.event;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Tập phần thưởng đặt tên: rank-1, rank-2, participation, completion, loot... */
public final class RewardTable {

    private final Map<String, Reward> map = new LinkedHashMap<>();

    private RewardTable() {}

    public static RewardTable load(ConfigurationSection cs, AstraxEventPlugin plugin) {
        RewardTable t = new RewardTable();
        if (cs == null) return t;
        for (String key : cs.getKeys(false)) {
            ConfigurationSection s = cs.getConfigurationSection(key);
            if (s != null) t.map.put(key.toLowerCase(Locale.ROOT), Reward.from(s, plugin));
        }
        return t;
    }

    public Reward get(String key) {
        return map.get(key.toLowerCase(Locale.ROOT));
    }

    public Reward rank(int n) {
        return map.get("rank-" + n);
    }

    public Collection<Reward> all() {
        return map.values();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }
}
