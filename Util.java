package me.astrax.event;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class Util {

    private static final LegacyComponentSerializer AMP = LegacyComponentSerializer.legacyAmpersand();

    private Util() {}

    // ---------- Văn bản ----------

    public static Component color(String s) {
        return AMP.deserialize(s == null ? "" : s);
    }

    public static String strip(String s) {
        return s == null ? "" : s.replaceAll("(?i)[§&][0-9A-FK-ORX]", "");
    }

    public static String time(int seconds) {
        int s = Math.max(0, seconds);
        int h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec);
        return String.format(Locale.ROOT, "%02d:%02d", m, sec);
    }

    public static String humanMinutes(long mins) {
        if (mins >= 60) return (mins / 60) + "g " + (mins % 60) + "p";
        return Math.max(0, mins) + "p";
    }

    public static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    // ---------- Âm thanh / tiêu đề ----------

    public static void sound(Player p, String key) {
        if (key == null || key.isBlank()) return;
        p.playSound(p.getLocation(), key, 1f, 1f);
    }

    public static void soundAll(String key) {
        for (Player p : Bukkit.getOnlinePlayers()) sound(p, key);
    }

    public static void title(Player p, String title, String subtitle) {
        p.showTitle(Title.title(color(title), color(subtitle),
                Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(3), Duration.ofMillis(800))));
    }

    // ---------- Hạt / hiệu ứng (ánh xạ tên -> hằng số, tránh phụ thuộc kiểu enum của API) ----------

    public static Particle particle(String name) {
        if (name == null) return Particle.FLAME;
        switch (name.trim().toUpperCase(Locale.ROOT)) {
            case "SNOWFLAKE": return Particle.SNOWFLAKE;
            case "ELECTRIC_SPARK": return Particle.ELECTRIC_SPARK;
            case "CLOUD": return Particle.CLOUD;
            case "END_ROD": return Particle.END_ROD;
            case "PORTAL": return Particle.PORTAL;
            case "CRIT": return Particle.CRIT;
            case "HEART": return Particle.HEART;
            case "SOUL_FIRE_FLAME": return Particle.SOUL_FIRE_FLAME;
            case "ENCHANT": return Particle.ENCHANT;
            default: return Particle.FLAME;
        }
    }

    public static PotionEffectType effectType(String name) {
        if (name == null) return null;
        return Registry.EFFECT.get(NamespacedKey.minecraft(name.trim().toLowerCase(Locale.ROOT)));
    }

    // ---------- Vị trí ----------

    public static String key(Block b) {
        return b.getWorld().getName() + "," + b.getX() + "," + b.getY() + "," + b.getZ();
    }

    /** "world,x,y,z" -> Location */
    public static Location parseLocation(String s) {
        try {
            String[] p = s.split(",");
            World w = Bukkit.getWorld(p[0].trim());
            if (w == null) return null;
            return new Location(w, Double.parseDouble(p[1].trim()) + 0.5,
                    Double.parseDouble(p[2].trim()), Double.parseDouble(p[3].trim()) + 0.5);
        } catch (Exception e) {
            return null;
        }
    }

    public static World pickWorld(List<String> names) {
        List<World> ws = new ArrayList<>();
        for (String n : names) {
            World w = Bukkit.getWorld(n);
            if (w != null) ws.add(w);
        }
        if (ws.isEmpty()) return Bukkit.getWorlds().get(0);
        return ws.get(ThreadLocalRandom.current().nextInt(ws.size()));
    }

    /** Chọn vị trí theo mục "spawn" trong config. Trả về null nếu không tìm được. */
    public static Location pickSpawn(ConfigurationSection sp, World world) {
        if (sp == null) sp = new MemoryConfiguration();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if ("fixed".equalsIgnoreCase(sp.getString("mode", "random"))) {
            List<Location> locs = new ArrayList<>();
            for (String s : sp.getStringList("fixed-locations")) {
                Location l = parseLocation(s);
                if (l != null) locs.add(l);
            }
            if (locs.isEmpty()) return null;
            return locs.get(rnd.nextInt(locs.size()));
        }
        double cx, cz;
        if (sp.getBoolean("use-world-spawn", true)) {
            Location s = world.getSpawnLocation();
            cx = s.getX();
            cz = s.getZ();
        } else {
            cx = sp.getDouble("center-x");
            cz = sp.getDouble("center-z");
        }
        return randomSurface(world, cx, cz, sp.getInt("min-radius", 100), sp.getInt("max-radius", 1000));
    }

    public static Location randomSurface(World w, double cx, double cz, int minR, int maxR) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int max = Math.max(maxR, minR + 1);
        for (int i = 0; i < 25; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double dist = minR + r.nextDouble() * (max - minR);
            int x = (int) Math.floor(cx + Math.cos(ang) * dist);
            int z = (int) Math.floor(cz + Math.sin(ang) * dist);
            if (!w.getWorldBorder().isInside(new Location(w, x, 64, z))) continue;
            int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Block b = w.getBlockAt(x, y, z);
            if (b.isLiquid()) continue;
            return new Location(w, x + 0.5, y + 1, z + 0.5);
        }
        return null;
    }

    /** Vị trí ngẫu nhiên quanh tâm (cùng độ cao bề mặt). */
    public static Location around(Location c, double minR, double maxR) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double ang = r.nextDouble() * Math.PI * 2;
        double dist = minR + r.nextDouble() * Math.max(0.1, maxR - minR);
        int x = (int) Math.floor(c.getX() + Math.cos(ang) * dist);
        int z = (int) Math.floor(c.getZ() + Math.sin(ang) * dist);
        int y = c.getWorld().getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
        return new Location(c.getWorld(), x + 0.5, y, z + 0.5);
    }

    public static List<Player> nearby(Location c, double radius) {
        List<Player> out = new ArrayList<>();
        if (c.getWorld() == null) return out;
        double r2 = radius * radius;
        for (Player p : c.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(c) <= r2) out.add(p);
        }
        return out;
    }

    // ---------- Xếp hạng ----------

    public static List<Map.Entry<UUID, Double>> ranking(Map<UUID, ? extends Number> scores) {
        List<Map.Entry<UUID, Double>> list = new ArrayList<>();
        for (Map.Entry<UUID, ? extends Number> e : scores.entrySet()) {
            list.add(Map.entry(e.getKey(), e.getValue().doubleValue()));
        }
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return list;
    }

    public static String name(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? "???" : n;
    }
}
