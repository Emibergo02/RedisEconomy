# RedisEconomy
Economy plugin made with redis

### Maven
Add this repository to your `pom.xml`:
```xml
<repository>
  <id>jitpack.io</id>
  <url>https://jitpack.io</url>
</repository>  
```

Add the dependency and replace `<version>...</version>` with the latest release version:
```xml
<dependency>
  <groupId>com.github.Emibergo02</groupId>
  <artifactId>RedisEconomy</artifactId>
  <version>4.5.0</version>
  <scope>provided</scope>
</dependency>
```

### Gradle
Add it in your root `build.gradle` at the end of repositories:
```gradle
allprojects {
  repositories {
    ...
    maven { url 'https://jitpack.io' }
  }
}
```

Add the dependency and replace `master-SNAPSHOT` with the latest release version:
```gradle
dependencies {
  compileOnly 'com.github.Emibergo02:RedisEconomy:4.5.0'
}
```
## API usage
Use the Vault API only for the Vault-linked main currency, and for other currencies use RedisEconomy’s API directly
```java
// Access Point
RedisEconomyAPI api = RedisEconomyAPI.getAPI();
if(api==null){
    Bukkit.getLogger().info("RedisEconomyAPI not found!");
}

//get a Currency
Currency currency = api.getCurrencyByName("vault"); //Same as api.getDefaultCurrency()
api.getCurrencyBySymbol("€");//Gets the currency by symbol

//Currency is a Vault Economy https://github.com/MilkBowl/VaultAPI/blob/master/src/main/java/net/milkbowl/vault/economy/Economy.java, 
//same methods and everything
currency.getBalance(offlinePlayer);
currency.withdrawPlayer(offlinePlayer, 100, "Reason of withdrawal");

//Modify a player balance (default currency)
api.getDefaultCurrency().setPlayerBalance(player.getUniqueId(), 1000);

//Get all accounts from currency cache
api.getDefaultCurrency().getAccounts().forEach((uuid, account) -> {
    Bukkit.getLogger().info("Account: "+uuid+", Balance: "+account);
});

//Direct data from redis. (Not recommended)
api.getDefaultCurrency().getOrderedAccounts().thenAccept(accounts -> {
    accounts.forEach(account -> {
        Bukkit.getLogger().info("UUID: "+account.getElement()+", Balance: "+account.getScore());
    });
});
api.getDefaultCurrency().getAccountRedis(uuid).thenAccept(account -> {
    Bukkit.getLogger().info("Balance: "+ account);
});
```
