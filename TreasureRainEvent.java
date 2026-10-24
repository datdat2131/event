package me.astrax.event;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Mưa Rương Kho Báu: rương rơi khắp nơi, chuột phải trước người đó nhận. */
public final class TreasureRainEvent extends GameEvent {

    private final Map<String, Block> chests = new LinkedHashMap<>();
    private final Map<UUID, Integer> claims = new HashMap<>();
    private RewardTable loot;
    private int total;

    public TreasureRainEvent(AstraxEventPlugin plugin) {
        super(plugin, "treasure");
    }

    @Override
    protected String defaultName() {
        return "Mưa Rương Kho Báu";
    }

    @Override
    protected boolean onStart() {
        chests.clear();
        claims.clear();
        loot = RewardTable.load(cfg.getConfigurationSection("loot"), plugin);
        if (loot.isEmpty()) {
            plugin.getLogger().warning("[treasure] Chưa cấu hình mục 'loot'.");
            return false;
        }

        World w = Util.pickWorld(cfg.getStringList("worlds"));
        int count = Math.max(1, cfg.getInt("chest-count", 8));
        for (int i = 0; i < count; i++) {
            Location l = Util.pickSpawn(cfg.getConfigurationSection("spawn"), w);
            if (l == null) continue;
            Block b = l.getBlock();
            if (!b.getType().isAir()) continue;
            b.setType(Material.CHEST);
            chests.put(Util.key(b), b);
        }
        if (chests.isEmpty()) {
            plugin.getLogger().warning("[treasure] Không đặt được rương nào (kiểm tra spawn/worlds).");
            return false;
        }
        total = chests.size();

        plugin.msg().broadcast("treasure.start", "event", displayName(), "count", total, "minutes", totalTime / 60);
        for (String line : coordLines()) Bukkit.broadcast(Util.color(line));
        String title = plugin.msg().text("treasure.title");
        String subtitle = plugin.msg().text("treasure.subtitle");
        for (Player p : Bukkit.getOnlinePlayers()) Util.title(p, title, subtitle);
        Util.soundAll(cfg.getString("sound-start", ""));
        return true;
    }

    private List<String> coordLines() {
        String hint = cfg.getString("hint", "exact").toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        int n = 1;
        for (Block b : chests.values()) {
            if (hint.equals("exact")) {
                out.add(plugin.msg().text("treasure.coord-line", "n", n, "world", b.getWorld().getName(),
                        "x", b.getX(), "y", b.getY(), "z", b.getZ()));
            } else if (hint.equals("rounded")) {
                int rt = Math.max(1, cfg.getInt("round-to", 50));
                out.add(plugin.msg().text("treasure.coord-line-rounded", "n", n, "world", b.getWorld().getName(),
                        "x", Math.round(b.getX() / (double) rt) * rt, "z", Math.round(b.getZ() / (double) rt) * rt));
            }
            n++;
        }
        if (out.isEmpty()) out.add(plugin.msg().text("treasure.coord-hidden"));
        return out;
    }

    @Override
    protected void onTick() {
        for (Block b : chests.values()) {
            World w = b.getWorld();
            for (int h = 1; h <= 7; h += 2) {
                w.spawnParticle(Particle.END_ROD, b.getX() + 0.5, b.getY() + h, b.getZ() + 0.5, 1, 0.1, 0, 0.1, 0.01);
            }
        }
    }

    private Reward pick() {
        List<Reward> all = new ArrayList<>(loot.all());
        if (all.isEmpty()) return null;
        double sum = 0;
        for (Reward r : all) sum += Math.max(0, r.weight);
        double roll = ThreadLocalRandom.current().nextDouble() * sum;
        for (Reward r : all) {
            roll -= Math.max(0, r.weight);
            if (roll <= 0) return r;
        }
        return all.get(all.size() - 1);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null || !chests.containsKey(Util.key(b))) return;
        e.setCancelled(true);
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        claim(e.getPlayer(), b);
    }

    private void claim(Player p, Block b) {
        chests.remove(Util.key(b));
        b.setType(Material.AIR);
        b.getWorld().spawnParticle(Particle.END_ROD, b.getLocation().add(0.5, 1, 0.5), 40, 0.4, 0.6, 0.4, 0.1);
        Util.sound(p, "entity.player.levelup");

        String label = "";
        int rolls = Math.max(1, cfg.getInt("rolls", 1));
        for (int i = 0; i < rolls; i++) {
            Reward r = pick();
            if (r == null) continue;
            r.give(plugin, p, Map.of("event", displayName()));
            label = r.label;
        }
        claims.merge(p.getUniqueId(), 1, Integer::sum);
        plugin.msg().broadcast("treasure.claimed", "player", p.getName(), "label", label,
                "left", chests.size(), "total", total);
        if (chests.isEmpty()) stop(Reason.COMPLETED);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!chests.containsKey(Util.key(e.getBlock()))) return;
        e.setCancelled(true);
        plugin.msg().send(e.getPlayer(), "treasure.use-rightclick");
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> chests.containsKey(Util.key(b)));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> chests.containsKey(Util.key(b)));
    }

    @Override
    protected void onStop(Reason reason) {
        int remaining = chests.size();
        for (Block b : chests.values()) {
            if (b.getType() == Material.CHEST) b.setType(Material.AIR);
        }
        chests.clear();
        if (reason == Reason.COMPLETED || reason == Reason.TIMEOUT) {
            plugin.msg().broadcast("treasure.end", "claimed", total - remaining, "total", total);
        } else if (reason == Reason.ADMIN) {
            plugin.msg().broadcast("common.cancelled", "event", displayName());
        }
    }

    @Override
    protected float progress() {
        return total <= 0 ? 0f : (float) chests.size() / total;
    }

    @Override
    protected String barTitle() {
        return plugin.msg().text("treasure.bar", "event", displayName(),
                "left", chests.size(), "total", total, "time", Util.time(timeLeft));
    }

    @Override
    public String detail() {
        return plugin.msg().text("treasure.detail", "left", chests.size(), "total", total);
    }

    @Override
    public String where() {
        if (chests.isEmpty()) return null;
        return String.join("\n", coordLines());
    }
}
