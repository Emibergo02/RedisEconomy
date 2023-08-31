package dev.unnm3d.rediseconomy.config.struct;

import lombok.AllArgsConstructor;
import lombok.Data;
import ru.xezard.configurations.bukkit.serialization.ConfigurationSerializable;
import ru.xezard.configurations.bukkit.serialization.SerializableAs;

import java.util.HashMap;
import java.util.Map;

@AllArgsConstructor
@Data
@SerializableAs("RedisSettings")
public class RedisSettings implements ConfigurationSerializable {
    String host;
    int port;
    String user;
    String password;
    int database;
    int timeout;
    String clientName;

    @Override
    public Map<String, Object> serialize() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("host", host);
        map.put("port", port);
        map.put("user", user);
        map.put("password", password);
        map.put("database", database);
        map.put("timeout", timeout);
        map.put("clientName", clientName);
        return map;
    }

    public static RedisSettings deserialize(Map<String, Object> s)
    {
        return new RedisSettings((String) s.get("host"), (int) s.get("port"), (String) s.get("user"), (String) s.get("password"), (int) s.get("database"), (int) s.get("timeout"), (String) s.get("clientName"));
    }
}
