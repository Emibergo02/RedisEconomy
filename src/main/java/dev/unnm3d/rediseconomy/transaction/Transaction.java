package dev.unnm3d.rediseconomy.transaction;

import dev.unnm3d.rediseconomy.redis.RedisKeys;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
public class Transaction {
    private AccountID accountIdentifier = new AccountID(RedisKeys.getServerUUID());
    private long timestamp = 0;
    private AccountID receiver = new AccountID(RedisKeys.getServerUUID());
    @Setter
    private double amount = 0;
    private String currencyName = "";
    @Setter
    private String reason = "Unknown";
    @Setter
    private String revertedWith = null;

    /**
     * Creates a new transaction from a string
     *
     * @param serializedTransaction The serialized transaction
     * @return The transaction created from the string
     */
    public static Transaction fromString(String serializedTransaction) {
        String[] parts = serializedTransaction.split(";");
        return new Transaction(
                parts[0].length() == 36 ? new AccountID(UUID.fromString(parts[0])) : new AccountID(parts[0]),
                Long.parseLong(parts[1]),
                parts[2].length() == 36 ? new AccountID(UUID.fromString(parts[2])) : new AccountID(parts[2]),
                Double.parseDouble(parts[3]),
                parts[4],
                parts[5],
                parts.length == 7 ? parts[6] : null);
    }

    @Override
    public String toString() {
        return accountIdentifier + ";" + timestamp + ";" + receiver + ";" + amount + ";" + currencyName + ";" + reason + (revertedWith == null ? "" : ";" + revertedWith);
    }
}
