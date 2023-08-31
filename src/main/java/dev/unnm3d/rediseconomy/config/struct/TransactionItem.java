package dev.unnm3d.rediseconomy.config.struct;

import lombok.AllArgsConstructor;
import lombok.Data;
import ru.xezard.configurations.bukkit.serialization.ConfigurationSerializable;
import ru.xezard.configurations.bukkit.serialization.SerializableAs;

import java.util.HashMap;
import java.util.Map;

@AllArgsConstructor
@Data
@SerializableAs("TransactionItem")
public class TransactionItem implements ConfigurationSerializable {
    String outgoingFunds;
    String incomingFunds;

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("outgoingFunds", outgoingFunds);
        map.put("incomingFunds", incomingFunds);
        return map;
    }
}
