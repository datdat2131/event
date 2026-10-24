package me.astrax.event;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Spawn quái vanilla hoặc MythicMobs (qua lệnh console) và chuẩn bị chúng cho event. */
public final class Spawner {

    public static final String DEFAULT_MYTHIC = "mm mobs spawn {mob} 1 {world},{x},{y},{z}";

    private Spawner() {}

    /** Spawn bằng lệnh MythicMobs, trả về thực thể mới xuất hiện gần vị trí (hoặc null). */
    public static LivingEntity mythic(String template, String mobId, Location loc) {
        Set<UUID> before = new HashSet<>();
        for (LivingEntity le : loc.getWorld().getNearbyLivingEntities(loc, 8)) {
            before.add(le.getUniqueId());
        }
        String cmd = template
                .replace("{mob}", mobId)
                .replace("{world}", loc.getWorld().getName())
                .replace("{x}", String.format(Locale.ROOT, "%.2f", loc.getX()))
                .replace("{y}", String.format(Locale.ROOT, "%.2f", loc.getY()))
                .replace("{z}", String.format(Locale.ROOT, "%.2f", loc.getZ()));
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        for (LivingEntity le : loc.getWorld().getNearbyLivingEntities(loc, 8)) {
            if (!before.contains(le.getUniqueId()) && !(le instanceof Player)) return le;
        }
        return null;
    }

    public static LivingEntity vanilla(String type, Location loc) {
        try {
            EntityType t = EntityType.valueOf(type.trim().toUpperCase(Locale.ROOT));
            Entity e = loc.getWorld().spawnEntity(loc, t);
            if (e instanceof LivingEntity le) return le;
            e.remove();
        } catch (IllegalArgumentException ignored) {
            // loại thực thể không hợp lệ hoặc không spawn được
        }
        return null;
    }

    /** Không tự biến mất, không cháy nắng. */
    public static void prep(LivingEntity e) {
        e.setRemoveWhenFarAway(false);
        e.setPersistent(true);
        e.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE,
                PotionEffect.INFINITE_DURATION, 0, false, false));
    }

    public static void scaleHealth(LivingEntity e, double mult) {
        AttributeInstance hp = e.getAttribute(Attribute.MAX_HEALTH);
        if (hp == null) return;
        double v = Math.min(1024, hp.getBaseValue() * mult);
        try {
            hp.setBaseValue(v);
            e.setHealth(Math.min(v, hp.getValue()));
        } catch (IllegalArgumentException ignored) {
            // vượt giới hạn máu của server
        }
    }
}
