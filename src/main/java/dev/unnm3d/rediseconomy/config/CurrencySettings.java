package dev.unnm3d.rediseconomy.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Configuration
@Getter
public final class CurrencySettings {
    public String currencyName;
    public String currencySingle;
    public String currencyPlural;
    public String decimalFormat;
    public String languageTag;
    public double startingBalance;
    public double maxBalance;
    public double payTax;
    public boolean saveTransactions;
    public int transactionsTTL;
    public boolean bankEnabled;
    public boolean taxOnlyPay;
    public int executorThreads;
}