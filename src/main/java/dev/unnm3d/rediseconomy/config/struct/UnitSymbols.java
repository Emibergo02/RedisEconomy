package dev.unnm3d.rediseconomy.config.struct;

import lombok.AllArgsConstructor;
import lombok.Data;
import ru.xezard.configurations.bukkit.serialization.ConfigurationSerializable;
import ru.xezard.configurations.bukkit.serialization.SerializableAs;

import java.util.HashMap;
import java.util.Map;

@AllArgsConstructor
@Data
@SerializableAs("UnitSymbols")
public class UnitSymbols implements ConfigurationSerializable {
    String thousand;
    String million;
    String billion;
    String trillion;
    String quadrillion;

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("thousand", thousand);
        map.put("million", million);
        map.put("billion", billion);
        map.put("trillion", trillion);
        map.put("quadrillion", quadrillion);
        return map;
    }
}