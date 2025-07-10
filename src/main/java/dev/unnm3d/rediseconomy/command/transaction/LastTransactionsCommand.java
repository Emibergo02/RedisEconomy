package dev.unnm3d.rediseconomy.command.transaction;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.currency.Currency;
import dev.unnm3d.rediseconomy.transaction.AccountID;
import dev.unnm3d.rediseconomy.transaction.Transaction;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;


public class LastTransactionsCommand extends TransactionCommandAbstract implements CommandExecutor, TabCompleter {

    public LastTransactionsCommand(RedisEconomyPlugin plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        final AccountID accountID = new AccountID(player.getUniqueId());

        plugin.getCurrenciesManager().getExchange().getTransactions(accountID, 5).thenAccept(transactions -> {
            if (transactions.isEmpty()) {
                plugin.langs().send(sender, plugin.langs().noTransactionFound.replace("%player%", sender.getName()));
                return;
            }
            for (Map.Entry<Long, Transaction> integerTransactionEntry : transactions.entrySet()) {
                sendTransaction(sender, integerTransactionEntry.getKey(), integerTransactionEntry.getValue());
            }
        });

        return true;
    }

    @Override
    public void sendTransaction(CommandSender sender, long transactionId, Transaction transaction) {
        String accountOwnerName = transaction.getAccountIdentifier().isPlayer() ?//If the sender is a player
                plugin.getCurrenciesManager().getUsernameFromUUIDCache(transaction.getAccountIdentifier().getUUID()) : //Get the username from the cache (with server uuid translation)
                transaction.getAccountIdentifier().toString(); //Else, it's a bank, so we get the bank id
        String otherAccount = transaction.getActor().isPlayer() ?
                plugin.getCurrenciesManager().getUsernameFromUUIDCache(transaction.getActor().getUUID()) :
                transaction.getActor().toString();
        Currency currency = plugin.getCurrenciesManager().getCurrencyByName(transaction.getCurrencyName());

        String transactionMessage = plugin.langs().playerTransactionItem.incomingFunds();
        if (transaction.getAmount() < 0) {
            transactionMessage = plugin.langs().playerTransactionItem.outgoingFunds();
        }
        transactionMessage = transactionMessage
                .replace("%id%", String.valueOf(transactionId))
                .replace("%amount%", String.valueOf(transaction.getAmount()))
                .replace("%symbol%", currency == null ? "" : currency.getCurrencyPlural())
                .replace("%account-owner%", accountOwnerName == null ? "Unknown" : accountOwnerName)
                .replace("%other-account%", otherAccount == null ? "Unknown" : otherAccount)
                .replace("%timestamp%", convertTimeWithLocalTimeZome(transaction.getTimestamp()))
                .replace("%reason%", transaction.getReason());
        plugin.langs().send(sender, transactionMessage);
    }


    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return List.of();
    }
}
