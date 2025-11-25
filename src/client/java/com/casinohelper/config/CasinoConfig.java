package com.casinohelper.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class CasinoConfig {
    public static int x = 10;
    public static int y = 10;
    public static float scale = 1.0f;
    public static boolean hudVisible = true;
    public static String donutApiKey = "";
    public static String dashboardApiKey = "";
    public static float backgroundOpacity = 0.67f; // 0 (transparent) to 1 (opaque)
    public static boolean copyCommandWithSlash = false;
    public static boolean soundEnabled = true;

    // Betting Limits
    public static double minBet = 0; // 0 Default
    public static double maxBet = 10_000_000; // 10M Default

    // Session Stats (Persistent now)
    public static double totalVolume = 0;
    public static double netProfit = 0;

    public static int wins = 0;
    public static int losses = 0;

    // Per-Player Stats: Name -> Total Wagered
    public static Map<String, Double> playerTotalWagered = new HashMap<>();

    // Per-Player Net Profit (Player's perspective): Name -> (Total Payouts - Total
    // Wagered)
    // Positive means they are winning against casino.
    // Per-Player Net Profit (Player's perspective): Name -> (Total Payouts - Total
    // Wagered)
    // Positive means they are winning against casino.
    public static Map<String, Double> playerNetProfit = new HashMap<>();

    // Custom Multipliers
    public static java.util.List<Integer> multipliers = new java.util.ArrayList<>(
            java.util.Arrays.asList(2, 3, 4, 6, 12));

    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("casinohelper.json")
            .toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void addPlayerWager(String playerName, double amount) {
        playerTotalWagered.put(playerName, playerTotalWagered.getOrDefault(playerName, 0.0) + amount);
        // Wager is a loss for them initially until they win
        playerNetProfit.put(playerName, playerNetProfit.getOrDefault(playerName, 0.0) - amount);
        save();
    }

    public static void addPlayerWin(String playerName, double payout) {
        // Payout is a gain for them
        playerNetProfit.put(playerName, playerNetProfit.getOrDefault(playerName, 0.0) + payout);
        save();
    }

    public static void removePlayerWager(String playerName, double amount) {
        double currentWager = playerTotalWagered.getOrDefault(playerName, 0.0);
        if (currentWager >= amount) {
            playerTotalWagered.put(playerName, currentWager - amount);
        }
        // Revert the loss (add it back to net profit since we are voiding the wager)
        playerNetProfit.put(playerName, playerNetProfit.getOrDefault(playerName, 0.0) + amount);
        save();
    }

    public static double getPlayerTotal(String playerName) {
        return playerTotalWagered.getOrDefault(playerName, 0.0);
    }

    public static double getPlayerProfit(String playerName) {
        return playerNetProfit.getOrDefault(playerName, 0.0);
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(new ConfigData(x, y, scale, hudVisible, donutApiKey, dashboardApiKey, backgroundOpacity,
                    copyCommandWithSlash, soundEnabled, minBet, maxBet, totalVolume, netProfit, wins, losses, playerTotalWagered,
                    playerNetProfit, multipliers), writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void load() {
        if (!CONFIG_FILE.exists())
            return;

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            ConfigData data = GSON.fromJson(reader, ConfigData.class);
            if (data != null) {
                x = data.x;
                y = data.y;
                scale = data.scale;
                hudVisible = data.hudVisible;
                if (data.donutApiKey != null)
                    donutApiKey = data.donutApiKey;
                // Support migration: if apiKey existed (old config), map it to donutApiKey?
                // Or just ignore. The user said "old mod has the donut smp api key".
                // If the JSON has "apiKey", GSON won't map it to "donutApiKey" automatically
                // unless we use @SerializedName or custom logic.
                // But since we control the class, we can just add a field for migration or let
                // the user re-enter it.
                // However, the user specifically mentioned needing the old key.
                // Let's add a temporary field to ConfigData to catch it.
                if (data.apiKey != null && (donutApiKey == null || donutApiKey.isEmpty()))
                    donutApiKey = data.apiKey;

                if (data.dashboardApiKey != null)
                    dashboardApiKey = data.dashboardApiKey;

                // Load min/max - always load them
                minBet = data.minBet;
                maxBet = data.maxBet;
                soundEnabled = data.soundEnabled;
                backgroundOpacity = (data.backgroundOpacity <= 0f || data.backgroundOpacity > 1f) ? backgroundOpacity
                        : data.backgroundOpacity;
                copyCommandWithSlash = data.copyCommandWithSlash;

                totalVolume = data.totalVolume;
                netProfit = data.netProfit;
                wins = data.wins;
                losses = data.losses;
                if (data.playerTotalWagered != null)
                    playerTotalWagered = data.playerTotalWagered;
                if (data.playerNetProfit != null)
                    playerNetProfit = data.playerNetProfit;
                if (data.multipliers != null && !data.multipliers.isEmpty())
                    multipliers = new java.util.ArrayList<>(data.multipliers);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Data container for GSON
    private static class ConfigData {
        int x, y;
        float scale;
        boolean hudVisible;
        String donutApiKey;
        String dashboardApiKey;
        String apiKey; // Legacy field for migration
        float backgroundOpacity;
        boolean copyCommandWithSlash;
        boolean soundEnabled;
        double minBet, maxBet;
        double totalVolume, netProfit;
        int wins, losses;
        Map<String, Double> playerTotalWagered;
        Map<String, Double> playerNetProfit;
        java.util.List<Integer> multipliers;

        public ConfigData(int x, int y, float scale, boolean hudVisible, String donutApiKey, String dashboardApiKey,
                float backgroundOpacity, boolean copyCommandWithSlash, boolean soundEnabled, double minBet, double maxBet, double totalVolume,
                double netProfit, int wins, int losses, Map<String, Double> playerTotalWagered,
                Map<String, Double> playerNetProfit, java.util.List<Integer> multipliers) {
            this.x = x;
            this.y = y;
            this.scale = scale;
            this.hudVisible = hudVisible;
            this.donutApiKey = donutApiKey;
            this.dashboardApiKey = dashboardApiKey;
            this.backgroundOpacity = backgroundOpacity;
            this.copyCommandWithSlash = copyCommandWithSlash;
            this.soundEnabled = soundEnabled;
            this.minBet = minBet;
            this.maxBet = maxBet;
            this.totalVolume = totalVolume;
            this.netProfit = netProfit;
            this.wins = wins;
            this.losses = losses;
            this.playerTotalWagered = playerTotalWagered;
            this.playerNetProfit = playerNetProfit;
            this.multipliers = multipliers;
        }
    }

    public static void resetStats() {
        totalVolume = 0;
        netProfit = 0;
        wins = 0;
        losses = 0;
        playerTotalWagered.clear();
        playerNetProfit.clear();
        save();
    }
}
