package dev.unnm3d.rediseconomy.currency.migration;

import dev.unnm3d.rediseconomy.RedisEconomyPlugin;
import dev.unnm3d.rediseconomy.config.Settings;
import dev.unnm3d.rediseconomy.currency.Currency;
import dev.unnm3d.rediseconomy.currency.CurrencyMigration;
import io.lettuce.core.ScoredValue;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class SqlCurrencyMigration extends CurrencyMigration {

    private Settings.SqlMigrateSettings sql;
    private Connection connection;

    public SqlCurrencyMigration(RedisEconomyPlugin plugin, Currency currency) {
        super(plugin, currency);
    }

    @Override
    public String getProvider() {
        return "SQL DATABASE";
    }

    @Override
    protected boolean setup() {
        sql = plugin.getConfigManager().getSettings().sqlMigration;
        try {
            Class.forName(sql.driver());
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("Cannot find SQL driver class: " + sql.driver());
        }
        try {
            connection = DriverManager.getConnection(sql.url(), sql.username(), sql.password());
            return true;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, t, () -> "Cannot connect to SQL database");
            return false;
        }
    }

    @Override
    protected void start() {
        final List<ScoredValue<String>> balances = new ArrayList<>();
        final Map<String, String> nameUniqueIds = new HashMap<>();

        try (PreparedStatement count = connection.prepareStatement("SELECT COUNT(*) FROM `" + sql.table() + "`"); PreparedStatement stmt = connection.prepareStatement("SELECT ALL * FROM `" + sql.table() + "`")) {
            String total = "ALL";
            try {
                ResultSet resultSet = count.executeQuery();

                if (resultSet.next()) {
                    int rowCount = resultSet.getInt(1);
                    total = String.valueOf(rowCount);
                }
            } catch (SQLException ignored) { }

            plugin.getLogger().info("Total lines: " + total);

            final ResultSet result = stmt.executeQuery();
            int i = 0;
            while (result.next()) {
                i++;
                try {
                    final String name = result.getString(sql.nameColumn());
                    final String uuid = result.getString(sql.uuidColumn());
                    if (uuid == null) continue;
                    final double money = result.getDouble(sql.moneyColumn());
                    if (money == 0) continue;

                    balances.add(ScoredValue.just(money, uuid));
                    if (name != null)
                        nameUniqueIds.put(name, uuid);
                    updateAccountLocal(UUID.fromString(uuid), name == null ? uuid : name, money);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (i % 1000 == 0) {
                    plugin.getLogger().info("Progress: " + i + "/" + total);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        try {
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        currency.updateBulkAccountsCloudCache(balances, nameUniqueIds);
    }
}
