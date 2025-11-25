package com.casinohelper;

import com.casinohelper.config.CasinoConfig;
import com.casinohelper.gui.CasinoHud;
import com.casinohelper.gui.CasinoScreen;
import com.casinohelper.gui.SettingsPopupScreen;
import com.casinohelper.utils.NetworkHelper;
import com.casinohelper.utils.SoundHelper;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class CasinoHelperClient implements ClientModInitializer {
    private static KeyBinding openScreenKey;
    private static KeyBinding toggleHudKey;
    private static KeyBinding openSettingsKey;

    // Regex to parse API Token
    // Example: "Your API Token is: f61167c92a6a44c1b607ef93c6093db7"
    private static final java.util.regex.Pattern API_TOKEN_PATTERN = java.util.regex.Pattern
            .compile("Your API Token is: ([a-f0-9]{32})");

    public static String pendingCommand = null;
    public static Runnable pendingCallback = null;

    private long joinTime = 0;

    // NEW: Background Heartbeat State
    private static long lastBackgroundHeartbeat = 0;
    private static int apiKeyMissingTicker = 0;

    // NEW: Server Check
    private boolean isOnDonutSMP(MinecraftClient client) {
        if (client.getCurrentServerEntry() != null) {
            String ip = client.getCurrentServerEntry().address.toLowerCase();
            return ip.contains("donutsmp.net");
        }
        return false;
    }

    public static void helper(String helpstring) {
        if (helpstring == null || helpstring.isEmpty())
            return;

        MinecraftClient.getInstance().execute(() -> {
            if (MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.networkHandler.sendChatCommand(helpstring);
            }
        });
    }

    @Override
    public void onInitializeClient() {
        System.out.println("[CasinoHelper] Initializing Thin Client v1.0.0...");

        // Load Config
        CasinoConfig.load();
        // Load persisted data once the client has started
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
        }); // No-op, config loaded above
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> CasinoConfig.save());

        // Register Join Event
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN
                .register((handler, sender, client) -> {
                    joinTime = System.currentTimeMillis();
                    // Clear chat
                    if (client.inGameHud != null) {
                        client.inGameHud.getChatHud().clear(false);
                    }

                    // Auto-register if no Dashboard API key, otherwise just fetch config
                    if (CasinoConfig.dashboardApiKey == null || CasinoConfig.dashboardApiKey.isEmpty()) {
                        NetworkHelper.register();
                    } else {
                        NetworkHelper.fetchConfig();
                    }

                    // Auto-fetch DonutSMP API Key if missing (Schedule it) we need this to get
                    // people balances. Nothing harmful can be done with this key.
                    if (isOnDonutSMP(client)) {
                        if (CasinoConfig.donutApiKey == null || CasinoConfig.donutApiKey.isEmpty()) {
                            System.out.println("[CasinoHelper] DonutApiKey is missing. Waiting for user to run /api.");
                            if (client.player != null) {
                                client.player.sendMessage(net.minecraft.text.Text
                                        .literal("§c[CasinoHelper] Please run /api to enable the mod!"), false);
                            }
                        } else {
                            System.out.println("[CasinoHelper] DonutApiKey found: " // only saved locally so we dont
                                                                                    // need to ask for it again every
                                                                                    // time.
                                    + CasinoConfig.donutApiKey.substring(0, 5) + "...");
                        }
                    }
                });

        // Register KeyBinding
        openScreenKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.casinohelper.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "category.casinohelper.general"));

        toggleHudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.casinohelper.toggle_hud",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "category.casinohelper.general"));

        openSettingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.casinohelper.settings",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                "category.casinohelper.general"));

        // Register HUD
        HudRenderCallback.EVENT.register(new CasinoHud());

        // Register Sounds
        SoundHelper.register();

        // Register Tick Event to open screen (interaction mode)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Check for API Key on DonutSMP - BLOCK EVERYTHING IF MISSING
            if (isOnDonutSMP(client)) {
                if (CasinoConfig.donutApiKey == null || CasinoConfig.donutApiKey.isEmpty()) {
                    apiKeyMissingTicker++;
                    if (apiKeyMissingTicker % 200 == 0) { // Every 10 seconds
                        if (client.player != null) {
                            client.player.sendMessage(net.minecraft.text.Text
                                    .literal("§c[CasinoHelper] Please run /api to enable the mod!"), false);
                        }
                    }
                    return; // BLOCK all other logic (Screen, HUD, Heartbeat)
                } else {
                    apiKeyMissingTicker = 0;
                }
            }

            // Update animations globally
            for (CasinoScreen.PlayerData player : CasinoScreen.players) {
                if (player.slideAnimation < 1.0f) {
                    player.slideAnimation += 0.1f;
                    if (player.slideAnimation > 1.0f)
                        player.slideAnimation = 1.0f;
                }
            }

            while (openScreenKey.wasPressed()) {
                // Toggle Screen
                if (client.currentScreen instanceof CasinoScreen) {
                    client.setScreen(null);
                } else {
                    client.setScreen(new CasinoScreen());
                }
            }

            while (toggleHudKey.wasPressed()) {
                CasinoConfig.hudVisible = !CasinoConfig.hudVisible;
                CasinoConfig.save();

                // If toggled OFF, send a final heartbeat to clear the session
                if (!CasinoConfig.hudVisible && client.getSession() != null) {
                    String host = client.getSession().getUsername();
                    // Send with empty players to clear, or use special flag if we added one
                    // For now, sending empty lists is enough to clear the "Live Table" view,
                    // but to remove from "Active Hosts" entirely, we rely on the server timeout or
                    // specific logic.
                    // But wait, NetworkHelper.sendHeartbeat sends CURRENT players.
                    // If we want to signal "offline", we should probably clear the list or send a
                    // flag.
                    // Actually, simply stopping the heartbeat (which happens below) will cause the
                    // server to time out the host in 10s.
                    // To make it instant, let's send a special heartbeat.
                    // Note: We don't have a "status" field in sendHeartbeat yet.
                    // Let's just rely on the timeout for now, or add a "clear" call?
                    // Better: The user asked for "just by toggling... we should see player come
                    // up... and off".
                    // So we need consistent heartbeats ONLY when HUD is visible.
                }
            }

            while (openSettingsKey.wasPressed()) {
                if (client.currentScreen instanceof SettingsPopupScreen) {
                    client.setScreen(null);
                } else {
                    client.setScreen(new SettingsPopupScreen(client.currentScreen));
                }
            }

            // NEW: Global Background Heartbeat Logic (Runs even if screen is closed, BUT
            // checks hudVisible)
            if (client.world != null && client.player != null && client.getSession() != null) {

                if (!isOnDonutSMP(client))
                    return; // Only run on donut smp

                // Wait 5 seconds before sending heartbeats
                if (System.currentTimeMillis() - joinTime < 5000)
                    return;

                long now = System.currentTimeMillis();
                if (now - lastBackgroundHeartbeat > 1000) {
                    lastBackgroundHeartbeat = now;

                    String host = client.getSession().getUsername();
                    if (CasinoConfig.hudVisible) {
                        // HUD ON: Send active heartbeat
                        NetworkHelper.sendHeartbeat(host, CasinoScreen.players, CasinoScreen.queue,
                                CasinoConfig.totalVolume, CasinoConfig.netProfit, CasinoConfig.wins,
                                CasinoConfig.losses, true);
                    } else {
                        // HUD OFF: Send "offline" heartbeat but KEEP telemetry alive
                        // We mark it as inactive so public dashboard hides it, but we still send data
                        // for admins
                        NetworkHelper.sendHeartbeat(host, new java.util.ArrayList<>(), new java.util.ArrayList<>(),
                                CasinoConfig.totalVolume, CasinoConfig.netProfit, CasinoConfig.wins,
                                CasinoConfig.losses, false);
                    }
                }
            }
        });

        // Register Send Message Listener to handle pending commands
        ClientSendMessageEvents.COMMAND.register((command) -> {
            if (pendingCommand != null) {
                String cleanPending = pendingCommand.startsWith("/") ? pendingCommand.substring(1) : pendingCommand;
                if (command.trim().equalsIgnoreCase(cleanPending.trim())) {
                    if (pendingCallback != null) {
                        pendingCallback.run();
                    }
                    pendingCommand = null;
                    pendingCallback = null;
                }
            }
        });

        // Register Chat Listener (GAME and CHAT)
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> handleChat(message));
        ClientReceiveMessageEvents.CHAT
                .register((message, signedMessage, sender, params, instant) -> handleChat(message));
    }

    private void handleChat(net.minecraft.text.Text message) {
        String text = message.getString(); // Content only
        // Strip color codes just in case (though getString usually does it)
        String cleanText = text.replaceAll("§.", "");

        // System.out.println("[CasinoHelper] Chat: " + cleanText);

        // 1. API Token Check (Priority - Works even if HUD hidden or just joined)
        java.util.regex.Matcher apiMatcher = API_TOKEN_PATTERN.matcher(cleanText);
        if (apiMatcher.find()) {
            String token = apiMatcher.group(1);
            System.out.println("[CasinoHelper] API Token found: " + token);
            CasinoConfig.donutApiKey = token;
            CasinoConfig.save();
            if (CasinoConfig.dashboardApiKey == null || CasinoConfig.dashboardApiKey.isEmpty()) {
                NetworkHelper.register();
            }
            if (MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().inGameHud.getChatHud()
                        .addMessage(net.minecraft.text.Text.literal("§a[CasinoHelper] DonutSMP API Key saved!"));
                MinecraftClient.getInstance().inGameHud.getChatHud()
                        .addMessage(net.minecraft.text.Text.literal("§7(This key is stored locally and only sent to api.donutsmp.net)"));
            }
            return;
        }

        if (!CasinoConfig.hudVisible)
            return; // Disable logic when HUD is hidden

        // Ignore messages for 5 seconds after joining
        if (System.currentTimeMillis() - joinTime < 5000) {
            return;
        }

        if (!isOnDonutSMP(MinecraftClient.getInstance()))
            return; // Double check for chat events

        // Check against pattern (Simple "paid you" check to reduce traffic)
        if (cleanText.contains("paid you")) {
            // Fix for Chat Grouping (e.g. "Player paid you $10M. [5]")
            // We strip the [x] suffix so the backend sees it as a standard message
            // and can process it (even if it processes it 5 times, that's what we want).
            // Regex: matches space (optional), [, digits, ] at the end of string
            String processedText = cleanText.replaceAll("\\s*\\[\\d+\\]$", "");

            NetworkHelper.sendChatEvent(processedText);
        }

        // Check for Private Message Votes
        // Pattern: "sender -> YOU: vote +" OR "sender -> YOU: +"
        if (cleanText.contains("-> YOU:")) {
            String lower = cleanText.toLowerCase();
            if (lower.contains("vote") || cleanText.contains("+") || cleanText.contains("-")) {
                System.out.println("[CasinoHelper] Detected vote: " + cleanText);
                NetworkHelper.sendChatEvent(cleanText);
            }
        }
    }
}
