package dev.unnm3d.rediseconomy.transaction;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
public class Transaction {
    public AccountID accountIdentifier = new AccountID(Transaction.getServerUUID());
    public long timestamp = 0;
    public AccountID receiver = new AccountID(Transaction.getServerUUID());
    public double amount = 0;
    public String currencyName = "";
    public String reason = "Unknown";
    public String revertedWith = null;

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

    /**
     * Returns the server UUID
     *
     * @return "00000000-0000-0000-0000-000000000000" as UUID
     */
    public static UUID getServerUUID() {
        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }

    @Override
    public String toString() {
        return accountIdentifier + ";" + timestamp + ";" + receiver + ";" + amount + ";" + currencyName + ";" + reason + (revertedWith == null ? "" : ";" + revertedWith);
    }
}
