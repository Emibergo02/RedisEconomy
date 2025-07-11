package dev.unnm3d.rediseconomy.utils;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.config.Langs;
import dev.unnm3d.rediseconomy.currency.CurrenciesManager;
import dev.unnm3d.rediseconomy.currency.Currency;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Relational;
import net.milkbowl.vault.chat.Chat;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.util.*;


public class PlaceholderAPIHook extends PlaceholderExpansion implements Relational {

    private final CurrenciesManager currenciesManager;
    private final Langs langs;
    private final HashMap<Currency, Double> totalSupplyCache;
    private final HashMap<Currency, List<String[]>> baltopCache;
    private final int updateCachePeriod;
    private final int baltopPlaceholderAccounts;
    private final RegisteredServiceProvider<Chat> prefixProvider;
    private final RedisEconomyPlugin plugin;
    private long lastUpdateTimestamp;

    public PlaceholderAPIHook(RedisEconomyPlugin redisEconomyPlugin) {
        this.currenciesManager = redisEconomyPlugin.getCurrenciesManager();
        this.langs = redisEconomyPlugin.langs();
        this.totalSupplyCache = new HashMap<>();
        this.baltopCache = new HashMap<>();
        this.updateCachePeriod = redisEconomyPlugin.getConfigManager().getSettings().placeholderCacheUpdateInterval;
        this.baltopPlaceholderAccounts = redisEconomyPlugin.getConfigManager().getSettings().baltopPlaceholderAccounts;
        this.lastUpdateTimestamp = 0;
        this.plugin = redisEconomyPlugin;
        this.prefixProvider = redisEconomyPlugin.getServer().getServicesManager().getRegistration(net.milkbowl.vault.chat.Chat.class);
        updatePlaceholdersCache();
    }

    private void updatePlaceholdersCache() {
        if (System.currentTimeMillis() - lastUpdateTimestamp < updateCachePeriod) return;
        for (Currency currency : currenciesManager.getCurrencies()) {
            //Total Supply
            double totalSupply = 0;
            for (Map.Entry<UUID, Double> uuidDoubleEntry : currency.getAccounts().entrySet()) {
                totalSupply += uuidDoubleEntry.getValue();
            }
            totalSupplyCache.put(currency, totalSupply);

            //Balance top
            currency.getOrderedAccounts(baltopPlaceholderAccounts).thenAccept(accounts -> {
                List<String[]> baltopList = new ArrayList<>();
                for (int i = 0; i < baltopPlaceholderAccounts; i++) {
                    if (accounts.size() <= i) break;

                    //Extract data from vault and cache
                    String worldName = plugin.getServer().getWorlds().get(0).getName();
                    UUID fromString = UUID.fromString(accounts.get(i).getValue());
                    baltopList.add(new String[]{
                            prefixProvider.getProvider().getPlayerPrefix(worldName, plugin.getServer().getOfflinePlayer(fromString)),
                            prefixProvider.getProvider().getPlayerSuffix(worldName, plugin.getServer().getOfflinePlayer(fromString)),
                            currenciesManager.getUsernameFromUUIDCache(fromString) == null ? "Unknown" : currenciesManager.getUsernameFromUUIDCache(fromString),
                            String.valueOf(accounts.get(i).getScore())
                    });
                }
                baltopCache.put(currency, baltopList);
            });
        }
        lastUpdateTimestamp = System.currentTimeMillis();
    }


    @Override
    public @NotNull String getAuthor() {
        return "Unnm3d";
    }

