package com.casinohelper.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.casinohelper.config.CasinoConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * DonutSMP API client that fetches player stats through the dashboard.
 * The dashboard handles API key management and rotation to avoid rate limiting.
 */
public class DonutSMPApi {
    private static final String BASE_URL = System.getenv().getOrDefault("CASINO_BASE_URL",
            "https://casinohelper.online/api");
    private static final HttpClient client = HttpClient.newHttpClient();

    public static CompletableFuture<Stats> getPlayerStats(String username) {
        String apiKey = CasinoConfig.dashboardApiKey;
        if (apiKey == null || apiKey.isEmpty()) {
            return CompletableFuture.completedFuture(new Stats("No Dashboard Key", "0", "0", "0"));
        }

        // Calculate HMAC signature for authentication
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        String nonce = java.util.UUID.randomUUID().toString();
        String player = net.minecraft.client.MinecraftClient.getInstance().getSession().getUsername();
        
        // For GET requests, we sign the endpoint + timestamp + nonce
        String payload = "/api/donut/stats/" + username + "." + timestamp + "." + nonce;
        String signature = calculateHmac(payload, apiKey);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/donut/stats/" + username))
                .header("accept", "application/json")
                .header("X-Signature", signature)
                .header("X-Player", player)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                            
                            if (json.has("success") && json.get("success").getAsBoolean()) {
                                JsonObject result = json.getAsJsonObject("result");
                                return new Stats(
                                        result.get("money").getAsString(),
                                        result.get("shards").getAsString(),
                                        result.get("kills").getAsString(),
                                        result.get("deaths").getAsString());
                            } else {
                                // Dashboard returned error but with fallback data
                                if (json.has("result")) {
                                    JsonObject result = json.getAsJsonObject("result");
                                    return new Stats(
                                            result.get("money").getAsString(),
                                            result.get("shards").getAsString(),
                                            result.get("kills").getAsString(),
                                            result.get("deaths").getAsString());
                                }
                                return new Stats("N/A", "0", "0", "0");
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            return new Stats("Error", "0", "0", "0");
                        }
                    }
                    System.out.println("[CasinoHelper] DonutAPI proxy error: " + response.statusCode());
                    return new Stats("N/A", "0", "0", "0");
                })
                .exceptionally(e -> {
                    e.printStackTrace();
                    return new Stats("Error", "0", "0", "0");
                });
    }

    private static String calculateHmac(String data, String key) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKeySpec = new javax.crypto.spec.SecretKeySpec(key.getBytes(),
                    "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmacData = mac.doFinal(data.getBytes());
            StringBuilder result = new StringBuilder();
            for (byte b : hmacData) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
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
