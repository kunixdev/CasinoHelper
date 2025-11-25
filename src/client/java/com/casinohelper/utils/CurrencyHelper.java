package com.casinohelper.utils;
import java.util.Locale;

public class CurrencyHelper {
    
    public static double parseAmount(String amountStr) {
        if (amountStr == null || amountStr.isEmpty()) return 0;
        
        String raw = amountStr.toUpperCase().replace("$", "").replace(",", "");
        double multiplier = 1;
        
        if (raw.endsWith("B")) {
            multiplier = 1_000_000_000;
            raw = raw.substring(0, raw.length() - 1);
        } else if (raw.endsWith("M")) {
            multiplier = 1_000_000;
            raw = raw.substring(0, raw.length() - 1);
        } else if (raw.endsWith("K")) {
            multiplier = 1_000;
            raw = raw.substring(0, raw.length() - 1);
        }
        
        try {
            return Double.parseDouble(raw) * multiplier;
        } catch (NumberFormatException e) {
            System.err.println("Failed to parse currency: " + amountStr);
            return 0;
        }
    }
    
    public static String formatAmount(double amount) {
        double abs = Math.abs(amount);
        String sign = amount < 0 ? "-" : "";
        
        if (abs >= 1_000_000_000) return sign + String.format(Locale.US, "%.1fB", abs / 1_000_000_000).replace(".0B", "B");
        if (abs >= 1_000_000) return sign + String.format(Locale.US, "%.1fM", abs / 1_000_000).replace(".0M", "M");
        if (abs >= 1_000) return sign + String.format(Locale.US, "%.1fK", abs / 1_000).replace(".0K", "K");
        
        // For small amounts, show integers if whole, else 1 decimal
        if (amount % 1 == 0) return sign + String.format(Locale.US, "%.0f", abs);
        return sign + String.format(Locale.US, "%.1f", abs);
    }
}

