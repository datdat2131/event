package me.astrax.event;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Boss Thế Giới: thưởng theo sát thương gây ra + đòn kết liễu. */
public final class WorldBossEvent extends GameEvent {

    private UUID bossId;
    private Location spawnLoc;
    private double maxHp = 1;
    private double hpRatio = 1;
    private boolean killed;
    private UUID killerId;
    private final Map<UUID, Double> damage = new HashMap<>();
    private RewardTable rewards;
    private Particle aura = Particle.FLAME;
    private World chunkWorld;
    private int chunkX;
    private int chunkZ;

    public WorldBossEvent(AstraxEventPlugin plugin) {
        super(plugin, "boss");
    }

    @Override
    protected String defaultName() {
        return "Boss Thế Giới";
    }

    private String bossName() {
        return cfg.getString("boss.name", "&c&lBoss Thế Giới");
    }

    @Override
    protected boolean onStart() {
        damage.clear();
        killed = false;
        killerId = null;
        hpRatio = 1;
        rewards = RewardTable.load(cfg.getConfigurationSection("rewards"), plugin);
        aura = Util.particle(cfg.getString("boss.aura-particle", "FLAME"));

        World w = Util.pickWorld(cfg.getStringList("worlds"));
        Location loc = Util.pickSpawn(cfg.getConfigurationSection("spawn"), w);
        if (loc == null) {
            plugin.getLogger().warning("[boss] Không tìm được vị trí spawn hợp lệ.");
            return false;
        }
        w = loc.getWorld();
        chunkWorld = w;
        chunkX = loc.getBlockX() >> 4;
        chunkZ = loc.getBlockZ() >> 4;
        w.addPluginChunkTicket(chunkX, chunkZ, plugin);

        LivingEntity boss = spawnBoss(loc);
        if (boss == null) {
            w.removePluginChunkTicket(chunkX, chunkZ, plugin);
            chunkWorld = null;
            plugin.getLogger().warning("[boss] Không spawn được boss (kiểm tra boss.type / mythic-id).");
            return false;
        }
        bossId = boss.getUniqueId();
        spawnLoc = loc.clone();
        AttributeInstance a = boss.getAttribute(Attribute.MAX_HEALTH);
        maxHp = Math.max(1, a != null ? a.getValue() : boss.getHealth());

        boolean show = cfg.getBoolean("announce-coordinates", true);
        String coords = show ? coords(loc) : plugin.msg().text("common.coords-hidden");
        plugin.msg().broadcast("boss.spawn", "event", displayName(), "name", bossName(),
                "coords", coords, "minutes", totalTime / 60, "hp", Math.round(maxHp));
        String title = plugin.msg().text("boss.title", "name", bossName());
        String subtitle = plugin.msg().text("boss.subtitle", "name", bossName());
        for (Player p : Bukkit.getOnlinePlayers()) Util.title(p, title, subtitle);
        Util.soundAll(cfg.getString("sound-start", ""));
        return true;
    }

