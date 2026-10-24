package me.astrax.event;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class AstraxEventPlugin extends JavaPlugin {

    private Msg msg;
    private EventManager manager;
    private DailyStreak daily;
    private EconomyHook economy;
    private boolean vault;
    private boolean papi;
    private boolean mythic;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "messages.yml").exists()) {
            saveResource("messages.yml", false);
        }
        vault = Bukkit.getPluginManager().getPlugin("Vault") != null;
        papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        mythic = Bukkit.getPluginManager().getPlugin("MythicMobs") != null;

        msg = new Msg(this);
        manager = new EventManager(this);
        daily = new DailyStreak(this);
        getServer().getPluginManager().registerEvents(manager, this);
        getServer().getPluginManager().registerEvents(daily, this);

        EventCommand handler = new EventCommand(this);
        bind("event", handler);
        bind("diemdanh", handler);

        manager.startScheduler();

        if (papi) {
            new EventExpansion(this).register();
        }
        getLogger().info("AstraxEvent đã bật. Vault=" + vault + ", PlaceholderAPI=" + papi + ", MythicMobs=" + mythic);
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
        if (daily != null) daily.saveNow();
    }

    private void bind(String name, EventCommand handler) {
        PluginCommand c = getCommand(name);
        if (c != null) {
            c.setExecutor(handler);
            c.setTabCompleter(handler);
        }
    }

    public void reloadAll() {
        reloadConfig();
        msg.reload();
        manager.reload();
    }

    /** Gửi tiền qua Vault (nếu có). */
    public boolean deposit(OfflinePlayer player, double amount) {
        if (!vault || amount <= 0) return false;
        if (economy == null) economy = EconomyHook.create(this);
        if (economy == null) return false;
        economy.deposit(player, amount);
        return true;
    }

    public Msg msg() { return msg; }
    public EventManager manager() { return manager; }
    public DailyStreak daily() { return daily; }
    public boolean hasPapi() { return papi; }
    public boolean hasMythic() { return mythic; }
}
