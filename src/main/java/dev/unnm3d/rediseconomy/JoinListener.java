package dev.unnm3d.rediseconomy;

import dev.unnm3d.rediseconomy.vaultcurrency.RedisEconomy;
import lombok.AllArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

@AllArgsConstructor
public class JoinListener implements Listener {

    private final RedisEconomy economy;

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        economy.getAccountRedis(e.getPlayer().getUniqueId()).thenAccept(balance -> {
            if (balance == null) {
                economy.createPlayerAccount(e.getPlayer());
            } else {
                economy.updateAccountLocal(e.getPlayer().getUniqueId(), e.getPlayer().getName(), balance);
            }
        });
    }
}
