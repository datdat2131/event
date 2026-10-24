package me.astrax.event;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Nguyên Tố Thức Tỉnh: một nguyên tố được buff, hạ quái để tích điểm xếp hạng. */
public final class AwakeningEvent extends GameEvent {

    private String elementKey = "";
    private ConfigurationSection el;
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final List<PotionEffect> buffs = new ArrayList<>();
    private Particle particle = Particle.FLAME;
    private Set<String> worlds = new HashSet<>();
    private RewardTable rewards;
    private Reward killReward;

    public AwakeningEvent(AstraxEventPlugin plugin) {
        super(plugin, "awakening");
    }

    @Override
    protected String defaultName() {
        return "Nguyên Tố Thức Tỉnh";
    }

    private String elName() {
        return el == null ? "" : el.getString("color", "&f") + el.getString("name", elementKey);
    }

    @Override
    protected String barColorName() {
        return el == null ? super.barColorName() : el.getString("bar-color", super.barColorName());
    }

    @Override
    protected boolean onStart() {
        ConfigurationSection els = cfg.getConfigurationSection("elements");
        if (els == null || els.getKeys(false).isEmpty()) {
            plugin.getLogger().warning("[awakening] Chưa cấu hình mục 'elements'.");
            return false;
        }

        // Chọn nguyên tố: theo lệnh admin, nếu không thì ngẫu nhiên trong pool
        String chosen = null;
        if (arg != null && els.isConfigurationSection(arg.toLowerCase(Locale.ROOT))) {
            chosen = arg.toLowerCase(Locale.ROOT);
        }
        if (chosen == null) {
            List<String> pool = new ArrayList<>();
            for (String k : cfg.getStringList("pool")) {
                if (els.isConfigurationSection(k.toLowerCase(Locale.ROOT))) pool.add(k.toLowerCase(Locale.ROOT));
            }
            if (pool.isEmpty()) pool.addAll(els.getKeys(false));
            chosen = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        }
        elementKey = chosen;
        el = els.getConfigurationSection(chosen);
        if (el == null) return false;

        kills.clear();
        buffs.clear();
        for (String s : el.getStringList("buffs")) {
            String[] p = s.split(":");
            PotionEffectType type = Util.effectType(p[0]);
            if (type == null) {
                plugin.getLogger().warning("[awakening] Hiệu ứng không hợp lệ: " + s);
                continue;
            }
            int amp = p.length > 1 ? Util.parseInt(p[1], 0) : 0;
            buffs.add(new PotionEffect(type, 100, amp, true, false, true));
        }
        particle = Util.particle(el.getString("particle", "FLAME"));
        worlds = new HashSet<>(cfg.getStringList("worlds"));
        rewards = RewardTable.load(cfg.getConfigurationSection("rewards"), plugin);
        ConfigurationSection kr = cfg.getConfigurationSection("kill-reward");
        killReward = kr == null ? null : Reward.from(kr, plugin);

        plugin.msg().broadcast("awakening.start", "event", displayName(), "element", elName(),
                "minutes", totalTime / 60);
        String title = plugin.msg().text("awakening.title", "element", elName());
        String subtitle = plugin.msg().text("awakening.subtitle", "element", elName());
        for (Player p : Bukkit.getOnlinePlayers()) Util.title(p, title, subtitle);
        Util.soundAll(cfg.getString("sound-start", ""));
        return true;
    }

    /** Người chơi có được hưởng buff/tính điểm không. */
    private boolean qualified(Player p) {
        if (!worlds.isEmpty() && !worlds.contains(p.getWorld().getName())) return false;
        String ph = cfg.getString("player-placeholder", "");
        if (ph.isEmpty() || !plugin.hasPapi()) return true;
        String value = Util.strip(PapiHook.parse(p, ph)).toLowerCase(Locale.ROOT);
        if (value.isBlank()) return false;
        for (String m : el.getStringList("match")) {
            if (value.contains(m.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    @Override
    protected void onTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!qualified(p)) continue;
            for (PotionEffect effect : buffs) p.addPotionEffect(effect);
            p.getWorld().spawnParticle(particle, p.getLocation().add(0, 1, 0), 6, 0.35, 0.6, 0.35, 0.01);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead instanceof Player) return;
        Player killer = dead.getKiller();
        if (killer == null || !qualified(killer)) return;
        if (!cfg.getBoolean("count-spawner-mobs", false) && dead.fromMobSpawner()) return;

        int n = kills.merge(killer.getUniqueId(), 1, Integer::sum);
        if (killReward != null) {
            killReward.give(plugin, killer, Map.of("element", elementKey, "kills", String.valueOf(n)));
        }
        killer.sendActionBar(Util.color(plugin.msg().text("awakening.actionbar",
                "element", elName(), "kills", n)));
    }

    @Override
    protected void onStop(Reason reason) {
        if (reason == Reason.COMPLETED || reason == Reason.TIMEOUT) {
            plugin.msg().broadcast("awakening.end", "event", displayName(), "element", elName());
            finishRanking(kills, rewards, cfg.getInt("min-kills", 10), null);
        } else if (reason == Reason.ADMIN) {
            plugin.msg().broadcast("common.cancelled", "event", displayName());
        }
    }

    @Override
    protected String barTitle() {
        return plugin.msg().text("awakening.bar", "event", displayName(), "element", elName(),
                "time", Util.time(timeLeft));
    }

    @Override
    public String detail() {
        return elName();
    }
}