    private LivingEntity spawnBoss(Location loc) {
        String mythicId = cfg.getString("boss.mythic-id", "");
        if (!mythicId.isBlank() && plugin.hasMythic()) {
            LivingEntity m = Spawner.mythic(cfg.getString("boss.mythic-command", Spawner.DEFAULT_MYTHIC),
                    mythicId, loc);
            if (m != null) {
                Spawner.prep(m);
                return m;
            }
            plugin.getLogger().warning("[boss] MythicMobs không spawn được '" + mythicId
                    + "', dùng boss vanilla thay thế.");
        }

        LivingEntity e = Spawner.vanilla(cfg.getString("boss.type", "WITHER_SKELETON"), loc);
        if (e == null) return null;
        Spawner.prep(e);
        e.customName(Util.color(bossName()));
        e.setCustomNameVisible(true);

        double health = cfg.getDouble("boss.health", 1500);
        AttributeInstance hp = e.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) {
            try {
                hp.setBaseValue(health);
            } catch (IllegalArgumentException ex) {
                hp.setBaseValue(1024);
                plugin.getLogger().warning("[boss] Máu vượt giới hạn server, dùng 1024.");
            }
            e.setHealth(Math.min(health, hp.getValue()));
        }
        AttributeInstance dmg = e.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmg != null && cfg.contains("boss.attack-damage")) {
            dmg.setBaseValue(cfg.getDouble("boss.attack-damage"));
        }
        e.setGlowing(cfg.getBoolean("boss.glowing", true));
        return e;
    }

    @Override
    protected void onTick() {
        Entity ent = Bukkit.getEntity(bossId);
        if (ent instanceof LivingEntity le && le.isValid()) {
            hpRatio = Math.min(1, Math.max(0, le.getHealth()) / maxHp);
            le.getWorld().spawnParticle(aura, le.getLocation().add(0, le.getHeight() / 2, 0),
                    12, 0.6, 0.8, 0.6, 0.02);
        }
        int every = cfg.getInt("reminder-seconds", 60);
        if (every > 0 && timeLeft > 0 && timeLeft % every == 0) {
            boolean show = cfg.getBoolean("announce-coordinates", true);
            Location l = ent != null ? ent.getLocation() : spawnLoc;
            plugin.msg().broadcast("boss.reminder", "name", bossName(),
                    "hp", Math.round(hpRatio * 100),
                    "coords", show ? coords(l) : plugin.msg().text("common.coords-hidden"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!e.getEntity().getUniqueId().equals(bossId)) return;
        Player p = attacker(e.getDamager());
        if (p == null) return;
        double remaining = e.getEntity() instanceof LivingEntity le ? le.getHealth() : e.getFinalDamage();
        damage.merge(p.getUniqueId(), Math.min(e.getFinalDamage(), remaining), Double::sum);
    }

    private Player attacker(Entity d) {
        if (d instanceof Player p) return p;
        if (d instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        if (d instanceof Tameable t && t.getOwnerUniqueId() != null) return Bukkit.getPlayer(t.getOwnerUniqueId());
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent e) {
        if (!e.getEntity().getUniqueId().equals(bossId)) return;
        if (cfg.getBoolean("clear-drops", true)) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
        killed = true;
        Player k = e.getEntity().getKiller();
        killerId = k == null ? null : k.getUniqueId();
        // Kết thúc ở tick kế tiếp để tránh xử lý ngay giữa sự kiện chết
        Bukkit.getScheduler().runTask(plugin, () -> stop(Reason.COMPLETED));
    }

    @Override
    protected void onStop(Reason reason) {
        Entity ent = bossId == null ? null : Bukkit.getEntity(bossId);
        if (reason == Reason.COMPLETED && killed) {
            Player k = killerId == null ? null : Bukkit.getPlayer(killerId);
            plugin.msg().broadcast("boss.killed", "name", bossName(), "player", k == null ? "???" : k.getName());
            finishRanking(damage, rewards, cfg.getDouble("min-damage", 100), null);
            Reward lastHit = rewards.get("last-hit");
            if (lastHit != null && k != null) {
                lastHit.give(plugin, k, Map.of("event", displayName()));
            }
        } else if (reason == Reason.TIMEOUT) {
            plugin.msg().broadcast("boss.escaped", "name", bossName());
            if (ent != null) ent.remove();
        } else {
            if (ent != null) ent.remove();
            if (reason == Reason.ADMIN) plugin.msg().broadcast("common.cancelled", "event", displayName());
        }
        if (chunkWorld != null) {
            chunkWorld.removePluginChunkTicket(chunkX, chunkZ, plugin);
            chunkWorld = null;
        }
    }

    @Override
    protected float progress() {
        return (float) hpRatio;
    }

    @Override
    protected String barTitle() {
        return plugin.msg().text("boss.bar", "name", bossName(),
                "hp", Math.round(hpRatio * 100), "time", Util.time(timeLeft));
    }

    @Override
    public String detail() {
        return plugin.msg().text("boss.detail", "hp", Math.round(hpRatio * 100));
    }

    @Override
    public String where() {
        if (!cfg.getBoolean("announce-coordinates", true)) return plugin.msg().text("common.coords-hidden");
        Entity ent = bossId == null ? null : Bukkit.getEntity(bossId);
        Location l = ent != null ? ent.getLocation() : spawnLoc;
        if (l == null) return null;
        return plugin.msg().text("boss.where", "name", bossName(), "coords", coords(l));
    }
}
