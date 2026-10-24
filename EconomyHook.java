package me.astrax.event;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** Chỉ được nạp khi server có Vault (Vault là softdepend). */
final class EconomyHook {

    private final Economy economy;

    private EconomyHook(Economy economy) {
        this.economy = economy;
    }

    static EconomyHook create(JavaPlugin plugin) {
        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : new EconomyHook(rsp.getProvider());
    }

    void deposit(OfflinePlayer player, double amount) {
        economy.depositPlayer(player, amount);
    }
}
