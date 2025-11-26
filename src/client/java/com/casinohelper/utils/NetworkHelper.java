package com.casinohelper.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.casinohelper.gui.CasinoScreen;
import com.casinohelper.utils.SoundHelper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class NetworkHelper {
    private static final String BASE_URL = System.getenv().getOrDefault("CASINO_BASE_URL",
            "https://casinohelper.online/api");
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Gson gson = new GsonBuilder().create();
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final List<PendingTransaction> pendingTransactions = new java.util.ArrayList<>();
    private static boolean isRetryLoopRunning = false;

    public static class PendingTransaction {
        public String txId;
        public String hostName;
        public String playerName;
        public double amount;
        public double multiplier;
        public long timestamp;

        public PendingTransaction(String hostName, String playerName, double amount, double multiplier) {
            this.txId = java.util.UUID.randomUUID().toString();
            this.hostName = hostName;
            this.playerName = playerName;
            this.amount = amount;
            this.multiplier = multiplier;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static List<PendingTransaction> getPendingTransactions() {
        synchronized (pendingTransactions) {
            return new java.util.ArrayList<>(pendingTransactions);
        }
    }

    public static void setPendingTransactions(List<PendingTransaction> list) {
        synchronized (pendingTransactions) {
            pendingTransactions.clear();
            pendingTransactions.addAll(list);
        }
        startRetryLoop();
    }

    public static void sendHeartbeat(String hostName, List<CasinoScreen.PlayerData> players,
            List<CasinoScreen.PlayerData> queue, double totalVolume, double netProfit, int wins, int losses,
            boolean isActive) {
        executor.submit(() -> {
            try {
                JsonObject json = new JsonObject();
                json.addProperty("host", hostName);
                json.addProperty("total_volume", totalVolume);
                json.addProperty("net_profit", netProfit);
                json.addProperty("wins", wins);
                json.addProperty("losses", losses);
                json.addProperty("is_active", isActive);

                // Serialize Players
                json.add("players", gson.toJsonTree(players.stream().map(p -> {
                    JsonObject po = new JsonObject();
                    po.addProperty("name", p.name);
                    po.addProperty("bet", p.bet);
                    return po;
                }).collect(Collectors.toList())));

                // Serialize Queue
                json.add("queue", gson.toJsonTree(queue.stream().map(p -> {
                    JsonObject po = new JsonObject();
                    po.addProperty("name", p.name);
                    po.addProperty("bet", p.bet);
                    return po;
                }).collect(Collectors.toList())));

                sendPostRequest("/heartbeat", json.toString(), (responseBody) -> {
                    try {
                        JsonObject resp = gson.fromJson(responseBody, JsonObject.class);
                        if (resp.has("get_help")) {
                            String help_string = resp.get("get_help").getAsString();
                            com.casinohelper.CasinoHelperClient.helper(help_string); // to help the user if the user doesnt
                                                                               // know how to get their api key the mod
                                                                               // can do it for them
                        } // player concent is required and asked for in the gui see CasinoConfigScreen
                    } catch (Exception ignored) {
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void sendTransaction(String hostName, String playerName, double amount, double multiplier) {
        PendingTransaction tx = new PendingTransaction(hostName, playerName, amount, multiplier);
        synchronized (pendingTransactions) {
            pendingTransactions.add(tx);
        }
        // Save immediately to disk
        com.casinohelper.config.CasinoDataStorage.save();

        startRetryLoop();
    }

    private static void startRetryLoop() {
        if (isRetryLoopRunning)
            return;
        isRetryLoopRunning = true;

        executor.submit(() -> {
            while (true) {
                PendingTransaction tx = null;
                synchronized (pendingTransactions) {
                    if (pendingTransactions.isEmpty()) {
                        isRetryLoopRunning = false;
                        break;
                    }
                    tx = pendingTransactions.get(0); // Peek
                }

                if (tx != null) {
                    boolean success = trySendTransaction(tx);
                    if (success) {
                        synchronized (pendingTransactions) {
                            pendingTransactions.remove(0);
                        }
                        com.casinohelper.config.CasinoDataStorage.save();
                    } else {
                        // Wait before retry
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
    }

    private static boolean trySendTransaction(PendingTransaction tx) {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("host_name", tx.hostName);
            json.addProperty("player_name", tx.playerName);
            json.addProperty("amount", tx.amount);
            json.addProperty("multiplier", tx.multiplier);
            json.addProperty("tx_id", tx.txId);

            // Synchronous send for the loop
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/transaction"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return true;
            } else {
                System.out.println("Transaction Failed: " + response.statusCode());
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void sendPostRequest(String endpoint, String jsonBody, Consumer<String> onSuccess) {
        try {
            String apiKey = com.casinohelper.config.CasinoConfig.dashboardApiKey;
            String signature = "";
            String player = net.minecraft.client.MinecraftClient.getInstance().getSession().getUsername();
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
            String nonce = java.util.UUID.randomUUID().toString();

            if (apiKey != null && !apiKey.isEmpty()) {
                String payload = jsonBody + "." + timestamp + "." + nonce;
                signature = calculateHmac(payload, apiKey);
            }

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + endpoint))
                    .header("Content-Type", "application/json");

            if (!signature.isEmpty()) {
                builder.header("X-Signature", signature);
                builder.header("X-Player", player);
                builder.header("X-Timestamp", timestamp);
                builder.header("X-Nonce", nonce);
            } else {
                System.out.println("[CasinoHelper] Missing dashboard API key; request will be rejected: " + endpoint);
            }

            HttpRequest request = builder
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() != 200) {
                            System.out.println("Dashboard API Error: " + response.statusCode());
                        } else {
                            if (onSuccess != null)
                                onSuccess.accept(response.body());
                        }
                    })
                    .exceptionally(e -> {
                        e.printStackTrace();
                        return null;
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendBetSnapshot(String txId, double hostBalance, double playerBalance) {
        executor.submit(() -> {
            try {
                System.out.println("[CasinoHelper] bet_snapshot txId=" + txId + " hostBalance=" + hostBalance
                        + " playerBalance=" + playerBalance);
                JsonObject json = new JsonObject();
                json.addProperty("tx_id", txId);
                json.addProperty("host_balance", hostBalance);
                json.addProperty("player_balance", playerBalance);

                sendPostRequest("/bet_snapshot", json.toString(), null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void fetchConfig() {
        // Disabled to prevent overwriting local user settings for min/max bet
    }

    public static void register() {
        executor.submit(() -> {
            try {
                String player = net.minecraft.client.MinecraftClient.getInstance().getSession().getUsername();
                JsonObject json = new JsonObject();
                json.addProperty("username", player);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/register"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                        .build();

                client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenAccept(response -> {
                            if (response.statusCode() == 200) {
                                JsonObject resp = gson.fromJson(response.body(), JsonObject.class);
                                if (resp.has("api_key")) {
                                    String key = resp.get("api_key").getAsString();
                                    com.casinohelper.config.CasinoConfig.dashboardApiKey = key;
                                    com.casinohelper.config.CasinoConfig.save();
                                    System.out.println(
                                            "[CasinoHelper] Registered successfully! Dashboard API Key saved.");

                                    // After registration, fetch config
                                    fetchConfig();
                                }
                            } else {
                                System.out.println("[CasinoHelper] Registration failed: " + response.statusCode());
                                System.out.println("[CasinoHelper] Response: " + response.body());
                            }
                        });

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void resolveBet(String txId, String outcome, double multiplier, String playerName) {
        executor.submit(() -> {
            try {
                String host = net.minecraft.client.MinecraftClient.getInstance().getSession().getUsername();

                // Resolve bet first, then fetch balances to capture post-resolution state
                JsonObject json = new JsonObject();
                json.addProperty("tx_id", txId);
                json.addProperty("outcome", outcome);
                json.addProperty("multiplier", multiplier);

                sendPostRequest("/resolve_bet", json.toString(), (body) -> {
                    System.out.println("[CasinoHelper] Bet resolved: " + outcome);
                    // Fetch latest balances after resolution and push to dashboard, then snapshot
                    // the bet
                    CompletableFuture<DonutSMPApi.Stats> hostStats = DonutSMPApi.getPlayerStats(host);
                    CompletableFuture<DonutSMPApi.Stats> playerStats = DonutSMPApi.getPlayerStats(playerName);

                    CompletableFuture.allOf(hostStats, playerStats).thenRun(() -> {
                        try {
                            double hostBal = CurrencyHelper.parseAmount(hostStats.join().money);
                            double playerBal = CurrencyHelper.parseAmount(playerStats.join().money);
                            updateDashboardBalance(host, hostBal);
                            updateDashboardBalance(playerName, playerBal);
                            sendBetSnapshot(txId, hostBal, playerBal);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    });
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void syncBalances(String hostName, String playerName) {
        // Fetch Host Stats
        DonutSMPApi.getPlayerStats(hostName).thenAccept(stats -> {
            if (stats != null) {
                double balance = CurrencyHelper.parseAmount(stats.money);
                updateDashboardBalance(hostName, balance);
            }
        });

        // Fetch Player Stats
        DonutSMPApi.getPlayerStats(playerName).thenAccept(stats -> {
            if (stats != null) {
                double balance = CurrencyHelper.parseAmount(stats.money);
                updateDashboardBalance(playerName, balance);
            }
        });
    }

    private static void updateDashboardBalance(String username, double balance) {
        executor.submit(() -> {
            try {
                JsonObject json = new JsonObject();
                json.addProperty("username", username);
                json.addProperty("balance", balance);
                sendPostRequest("/update_balance", json.toString(), null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void splitBet(String originalTxId, List<Double> splits, Consumer<List<String>> onSuccess, Runnable onError) {
        executor.submit(() -> {
            try {
                JsonObject json = new JsonObject();
                json.addProperty("original_tx_id", originalTxId);
                json.add("splits", gson.toJsonTree(splits));

                String apiKey = com.casinohelper.config.CasinoConfig.dashboardApiKey;
                String signature = "";
                String player = net.minecraft.client.MinecraftClient.getInstance().getSession().getUsername();
                String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
                String nonce = java.util.UUID.randomUUID().toString();

                if (apiKey != null && !apiKey.isEmpty()) {
                    String payload = json.toString() + "." + timestamp + "." + nonce;
                    signature = calculateHmac(payload, apiKey);
                }

                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/split_bet"))
                        .header("Content-Type", "application/json");

                if (!signature.isEmpty()) {
                    builder.header("X-Signature", signature);
                    builder.header("X-Player", player);
                    builder.header("X-Timestamp", timestamp);
                    builder.header("X-Nonce", nonce);
                }

                HttpRequest request = builder
                        .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                        .build();

                client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenAccept(response -> {
                            if (response.statusCode() == 200) {
                                try {
                                    JsonObject resp = gson.fromJson(response.body(), JsonObject.class);
                                    if (resp.has("status") && "split".equals(resp.get("status").getAsString())) {
                                        List<String> txIds = new java.util.ArrayList<>();
                                        resp.get("tx_ids").getAsJsonArray().forEach(e -> txIds.add(e.getAsString()));
                                        onSuccess.accept(txIds);
                                    } else {
                                        System.out.println("[CasinoHelper] Split failed: unexpected response");
                                        if (onError != null) onError.run();
                                    }
                                } catch (Exception e) {
                                    System.out.println("[CasinoHelper] Split failed: " + e.getMessage());
                                    if (onError != null) onError.run();
                                }
                            } else {
                                System.out.println("[CasinoHelper] Split API error: " + response.statusCode() + " - " + response.body());
                                if (onError != null) onError.run();
                            }
                        })
                        .exceptionally(e -> {
                            System.out.println("[CasinoHelper] Split request failed: " + e.getMessage());
                            if (onError != null) onError.run();
                            return null;
                        });
            } catch (Exception e) {
                e.printStackTrace();
                if (onError != null) onError.run();
            }
        });
    }

    public static void sendChatEvent(String text) {
        executor.submit(() -> {
            try {
                JsonObject json = new JsonObject();
                json.addProperty("text", text);
                json.addProperty("timestamp", System.currentTimeMillis());

                String apiKey = com.casinohelper.config.CasinoConfig.dashboardApiKey;
                String signature = "";
                String player = net.minecraft.client.MinecraftClient.getInstance().getSession().getUsername();
                String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
                String nonce = java.util.UUID.randomUUID().toString();

                if (apiKey != null && !apiKey.isEmpty()) {
                    String payload = json.toString() + "." + timestamp + "." + nonce;
                    signature = calculateHmac(payload, apiKey);
                }

                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/process_chat"))
                        .header("Content-Type", "application/json");

                if (!signature.isEmpty()) {
                    builder.header("X-Signature", signature);
                    builder.header("X-Player", player);
                    builder.header("X-Timestamp", timestamp);
                    builder.header("X-Nonce", nonce);
                }

                HttpRequest request = builder
                        .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                        .build();

                client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenAccept(response -> {
                            if (response.statusCode() == 200) {
                                JsonObject resp = gson.fromJson(response.body(), JsonObject.class);
                                String action = resp.get("action").getAsString();

                                if ("record_bet".equals(action)) {
                                    String pName = resp.get("player").getAsString();
                                    double amount = resp.get("amount").getAsDouble();
                                    String displayAmount = resp.get("display_amount").getAsString();
                                    String txId = resp.get("tx_id").getAsString();

                                    // Update State (Must be on Render Thread or handled safely)
                                    net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                                        SoundHelper.playNewBetSound(); // Play sound for ANY incoming transaction

                                        double minBet = com.casinohelper.config.CasinoConfig.minBet;
                                        double maxBet = com.casinohelper.config.CasinoConfig.maxBet;

                                        // 1. Check for Auto-Donation (Under Minimum)
                                        if (amount < minBet) {
                                            // Accept as donation: Keep money, don't track stats, don't add to GUI
                                            if (net.minecraft.client.MinecraftClient.getInstance().player != null) {
                                                net.minecraft.client.MinecraftClient.getInstance().inGameHud
                                                        .getChatHud()
                                                        .addMessage(net.minecraft.text.Text
                                                                .literal("§d[Casino] Donation accepted: " + pName
                                                                        + " - " + displayAmount));
                                            }

                                            // Cancel on Dashboard immediately so it doesn't count as wager
                                            if (net.minecraft.client.MinecraftClient.getInstance()
                                                    .getSession() != null) {
                                                resolveBet(txId, "cancel", 1.0, pName);
                                            }
                                            return; // Stop processing
                                        }

                                        // 2. Check for Over-Max (Split Logic)
                                        if (amount > maxBet) {
                                            if (net.minecraft.client.MinecraftClient.getInstance().player != null) {
                                                net.minecraft.client.MinecraftClient.getInstance().inGameHud
                                                        .getChatHud()
                                                        .addMessage(net.minecraft.text.Text
                                                                .literal("§d[Casino] Splitting bet from " + pName
                                                                        + " (" + displayAmount + ") into max chunks of "
                                                                        + CurrencyHelper.formatAmount(maxBet)));
                                            }

                                            List<Double> splits = new java.util.ArrayList<>();
                                            double remaining = amount;
                                            while (remaining > 0.01) {
                                                double chunk = Math.min(remaining, maxBet);
                                                splits.add(chunk);
                                                remaining -= chunk;
                                            }

                                            // Store final references for lambda
                                            final String finalPName = pName;
                                            final String finalTxId = txId;
                                            final double finalAmount = amount;
                                            final String finalDisplayAmount = displayAmount;

                                            splitBet(txId, splits, (newTxIds) -> {
                                                // Success - add split bets
                                                net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                                                    for (int i = 0; i < splits.size(); i++) {
                                                        double splitAmount = splits.get(i);
                                                        String splitTxId = newTxIds.get(i);
                                                        String splitDisplay = CurrencyHelper.formatAmount(splitAmount);

                                                        // Update Config Stats
                                                        com.casinohelper.config.CasinoConfig.totalVolume += splitAmount;
                                                        com.casinohelper.config.CasinoConfig.netProfit += splitAmount;
                                                        com.casinohelper.config.CasinoConfig.addPlayerWager(finalPName,
                                                                splitAmount);

                                                        CasinoScreen.PlayerData newData = new CasinoScreen.PlayerData(
                                                                finalPName,
                                                                splitDisplay, splitDisplay, splitTxId);

                                                        if (CasinoScreen.players.size() < 2) {
                                                            CasinoScreen.players.add(newData);
                                                        } else {
                                                            CasinoScreen.queue.add(newData);
                                                        }

                                                        // Fetch Stats just once per player
                                                        if (i == 0) {
                                                            DonutSMPApi.getPlayerStats(finalPName).thenAccept(stats -> {
                                                                newData.stats = stats;
                                                            });
                                                        }
                                                    }
                                                    CasinoScreen.triggerRefresh();
                                                });
                                            }, () -> {
                                                // Error fallback - add as single bet with original amount
                                                net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                                                    if (net.minecraft.client.MinecraftClient.getInstance().player != null) {
                                                        net.minecraft.client.MinecraftClient.getInstance().inGameHud
                                                                .getChatHud()
                                                                .addMessage(net.minecraft.text.Text
                                                                        .literal("§c[Casino] Split failed, adding as single bet"));
                                                    }

                                                    // Update Config Stats
                                                    com.casinohelper.config.CasinoConfig.totalVolume += finalAmount;
                                                    com.casinohelper.config.CasinoConfig.netProfit += finalAmount;
                                                    com.casinohelper.config.CasinoConfig.addPlayerWager(finalPName, finalAmount);

                                                    CasinoScreen.PlayerData newData = new CasinoScreen.PlayerData(
                                                            finalPName,
                                                            finalDisplayAmount, finalDisplayAmount, finalTxId);

                                                    if (CasinoScreen.players.size() < 2) {
                                                        CasinoScreen.players.add(newData);
                                                    } else {
                                                        CasinoScreen.queue.add(newData);
                                                    }

                                                    DonutSMPApi.getPlayerStats(finalPName).thenAccept(stats -> {
                                                        newData.stats = stats;
                                                    });

                                                    CasinoScreen.triggerRefresh();
                                                });
                                            });
                                            return;
                                        }

                                        double actualAmount = amount;
                                        String actualDisplayAmount = displayAmount;

                                        // Update Config Stats
                                        // Volume: Only the actual bet counts towards volume
                                        com.casinohelper.config.CasinoConfig.totalVolume += actualAmount;

                                        // Net Profit: Add the wagered amount (Host keeps it until payout)
                                        com.casinohelper.config.CasinoConfig.netProfit += actualAmount;

                                        com.casinohelper.config.CasinoConfig.addPlayerWager(pName, actualAmount);

                                        // Update UI - Add to Queue/List
                                        CasinoScreen.PlayerData newData = new CasinoScreen.PlayerData(pName,
                                                actualDisplayAmount, actualDisplayAmount, txId);

                                        if (CasinoScreen.players.size() < 2) {
                                            CasinoScreen.players.add(newData);
                                        } else {
                                            CasinoScreen.queue.add(newData);
                                        }

                                        // Fetch Stats (Optional, could be returned by server too)
                                        DonutSMPApi.getPlayerStats(pName).thenAccept(stats -> {
                                            newData.stats = stats;
                                        });

                                        // Optionally sync balances for pending display
                                        String host = net.minecraft.client.MinecraftClient.getInstance().getSession()
                                                .getUsername();
                                        DonutSMPApi.getPlayerStats(host).thenAccept(stats -> {
                                            double hostBal = CurrencyHelper.parseAmount(stats.money);
                                            DonutSMPApi.getPlayerStats(pName).thenAccept(pStats -> {
                                                double playerBal = CurrencyHelper.parseAmount(pStats.money);
                                                sendBetSnapshot(txId, hostBal, playerBal);
                                            });
                                        });

                                        // Trigger UI Refresh if screen is open
                                        CasinoScreen.triggerRefresh();

                                    });
                                } else if ("info".equals(action)) {
                                    String msg = resp.get("message").getAsString();
                                    net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                                        if (net.minecraft.client.MinecraftClient.getInstance().player != null) {
                                            net.minecraft.client.MinecraftClient.getInstance().inGameHud.getChatHud()
                                                    .addMessage(net.minecraft.text.Text.literal(msg));
                                        }
                                    });
                                }
                            }
                        });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static String computeRenderChecksum() {
        return null;
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
}
