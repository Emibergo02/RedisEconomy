package dev.unnm3d.rediseconomy.config.struct;

import lombok.AllArgsConstructor;
import lombok.Data;
import ru.xezard.configurations.bukkit.serialization.ConfigurationSerializable;
import ru.xezard.configurations.bukkit.serialization.SerializableAs;

import java.util.HashMap;
import java.util.Map;

@AllArgsConstructor
@Data
@SerializableAs("CurrencySettings")
public class CurrencySettings implements ConfigurationSerializable {
    String currencyName, currencySingle, currencyPlural, decimalFormat, languageTag;
    double startingBalance, payTax;
    boolean bankEnabled;

    @Override
    public Map<String, Object> serialize() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("currencyName", currencyName);
        map.put("currencySingle", currencySingle);
        map.put("currencyPlural", currencyPlural);
        map.put("decimalFormat", decimalFormat);
        map.put("languageTag", languageTag);
        map.put("startingBalance", startingBalance);
        map.put("payTax", payTax);
        map.put("bankEnabled", bankEnabled);
        return map;
    }

    public static CurrencySettings deserialize(Map<String, Object> s)
    {
        return new CurrencySettings((String) s.get("currencyName"), (String) s.get("currencySingle"), (String) s.get("currencyPlural"), (String) s.get("decimalFormat"), (String) s.get("languageTag"), (double) s.get("startingBalance"), (double) s.get("payTax"), (boolean) s.get("bankEnabled"));
    }
}
