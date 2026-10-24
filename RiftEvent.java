package me.astrax.event;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Khe Nứt Nguyên Tố: dọn sạch từng đợt quái trước khi hết giờ. */
public final class RiftEvent extends GameEvent {

    private World world;
    private Location center;
    private List<String> waveKeys = new ArrayList<>();
    private int waveIndex = -1;
    private int waveTotal = 1;
    private int nextWaveIn;
    private boolean waveActive;
    private final Set<UUID> alive = new HashSet<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final List<int[]> tickets = new ArrayList<>();
    private RewardTable rewards;

    public RiftEvent(AstraxEventPlugin plugin) {
        super(plugin, "rift");
    }

    @Override
    protected String defaultName() {
        return "Khe Nứt Nguyên Tố";
    }

    @Override
    protected boolean onStart() {
        ConfigurationSection ws = cfg.getConfigurationSection("waves");
        waveKeys = ws == null ? new ArrayList<>() : new ArrayList<>(ws.getKeys(false));
        if (waveKeys.isEmpty()) {
            plugin.getLogger().warning("[rift] Chưa cấu hình mục 'waves'.");
            return false;
        }
        World w = Util.pickWorld(cfg.getStringList("worlds"));
        Location loc = Util.pickSpawn(cfg.getConfigurationSection("spawn"), w);
        if (loc == null) {
            plugin.getLogger().warning("[rift] Không tìm được vị trí spawn hợp lệ.");
            return false;
        }
        world = loc.getWorld();
        center = loc;
        rewards = RewardTable.load(cfg.getConfigurationSection("rewards"), plugin);

        // Giữ chunk quanh khe nứt luôn được nạp
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.addPluginChunkTicket(cx + dx, cz + dz, plugin);
                tickets.add(new int[]{cx + dx, cz + dz});
            }
        }

        waveIndex = -1;
        waveActive = false;
        waveTotal = 1;
        nextWaveIn = Math.max(1, cfg.getInt("first-wave-delay-seconds", 20));
        alive.clear();
        kills.clear();

        boolean show = cfg.getBoolean("announce-coordinates", true);
        plugin.msg().broadcast("rift.start", "event", displayName(),
                "coords", show ? coords(loc) : plugin.msg().text("common.coords-hidden"),
                "waves", waveKeys.size(), "minutes", totalTime / 60);
        String title = plugin.msg().text("rift.title");
        String subtitle = plugin.msg().text("rift.subtitle");
        for (Player p : Bukkit.getOnlinePlayers()) Util.title(p, title, subtitle);
        Util.soundAll(cfg.getString("sound-start", ""));
        return true;
    }

    @Override
    protected void onTick() {
        drawRift();
        alive.removeIf(id -> {
            Entity e = Bukkit.getEntity(id);
            return e == null || !e.isValid();
        });

        if (!waveActive) {
            nextWaveIn--;
            if (nextWaveIn <= 0) spawnWave();
            return;
        }

        // Còn ít quái thì cho phát sáng để dễ tìm
        if (alive.size() <= 3) {
            for (UUID id : alive) {
                Entity e = Bukkit.getEntity(id);
                if (e != null) e.setGlowing(true);
            }
        }

        if (alive.isEmpty()) {
            int delay = Math.max(1, cfg.getInt("wave-delay-seconds", 10));
            if (waveIndex >= waveKeys.size() - 1) {
                stop(Reason.COMPLETED);
                return;
            }
            notifyNearby("rift.wave-cleared", "wave", waveIndex + 1, "waves", waveKeys.size(), "delay", delay);
            waveActive = false;
            nextWaveIn = delay;
        }
    }

    private void spawnWave() {
        waveIndex++;
        alive.clear();
        double mult = 1 + waveIndex * cfg.getDouble("health-multiplier-per-wave", 0.0);
        String mythicCmd = cfg.getString("mythic-command", Spawner.DEFAULT_MYTHIC);

        for (String entry : cfg.getStringList("waves." + waveKeys.get(waveIndex))) {
            String[] p = entry.split(":");
            boolean mythic = p[0].trim().equalsIgnoreCase("mm");
            String type;
            int amount;
            if (mythic) {
                if (p.length < 2) continue;
                type = p[1].trim();
                amount = p.length > 2 ? Util.parseInt(p[2], 1) : 1;
            } else {
                type = p[0].trim();
                amount = p.length > 1 ? Util.parseInt(p[1], 1) : 1;
            }
            for (int i = 0; i < amount; i++) {
                Location l = Util.around(center, 4, 10);
                LivingEntity le;
                if (mythic) {
                    le = plugin.hasMythic() ? Spawner.mythic(mythicCmd, type, l) : null;
                } else {
                    le = Spawner.vanilla(type, l);
                }
                if (le == null) continue;
                Spawner.prep(le);
                if (mult > 1) Spawner.scaleHealth(le, mult);
                if (cfg.getBoolean("glowing", false)) le.setGlowing(true);
                alive.add(le.getUniqueId());
            }
        }
        waveTotal = Math.max(1, alive.size());
        waveActive = true;

        String title = plugin.msg().text("rift.wave-title", "wave", waveIndex + 1, "waves", waveKeys.size());
        String subtitle = plugin.msg().text("rift.wave-subtitle", "count", alive.size());
        for (Player p : Util.nearby(center, cfg.getDouble("join-radius", 30) * 1.5)) {
            Util.title(p, title, subtitle);
        }
    }

    private void notifyNearby(String key, Object... kv) {
        for (Player p : Util.nearby(center, cfg.getDouble("join-radius", 30) * 1.5)) {
            plugin.msg().send(p, key, kv);
        }
    }

    private void drawRift() {
        double r = cfg.getDouble("ring-radius", 3);
        int points = 20;
        double offset = (timeLeft % 10) * 0.1;
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points + offset;
            world.spawnParticle(Particle.PORTAL,
                    center.getX() + Math.cos(a) * r, center.getY() + 0.2, center.getZ() + Math.sin(a) * r,
                    2, 0.1, 0.4, 0.1, 0.05);
        }
        for (int h = 0; h < 8; h++) {
            world.spawnParticle(Particle.END_ROD, center.getX(), center.getY() + h, center.getZ(),
                    1, 0.1, 0, 0.1, 0.01);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent e) {
        UUID id = e.getEntity().getUniqueId();
        if (!alive.remove(id)) return;
        if (cfg.getBoolean("clear-drops", false)) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
        Player killer = e.getEntity().getKiller();
        if (killer != null) kills.merge(killer.getUniqueId(), 1, Integer::sum);
    }

    @Override
    protected void onStop(Reason reason) {
        double min = cfg.getInt("min-kills", 3);
        if (reason == Reason.COMPLETED) {
            plugin.msg().broadcast("rift.complete");
            finishRanking(kills, rewards, min, "completion");
        } else if (reason == Reason.TIMEOUT) {
            plugin.msg().broadcast("rift.failed");
            Reward failure = rewards.get("failure");
            if (failure != null) {
                for (Map.Entry<UUID, Integer> k : kills.entrySet()) {
                    Player p = Bukkit.getPlayer(k.getKey());
                    if (p != null && k.getValue() >= min) {
                        failure.give(plugin, p, Map.of("event", displayName()));
                    }
                }
            }
        } else if (reason == Reason.ADMIN) {
            plugin.msg().broadcast("common.cancelled", "event", displayName());
        }

        for (UUID id : alive) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        alive.clear();
        if (world != null) {
            for (int[] t : tickets) world.removePluginChunkTicket(t[0], t[1], plugin);
        }
        tickets.clear();
    }

    @Override
    protected float progress() {
        return waveActive ? (float) alive.size() / waveTotal : 1f;
    }

    @Override
    protected String barTitle() {
        return plugin.msg().text("rift.bar", "event", displayName(),
                "wave", Math.max(0, waveIndex + 1), "waves", waveKeys.size(),
                "alive", alive.size(), "time", Util.time(timeLeft));
    }

    @Override
    public String detail() {
        return plugin.msg().text("rift.detail", "wave", Math.max(0, waveIndex + 1),
                "waves", waveKeys.size(), "alive", alive.size());
    }

    @Override
    public String where() {
        if (center == null) return null;
        if (!cfg.getBoolean("announce-coordinates", true)) return plugin.msg().text("common.coords-hidden");
        return plugin.msg().text("rift.where", "coords", coords(center));
    }
}
