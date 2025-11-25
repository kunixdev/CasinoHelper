package com.casinohelper.config;

import com.casinohelper.utils.CurrencyHelper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import com.casinohelper.utils.NetworkHelper;

/**
 * Simple JSON persistence for Casino Helper stats & layout.
 */
public class CasinoDataStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "casinohelper_stats.json";

    private static Path getFile() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.runDirectory.toPath().resolve(FILE_NAME);
    }

    public static void load() {
        try {
            Path file = getFile();
            if (!Files.exists(file)) {
                return;
            }

            try (Reader reader = Files.newBufferedReader(file)) {
                Data data = GSON.fromJson(reader, Data.class);
                if (data == null)
                    return;

                CasinoConfig.x = data.x;
                CasinoConfig.y = data.y;
                CasinoConfig.scale = data.scale;

                if (data.minBet > 0)
                    CasinoConfig.minBet = data.minBet;
                if (data.maxBet > 0)
                    CasinoConfig.maxBet = data.maxBet;

                CasinoConfig.totalVolume = data.totalVolume;
                CasinoConfig.netProfit = data.netProfit;
                CasinoConfig.wins = data.wins;
                CasinoConfig.losses = data.losses;

                CasinoConfig.playerTotalWagered.clear();
                if (data.playerTotals != null) {
                    CasinoConfig.playerTotalWagered.putAll(data.playerTotals);
                }

                CasinoConfig.playerNetProfit.clear();
                if (data.playerProfits != null) {
                    CasinoConfig.playerNetProfit.putAll(data.playerProfits);
                }

                if (data.pendingTransactions != null) {
                    NetworkHelper.setPendingTransactions(data.pendingTransactions);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            Path file = getFile();
            Files.createDirectories(file.getParent());

            Data data = new Data();
            data.x = CasinoConfig.x;
            data.y = CasinoConfig.y;
            data.scale = CasinoConfig.scale;
            data.minBet = CasinoConfig.minBet;
            data.maxBet = CasinoConfig.maxBet;
            data.totalVolume = CasinoConfig.totalVolume;
            data.netProfit = CasinoConfig.netProfit;
            data.wins = CasinoConfig.wins;
            data.losses = CasinoConfig.losses;
            data.playerTotals = new HashMap<>(CasinoConfig.playerTotalWagered);
            data.playerProfits = new HashMap<>(CasinoConfig.playerNetProfit);
            data.pendingTransactions = new ArrayList<>(NetworkHelper.getPendingTransactions());

            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class Data {
        int x = 10;
        int y = 10;
        float scale = 1.0f;
        double minBet = 0;
        double maxBet = 0;
        double totalVolume = 0;
        double netProfit = 0;
        int wins = 0;
        int losses = 0;
        Map<String, Double> playerTotals = new HashMap<>();
        Map<String, Double> playerProfits = new HashMap<>();
        List<NetworkHelper.PendingTransaction> pendingTransactions = new ArrayList<>();
    }
}
