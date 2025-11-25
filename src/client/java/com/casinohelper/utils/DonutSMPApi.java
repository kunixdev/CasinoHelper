package com.casinohelper.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.casinohelper.config.CasinoConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class DonutSMPApi {
    private static final String API_URL = "https://api.donutsmp.net/v1/stats/";
    private static final HttpClient client = HttpClient.newHttpClient();

    public static CompletableFuture<Stats> getPlayerStats(String username) {
        String apiKey = CasinoConfig.donutApiKey;
        if (apiKey == null || apiKey.isEmpty()) {
            return CompletableFuture.completedFuture(new Stats("No API Key", "0", "0", "0"));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + username))
                .header("accept", "application/json")
                .header("Authorization", apiKey)
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                            JsonObject result = json.getAsJsonObject("result");

                            return new Stats(
                                    result.get("money").getAsString(),
                                    result.get("shards").getAsString(),
                                    result.get("kills").getAsString(),
                                    result.get("deaths").getAsString());
                        } catch (Exception e) {
                            e.printStackTrace();
                            return new Stats("Error", "0", "0", "0");
                        }
                    }
                    return new Stats("N/A", "0", "0", "0");
                });
    }

    public static class Stats {
        public final String money;
        public final String shards;
        public final String kills;
        public final String deaths;

        public Stats(String money, String shards, String kills, String deaths) {
            this.money = money;
            this.shards = shards;
            this.kills = kills;
            this.deaths = deaths;
        }
    }
}
