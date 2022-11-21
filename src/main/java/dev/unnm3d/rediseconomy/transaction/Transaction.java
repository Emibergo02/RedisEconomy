package dev.unnm3d.rediseconomy.transaction;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
public class Transaction {
    public UUID sender = UUID.fromString("00000000-0000-0000-0000-000000000000");
    public long timestamp = 0;
    public UUID receiver = UUID.fromString("00000000-0000-0000-0000-000000000000");
    public double amount = 0;
    public String currencyName = "";
    public String reason = "Unknown";

}
