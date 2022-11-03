package dev.unnm3d.rediseconomy;

import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import lombok.AllArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

@AllArgsConstructor
public class JoinListener implements Listener {

    private final CurrenciesManager currenciesManager;

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        currenciesManager.getCurrencies().forEach(currency -> {
            currency.getAccountRedis(e.getPlayer().getUniqueId()).thenAccept(balance -> {
                if (balance == null) {
                    currency.createPlayerAccount(e.getPlayer());
                } else {
                    currency.updateAccountLocal(e.getPlayer().getUniqueId(), e.getPlayer().getName(), balance);
                }
            });
        });
    }
}
