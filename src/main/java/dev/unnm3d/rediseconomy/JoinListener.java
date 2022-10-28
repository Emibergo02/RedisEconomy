package dev.unnm3d.rediseconomy;

import lombok.AllArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

@AllArgsConstructor
public class JoinListener implements Listener {

    private final RedisEconomy economy;

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        economy.createPlayerAccount(e.getPlayer());
    }
}
