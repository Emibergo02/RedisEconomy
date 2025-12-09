package dev.unnm3d.rediseconomy.migrators;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.currency.Currency;
import io.lettuce.core.ScoredValue;
import me.yic.xconomy.data.sql.SQL;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;


public class XConomyMigrator implements Migrator {

    @Override
    public void migrate(Currency defaultCurrency) {
        RedisEconomyPlugin.getInstance().getLogger().info("§aMigrating from XConomy...");

        final List<ScoredValue<String>> balances = new ArrayList<>();
        final Map<String, String> nameUniqueIds = new HashMap<>();

        try {
            Connection connection = SQL.database.getConnectionAndCheck();
            PreparedStatement statement = connection.prepareStatement("select * from xconomy");
            ResultSet rs = statement.executeQuery();

            while (rs.next()) {
                UUID userUUID = UUID.fromString(rs.getString(1));
                String userName = rs.getString(2);
                double balance = Double.parseDouble(rs.getString(3));
                balances.add(ScoredValue.just(balance, userUUID.toString()));
                if (userName != null && !userName.isEmpty()) {
                    nameUniqueIds.put(userName, userUUID.toString());
                }
                defaultCurrency.updateAccountLocal(userUUID, userName == null ? userUUID.toString() : userName, balance);
            }

            rs.close();
            statement.close();
            SQL.database.closeHikariConnection(connection);
        } catch (SQLException e) {
            e.printStackTrace();
        }


        defaultCurrency.getEconomyStorage().updateBulkAccounts(defaultCurrency.getCurrencyName(), balances, nameUniqueIds);
    }
}