    @Override
    public @NotNull String getIdentifier() {
        return "rediseco";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    // %rediseco_bal_<currency>%
    // %rediseco_bal_formatted_<currency>%
    // %rediseco_bal_formatted_shorthand_<currency>%
    // %rediseco_top_1_name_shorthand_<currency>%
    // %rediseco_top_1_bal_shorthand_<currency>%
    // %rediseco_singular_<currency>%
    // %rediseco_plural_<currency>%
    @Override
    public String onRequest(OfflinePlayer player, String params) {
        List<String> splitted = List.of(params.split("_"));
        if (splitted.size() < 2) return null;
        Currency currency = currenciesManager.getCurrencyByName(splitted.get(splitted.size() - 1));
        if (currency == null) return "Invalid currency";

        if (splitted.get(0).equals("bal")) {
            double balance = currency.getBalance(player);
            return parseParams(balance, splitted, currency);

        }

        if (splitted.get(0).equals("singular")) {
            return currency.getCurrencySingular();
        }
        if (splitted.get(0).equals("plural")) {
            return currency.getCurrencyPlural();
        }

        updatePlaceholdersCache();

        switch (splitted.get(0)) {
            case "totsupply" -> {
                return parseParams(totalSupplyCache.get(currency), splitted, currency);
            }
            case "maxbal" -> {
                return String.valueOf(currency.getPlayerMaxBalance(player.getUniqueId()));
            }
            case "top" -> {

                if (splitted.size() < 3) return null; //Insufficient parameters

                List<String[]> user_balance_strings = baltopCache.get(currency);
                if (user_balance_strings == null) return null;

                if (splitted.get(1).equals("position")) {//rediseco_top_position_<currency>
                    for (int i = 0; i < user_balance_strings.size(); i++) {
                        if (user_balance_strings.get(i)[2].equals(player.getName())) {
                            return String.valueOf(i + 1);
                        }
                    }
                    return baltopPlaceholderAccounts + "+";
                }

                int position = Integer.parseInt(splitted.get(1));
                if (position < 1 || position > baltopPlaceholderAccounts) return "N/A"; //Invalid positions
                if (user_balance_strings.size() < position) return "N/A";

                switch (splitted.get(2)) {
                    case "playerprefix" -> {
                        return user_balance_strings.get(position - 1)[0];
                    }
                    case "playersuffix" -> {
                        return user_balance_strings.get(position - 1)[1];
                    }
                    case "name" -> {
                        return user_balance_strings.get(position - 1)[2];
                    }
                    case "bal" -> {
                        double balance = Double.parseDouble(user_balance_strings.get(position - 1)[3]);
                        return parseParams(balance, splitted, currency);
                    }
                }
            }
        }
        return null;
    }

    private String parseParams(double amount, List<String> splitted, Currency currency) {

        String formattedNumber = currency.getDecimalFormat().format(amount);

        if (splitted.contains("short")) {
            if (amount >= 1000000000000.0) {
                formattedNumber = currency.getDecimalFormat().format(amount / 1000000000000.0) + langs.unitSymbols.trillion();
            } else if (amount >= 1000000000.0) {
                formattedNumber = currency.getDecimalFormat().format(amount / 1000000000.0) + langs.unitSymbols.billion();
            } else if (amount >= 1000000.0) {
                formattedNumber = currency.getDecimalFormat().format(amount / 1000000.0) + langs.unitSymbols.million();
            } else if (amount >= 1000.0) {
                formattedNumber = currency.getDecimalFormat().format(amount / 1000.0) + langs.unitSymbols.thousand();
            }
        }

        formattedNumber = splitted.stream()
                .filter(s -> s.startsWith("decformat"))
                .findFirst()
                .map(s -> s.split("decformat"))
                .filter(splittedFormat -> splittedFormat.length > 1)
                .map(splittedFormat -> new DecimalFormat(splittedFormat[1]).format(amount))
                .orElse(formattedNumber);

        if (splitted.contains("formatted")) {
            if (amount == 1)
                formattedNumber += currency.getCurrencySingular();
            else
                formattedNumber += currency.getCurrencyPlural();
        }
        return formattedNumber;
    }

    // %rel_rediseco_is_pay_blocking%
    // %rel_rediseco_is_pay_blocked%
    @Override
    public String onPlaceholderRequest(Player one, Player two, String params) {
        if (params.equalsIgnoreCase("is_pay_blocking")) {
            if (plugin.getCurrenciesManager().getLockedAccounts(one.getUniqueId()).contains(two.getUniqueId())) {
                return "true";
            }
            return "false";
        } else if (params.equalsIgnoreCase("is_pay_blocked")) {
            if (plugin.getCurrenciesManager().getLockedAccounts(two.getUniqueId()).contains(one.getUniqueId())) {
                return "true";
            }
            return "false";
        }
        return onRequest(one, params);
    }
}
