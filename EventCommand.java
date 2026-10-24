package me.astrax.event;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class EventCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("status", "where", "start", "stop", "reload", "help");

    private final AstraxEventPlugin plugin;

    public EventCommand(AstraxEventPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (cmd.getName().equalsIgnoreCase("diemdanh")) {
            claim(sender);
            return true;
        }
        if (args.length == 0) {
            status(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status", "list" -> status(sender);
            case "where" -> where(sender);
            case "daily", "diemdanh" -> claim(sender);
            case "start" -> {
                if (!admin(sender)) return true;
                if (args.length < 2) {
                    plugin.msg().send(sender, "cmd.help");
                    return true;
                }
                String err = plugin.manager().start(args[1], args.length > 2 ? args[2] : null, true);
                if (err != null) {
                    sender.sendMessage(Util.color(err));
                } else {
                    plugin.msg().send(sender, "common.started", "event", plugin.manager().get(args[1]).displayName());
                }
            }
            case "stop" -> {
                if (!admin(sender)) return true;
                plugin.msg().send(sender, plugin.manager().stopActive() ? "common.stopped" : "common.nothing-running");
            }
            case "reload" -> {
                if (!admin(sender)) return true;
                plugin.reloadAll();
                plugin.msg().send(sender, "common.reloaded");
            }
            default -> plugin.msg().send(sender, "cmd.help");
        }
        return true;
    }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("astraxevent.admin")) return true;
        plugin.msg().send(s, "common.no-permission");
        return false;
    }

    private void claim(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            plugin.msg().send(sender, "common.players-only");
            return;
        }
        plugin.daily().claim(p);
    }

    private void status(CommandSender sender) {
        plugin.msg().send(sender, "cmd.status-header");
        GameEvent active = plugin.manager().active();
        for (GameEvent e : plugin.manager().all()) {
            String state = e == active
                    ? plugin.msg().text("cmd.state-running", "time", Util.time(e.timeLeft()), "detail", e.detail())
                    : plugin.manager().nextText(e);
            plugin.msg().send(sender, "cmd.status-line", "name", e.displayName(), "id", e.id(), "state", state);
        }
    }

    private void where(CommandSender sender) {
        GameEvent a = plugin.manager().active();
        String w = a == null ? null : a.where();
        if (w == null) {
            plugin.msg().send(sender, "common.where-none");
            return;
        }
        for (String line : w.split("\n")) sender.sendMessage(Util.color(line));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (!cmd.getName().equalsIgnoreCase("event")) return out;
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            for (GameEvent e : plugin.manager().all()) {
                if (e.id().startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(e.id());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("start") && args[1].equalsIgnoreCase("awakening")) {
            ConfigurationSection els = plugin.getConfig().getConfigurationSection("events.awakening.elements");
            if (els != null) {
                for (String k : els.getKeys(false)) if (k.startsWith(args[2].toLowerCase(Locale.ROOT))) out.add(k);
            }
        }
        return out;
    }
}
