package dev.unnm3d.rediseconomy.transaction;

import dev.unnm3d.rediseconomy.redis.RedisKeys;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
public class Transaction {
    /**
     * The identifier of the account this transaction belongs to
     */
    private AccountID accountIdentifier = new AccountID(RedisKeys.getServerUUID());
    /**
     * The identifier of the actor that performed this transaction
     */
    private AccountID actor = new AccountID(RedisKeys.getServerUUID());
    private String currencyName = "";
    /**
     * The timestamp of the transaction
     */
    private long timestamp = 0;
    @Setter
    private double amount = 0;
    /**
     * The identifier of the transaction that reverted this transaction, if any
     * <p>
     * This is used to track reversions of transactions, such as refunds or corrections.
     */
    @Setter
    private String revertedWith = null;
    /**
     * The reason for this transaction
     * <p>
     * This is used to track the purpose of the transaction
     */
    @Setter
    private String reason = "Unknown";

    /**
     * Creates a new transaction from a string
     *
     * @param serializedTransaction The serialized transaction
     * @return The transaction created from the string
     */
    public static Transaction fromString(String serializedTransaction) {
        final AccountID accountID = parseAccountID(serializedTransaction.substring(0, 17));
        final AccountID actorID = parseAccountID(serializedTransaction.substring(17, 34));
        final long timestamp = ByteBuffer.wrap(serializedTransaction.substring(34, 42).getBytes(StandardCharsets.ISO_8859_1)).getLong();
        final double amount = ByteBuffer.wrap(serializedTransaction.substring(42, 50).getBytes(StandardCharsets.ISO_8859_1)).getDouble();
        final String currencyName = removeTrailingNulls(serializedTransaction.substring(50, 58));//Remove padding null characters from currencyName
        final long revertedWith = ByteBuffer.wrap(serializedTransaction.substring(58, 66).getBytes(StandardCharsets.ISO_8859_1)).getLong();
        final String reason = serializedTransaction.substring(66);

        return new Transaction(accountID, actorID, currencyName, timestamp, amount, revertedWith == 0 ? null : String.valueOf(revertedWith), reason);
    }

    private static String removeTrailingNulls(String str) {
        int endIndex = str.length();
        while (endIndex > 0 && str.charAt(endIndex - 1) == '\0') {
            endIndex--;
        }
        return str.substring(0, endIndex);
    }

    private static AccountID parseAccountID(String id) {
        if (id.startsWith("§")) {
            ByteBuffer byteBuffer = ByteBuffer.wrap(id.substring(1).getBytes(StandardCharsets.ISO_8859_1));
            long high = byteBuffer.getLong();
            long low = byteBuffer.getLong();
            return new AccountID(new UUID(high, low));
        } else if (id.startsWith("^")) {
            return new AccountID(removeTrailingNulls(id.substring(1)));
        }
        throw new IllegalArgumentException("Invalid account identifier format: " + id);
    }

    @Override
    public String toString() {
        ByteBuffer buf = ByteBuffer.allocate(66 + reason.length());

        writeIdentifier(buf, accountIdentifier);
        writeIdentifier(buf, actor);

        buf.putLong(timestamp);
        buf.putDouble(amount);
        buf.put(Arrays.copyOf(currencyName.getBytes(StandardCharsets.ISO_8859_1),8)); // Ensure currencyName is exactly 8 bytes long, padding with zeros if necessary
        if (revertedWith == null) {
            buf.putLong(0);
        } else {
            buf.putLong(Long.parseLong(revertedWith));
        }
        buf.put(reason.getBytes(StandardCharsets.ISO_8859_1));

        return new String(buf.array(), StandardCharsets.ISO_8859_1);
    }

    private void writeIdentifier(ByteBuffer buf, AccountID accountID) {
        if (accountID.isPlayer()) {
            buf.put((byte) '§');
            buf.putLong(accountID.getUUID().getMostSignificantBits());
            buf.putLong(accountID.getUUID().getLeastSignificantBits());
        } else {
            buf.put((byte) '^');
            buf.put(Arrays.copyOf(accountID.toString().getBytes(StandardCharsets.ISO_8859_1),16)); // Ensure non-player account ID is exactly 16 bytes long, padding with zeros if necessary
        }
    }
}
