package me.astrax.event;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Một phần thưởng: tiền (Vault), vật phẩm, lệnh console, tin nhắn. */
public final class Reward {

    public final String label;
    public final double weight;
    public final double chance;
    public final double money;
    public final List<String> commands;
    public final List<ItemStack> items;
    public final String message;

    private Reward(String label, double weight, double chance, double money,
                   List<String> commands, List<ItemStack> items, String message) {
        this.label = label;
        this.weight = weight;
        this.chance = chance;
        this.money = money;
        this.commands = commands;
        this.items = items;
        this.message = message;
    }

    public static Reward from(ConfigurationSection cs, AstraxEventPlugin plugin) {
        List<ItemStack> items = new ArrayList<>();
        for (String s : cs.getStringList("items")) {
            String[] p = s.split(":");
            Material m = Material.matchMaterial(p[0].trim());
            if (m == null || m.isAir()) {
                plugin.getLogger().warning("Vật phẩm không hợp lệ trong config (" + cs.getCurrentPath() + "): " + s);
                continue;
            }
            int amount = p.length > 1 ? Math.max(1, Util.parseInt(p[1], 1)) : 1;
            items.add(new ItemStack(m, amount));
        }
        return new Reward(
                cs.getString("label", cs.getName()),
                cs.getDouble("weight", 1),
                cs.getDouble("chance", 100),
                cs.getDouble("money", 0),
                cs.getStringList("commands"),
                items,
                cs.getString("message", ""));
    }

    /** Trao thưởng cho người chơi đang online. vars dùng để thay {biến} trong lệnh và tin nhắn. */
    public void give(AstraxEventPlugin plugin, Player p, Map<String, String> vars) {
        if (chance < 100 && ThreadLocalRandom.current().nextDouble() * 100 >= chance) return;

        if (money > 0) plugin.deposit(p, money);

        for (ItemStack it : items) {
            for (ItemStack left : p.getInventory().addItem(it.clone()).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), left);
            }
        }

        for (String c : commands) {
            String cmd = apply(c, p, vars);
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            if (!cmd.isBlank()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }

        if (!message.isEmpty()) p.sendMessage(Util.color(apply(message, p, vars)));
    }

    private static String apply(String s, Player p, Map<String, String> vars) {
        String out = s.replace("%player%", p.getName()).replace("{player}", p.getName());
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue());
        }
        return out;
    }
}
