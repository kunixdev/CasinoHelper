package com.casinohelper.gui;

import com.casinohelper.config.CasinoConfig;
import com.casinohelper.gui.widgets.ColoredButton;
import com.casinohelper.utils.CurrencyHelper;
import com.casinohelper.utils.DonutSMPApi;
import com.casinohelper.utils.NetworkHelper;
import com.casinohelper.utils.SoundHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class CasinoScreen extends Screen {
    public static final List<PlayerData> players = new ArrayList<>();
    public static final List<PlayerData> queue = new ArrayList<>();

    private boolean isDragging = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    private boolean isResizing = false;
    private float resizeStartScale = 1.0f;
    private int resizeStartY = 0;

    // Animation State
    // private float openAnimationProgress = 0.0f; // Removed opening animation
    private long lastTickTime = 0;
    // REMOVED: local lastHeartbeat tracking since it's moved to Global Client Tick

    // Base Dimensions (Unscaled)
    private static final int PANEL_WIDTH = 220;
    private static final int HEADER_HEIGHT = 85;
    private static final int ROW_HEIGHT = 60; // Increased to accommodate all player info
    private static final int QUEUE_SECTION_HEIGHT = 20;
    private static final int FOOTER_PADDING = 20;

    private static final net.minecraft.util.Identifier LOGO_TEXTURE = net.minecraft.util.Identifier.of("casinohelper",
            "textures/gui/logo.png");

    public CasinoScreen() {
        super(Text.literal("Casino Helper"));
    }

    private static CasinoScreen instance;

    @Override
    protected void init() {
        super.init();
        instance = this;
        refreshButtons();
    }

    @Override
    public void removed() {
        super.removed();
        instance = null;
    }

    public static void triggerRefresh() {
        if (instance != null) {
            instance.refreshButtons();
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // No background blur/tint
    }

    @Override
    public void tick() {
        super.tick();
        // Update player animations
        for (PlayerData player : players) {
            if (player.slideAnimation < 1.0f) {
                player.slideAnimation += 0.1f;
                if (player.slideAnimation > 1.0f)
                    player.slideAnimation = 1.0f;
            }
        }
        // Heartbeat logic removed from here, handled globally in CasinoHelperClient
    }

    public void refreshButtons() {
        this.clearChildren();

        float scale = CasinoConfig.scale;
        int startX = CasinoConfig.x;
        int startY = (int) (CasinoConfig.y + (HEADER_HEIGHT * scale));
        int panelWidth = (int) (PANEL_WIDTH * scale);

        // Min/Max Control Buttons removed for cleaner UI (Clickable text implemented in
        // mouseClicked)

        for (int i = 0; i < players.size(); i++) {
            PlayerData p = players.get(i);
            final int index = i;
            int rowY = (int) (startY + (i * ROW_HEIGHT * scale));

            // Button Y position - Moved to bottom of box to avoid text overlap
            int btnY = (int) (rowY + (37 * scale));
            int btnHeight = (int) (16 * scale);

            int btnW = (int) (35 * scale);
            int rightAlignX = (int) (startX + (PANEL_WIDTH * scale) - (10 * scale));

            // Lose Button (Right Aligned)
            this.addDrawableChild(
                    new ColoredButton(rightAlignX - btnW, btnY, btnW, btnHeight, Text.literal("Lose"), (button) -> {
                        handleLose(index);
                        refreshButtons();
                    }, 0xFFAA0000, 0xFFFF5555));

            // Win Button (Left of Lose) - Opens multiplier input screen
            this.addDrawableChild(new ColoredButton(rightAlignX - btnW - (int) (3 * scale) - btnW, btnY, btnW,
                    btnHeight, Text.literal("Win"), (button) -> {
                        if (this.client != null) {
                            this.client.setScreen(new MultiplierInputScreen(this, p, (multiplier) -> {
                                handleWin(p, multiplier);
                            }));
                        }
                    }, 0xFF00AA00, 0xFF55FF55));

            // Donation Button (Purple) - Left of Win
            int donateW = (int) (18 * scale);
            this.addDrawableChild(new ColoredButton(
                    rightAlignX - btnW - (int) (3 * scale) - btnW - (int) (3 * scale) - donateW, btnY,
                    donateW, btnHeight, Text.literal("$"), (button) -> {
                        handleDonation(p);
                    }, 0xFF8800AA, 0xFFAA55FF));

            // Cancel Trade Button (Small X) - Left of Donation
            int cancelTradeW = (int) (18 * scale);
            this.addDrawableChild(new ColoredButton(
                    rightAlignX - btnW - (int) (3 * scale) - btnW - (int) (3 * scale) - donateW - (int) (3 * scale)
                            - cancelTradeW,
                    btnY,
                    cancelTradeW, btnHeight, Text.literal("X"), (button) -> {
                        handleWin(p, 1); // 1x logic is refund/cancel
                    }, 0xFFFFAA00, 0xFFFFDD55));
        }

        // Website Button at the bottom (Left Corner, Small Symbol)
        int contentHeight = players.size() * ROW_HEIGHT;
        boolean hasQueue = !queue.isEmpty();
        int queueSpace = hasQueue ? (QUEUE_SECTION_HEIGHT + 4) : 0;

        // Position at the bottom, Left Aligned
        int footerY = (int) (startY + (contentHeight * scale) + (queueSpace * scale) + (3 * scale));
        int btnSize = (int) (14 * scale); // Square button
        int btnX = (int) (startX + (5 * scale)); // Left padding

        this.addDrawableChild(new ColoredButton(btnX, footerY, btnSize, btnSize, Text.literal("🌐"), (button) -> {
            try {
                net.minecraft.util.Util.getOperatingSystem().open(java.net.URI.create("https://casinohelper.online"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0xFF0000AA, 0xFF5555FF)); // Blue Color
    }

    private void adjustMinBet(double amount) {
        CasinoConfig.minBet += amount;
        if (CasinoConfig.minBet < 0)
            CasinoConfig.minBet = 0;
        CasinoConfig.save();
    }

    private void adjustMaxBet(double amount) {
        double current = CasinoConfig.maxBet;
        double next;

        if (amount > 0) {
            // Increasing
            if (current < 1_000_000) {
                next = 1_000_000;
            } else if (current < 5_000_000) {
                next = 5_000_000;
            } else {
                next = current + 5_000_000;
            }
        } else {
            // Decreasing
            if (current > 5_000_000) {
                next = current - 5_000_000;
            } else if (current > 1_000_000) {
                next = 1_000_000;
            } else {
                next = 0;
            }
        }

        if (next < CasinoConfig.minBet)
            next = CasinoConfig.minBet;

        CasinoConfig.maxBet = next;
        CasinoConfig.save();
    }

    private void handleWin(PlayerData player, int multiplier) {
        double betAmount = CurrencyHelper.parseAmount(player.bet);
        double payout = betAmount * multiplier;

        String payoutStr = CurrencyHelper.formatAmount(payout);
        String command = "pay " + player.name + " " + payoutStr;

        // Set pending command and callback
        com.casinohelper.CasinoHelperClient.pendingCommand = command;
        com.casinohelper.CasinoHelperClient.pendingCallback = () -> {
            if (multiplier == 1) {
                // 1x = Return/Void (Cancel)
                if (MinecraftClient.getInstance().getSession() != null) {
                    NetworkHelper.resolveBet(player.txId, "cancel", 1.0, player.name);
                }

                // Revert local stats
                // We added to totalVolume on receive.
                // If cancelled, we should subtract volume.
                CasinoConfig.totalVolume -= betAmount;
                // We also subtracted from netProfit (as if it was a win for host initially? No
                // wait)
                // Let's trace:
                // On Bet Received: totalVolume += amount; netProfit += amount (Host keeps it)
                // On Cancel: totalVolume -= amount; netProfit -= amount (Host gives it back)
                CasinoConfig.netProfit -= betAmount;
                CasinoConfig.save();

            } else {
                // Real Win
                CasinoConfig.wins++;
                CasinoConfig.addPlayerWin(player.name, payout);

                // Update Host Net Profit
                // Host had +amount (wager). Now pays -payout.
                // Net change for this transaction = amount - payout.
                // Since we already added 'amount' to netProfit on receive, we just subtract
                // 'payout'.
                CasinoConfig.netProfit -= payout;
                CasinoConfig.save();

                // Send Resolution to Dashboard
                if (MinecraftClient.getInstance().getSession() != null) {
                    NetworkHelper.resolveBet(player.txId, "win", (double) multiplier, player.name);
                }
            }

            players.remove(player);
            checkQueue();
        };

        // Open chat directly with the command prefilled (user just needs to press Enter)
        if (this.client != null) {
            this.client.setScreen(new net.minecraft.client.gui.screen.ChatScreen("/" + command));
        }
    }

    private void handleLose(int index) {
        PlayerData player = players.get(index);
        double betAmount = CurrencyHelper.parseAmount(player.bet);

        SoundHelper.playLoseSound();

        // Send Resolution to Dashboard
        if (MinecraftClient.getInstance().getSession() != null) {
            NetworkHelper.resolveBet(player.txId, "loss", 0.0, player.name);
        }

        CasinoConfig.losses++;
        // On Loss: Host keeps wager.
        // We already added 'amount' to netProfit on receive.
        // So we don't need to change netProfit here.
        CasinoConfig.save();

        players.remove(index);
        checkQueue();
    }

    private void handleDonation(PlayerData player) {
        // Manual Donation: Keep money, remove from stats/list, NO pay command
        double betAmount = CurrencyHelper.parseAmount(player.bet);

        CasinoConfig.totalVolume -= betAmount;
        CasinoConfig.removePlayerWager(player.name, betAmount);

        // Also cancel on Dashboard so it doesn't count as a wager there
        if (MinecraftClient.getInstance().getSession() != null) {
            NetworkHelper.resolveBet(player.txId, "cancel", 1.0, player.name);
        }

        players.remove(player);
        checkQueue();
        refreshButtons();
    }

    public static void checkQueue() {
        if (players.size() < 2 && !queue.isEmpty()) {
            PlayerData nextPlayer = queue.remove(0);
            players.add(nextPlayer);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderHudContent(context, mouseX, mouseY, true);
        super.render(context, mouseX, mouseY, delta);
    }

    public static void renderHudContent(DrawContext context, int mouseX, int mouseY, boolean isInteractive) {
        MinecraftClient client = MinecraftClient.getInstance();
        int x = CasinoConfig.x;
        int y = CasinoConfig.y;
        float scale = CasinoConfig.scale;

        // Scale matrix for content
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(scale, scale, 1f);

        // All drawing coords are now relative to (0,0) and unscaled

        float opacity = Math.max(0f, Math.min(1f, CasinoConfig.backgroundOpacity));
        int baseAlpha = (int) (opacity * 255);
        int panelAlpha = Math.max(0, Math.min(255, baseAlpha));
        int boxAlpha = Math.max(0, Math.min(255, (int) (baseAlpha * 0.5f)));
        int queueAlpha = Math.max(0, Math.min(255, (int) (baseAlpha * 0.4f)));
        int panelBgColor = (panelAlpha << 24);
        int boxBgColor = (boxAlpha << 24);
        int queueBgColor = (queueAlpha << 24);

        // Calculate height
        int contentHeight = players.size() * ROW_HEIGHT;
        boolean hasQueue = !queue.isEmpty();
        int queueSpace = hasQueue ? (QUEUE_SECTION_HEIGHT + 4) : 0; // extra spacing above footer
        int totalHeight = HEADER_HEIGHT + contentHeight + queueSpace + FOOTER_PADDING;

        // 1. Main Background Panel - Slightly transparent
        context.fill(0, 0, PANEL_WIDTH, totalHeight, panelBgColor);

        // 2. Header Content
        // Logo next to title
        // Logo next to title
        String titleText = "Casino Helper";
        int titleWidth = client.textRenderer.getWidth(titleText);
        int logoSize = 20;
        int spacing = 5;
        int totalHeaderWidth = logoSize + spacing + titleWidth;
        int startX = (PANEL_WIDTH - totalHeaderWidth) / 2;

        // Draw Logo
        context.getMatrices().push();
        context.getMatrices().translate(startX, 0, 0);
        float logoScale = (float) logoSize / 1024.0f;
        context.getMatrices().scale(logoScale, logoScale, 1f);
        context.drawTexture(net.minecraft.client.render.RenderLayer::getGuiTextured, LOGO_TEXTURE, 0, 0, 0.0f, 0.0f,
                1024, 1024, 1024, 1024);
        context.getMatrices().pop();

        // Draw Title
        context.drawTextWithShadow(client.textRenderer, Text.literal(titleText), startX + logoSize + spacing, 5,
                0xFF00FF);

        // Min/Max Display - Centered and larger
        String minStr = CurrencyHelper.formatAmount(CasinoConfig.minBet);
        String maxStr = CurrencyHelper.formatAmount(CasinoConfig.maxBet);

        int minMaxY = 18;
        // Calculate center positions
        int minTextWidth = client.textRenderer.getWidth("Min: ") + client.textRenderer.getWidth(minStr);
        int maxTextWidth = client.textRenderer.getWidth("Max: ") + client.textRenderer.getWidth(maxStr);
        int minX = (PANEL_WIDTH / 2) - minTextWidth - 20;
        int maxX = (PANEL_WIDTH / 2) + 20;

        // Min label (smaller)
        context.getMatrices().push();
        context.getMatrices().scale(0.7f, 0.7f, 1f);
        context.drawTextWithShadow(client.textRenderer, Text.literal("Min:"), (int) (minX / 0.7f),
                (int) (minMaxY / 0.7f), 0xAAAAAA);
        context.getMatrices().pop();

        // Min value (larger)
        context.drawTextWithShadow(client.textRenderer, Text.literal(minStr), minX + 20, minMaxY, 0xFFFF00);

        // Max label (smaller)
        context.getMatrices().push();
        context.getMatrices().scale(0.7f, 0.7f, 1f);
        context.drawTextWithShadow(client.textRenderer, Text.literal("Max:"), (int) (maxX / 0.7f),
                (int) (minMaxY / 0.7f), 0xAAAAAA);
        context.getMatrices().pop();

        // Max value (larger)
        context.drawTextWithShadow(client.textRenderer, Text.literal(maxStr), maxX + 20, minMaxY, 0xFFFF00);

        // Host - Larger and more prominent
        String username = client.getSession() != null ? client.getSession().getUsername() : "Unknown";
        context.getMatrices().push();
        context.getMatrices().scale(1.2f, 1.2f, 1f);
        context.drawTextWithShadow(client.textRenderer, Text.literal("Host: " + username), (int) (10 / 1.2f),
                (int) (35 / 1.2f), 0x00FFFF);
        context.getMatrices().pop();

        // Stats with better spacing and formatting
        context.getMatrices().push();
        context.getMatrices().scale(0.8f, 0.8f, 1f);

        String volumeStr = CurrencyHelper.formatAmount(CasinoConfig.totalVolume);
        context.drawTextWithShadow(client.textRenderer, Text.literal("Wagered: " + volumeStr), (int) (10 / 0.8f),
                (int) (52 / 0.8f), 0xAAAAAA);

        String profitStr = CurrencyHelper.formatAmount(CasinoConfig.netProfit);
        String profitPrefix = CasinoConfig.netProfit >= 0 ? "+" : "";
        int profitColor = CasinoConfig.netProfit >= 0 ? 0x00FF00 : 0xFF0000;
        context.drawTextWithShadow(client.textRenderer, Text.literal("Win/Loss: " + profitPrefix + profitStr),
                (int) (10 / 0.8f), (int) (64 / 0.8f), profitColor);

        context.getMatrices().pop();

        // 3. Player List
        int rowY = HEADER_HEIGHT;

        for (PlayerData player : players) {
            // Apply slide-in animation
            context.getMatrices().push();
            float slideOffset = (1.0f - player.slideAnimation) * PANEL_WIDTH;
            context.getMatrices().translate(-slideOffset, 0, 0);

            // Player Box Background with border
            int boxX1 = 5;
            int boxX2 = PANEL_WIDTH - 5;
            int boxY1 = rowY + 2;
            int boxY2 = rowY + ROW_HEIGHT - 2;

            // Box background - slightly more opaque than main bg
            context.fill(boxX1, boxY1, boxX2, boxY2, boxBgColor);

            // Box border (cyan)
            context.fill(boxX1, boxY1, boxX2, boxY1 + 1, 0xFF00FFFF); // Top
            context.fill(boxX1, boxY2 - 1, boxX2, boxY2, 0xFF00FFFF); // Bottom
            context.fill(boxX1, boxY1, boxX1 + 1, boxY2, 0xFF00FFFF); // Left
            context.fill(boxX2 - 1, boxY1, boxX2, boxY2, 0xFF00FFFF); // Right

            // Green Bar Indicator (inside the box)
            context.fill(boxX1 + 2, boxY1 + 2, boxX1 + 4, boxY2 - 2, 0xFF00FF00);

            // Player Head
            int headSize = (int) (24 * 0.7f);
            int headX = boxX1 + 6;
            int headY = rowY + 7;

            // Load Skin if needed
            if (player.headTexture == null) {
                loadMinotarSkin(player);
            }

            net.minecraft.util.Identifier skinTexture;
            if (player.headTexture != null) {
                skinTexture = player.headTexture;
            } else {
                // Fallback to Steve/Alex while loading
                java.util.UUID uuid = java.util.UUID.nameUUIDFromBytes(
                        ("OfflinePlayer:" + player.name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                skinTexture = client.getSkinProvider()
                        .getSkinTextures(new com.mojang.authlib.GameProfile(uuid, player.name)).texture();
            }

            // Draw using matrix scaling for perfect square
            context.getMatrices().push();
            context.getMatrices().translate(headX, headY, 0);
            float scaleF = headSize / 8.0f; // Scale 8x8 texture to headSize x headSize
            context.getMatrices().scale(scaleF, scaleF, 1.0f);

            // Draw 8x8 face
            // Minotar returns 8x8 avatar, so we draw full texture (0,0 to 8,8)
            // If using internal skin, we draw face region (8,8,8,8) from 64x64 texture

            if (player.headTexture != null) {
                // Minotar Avatar is just the face (8x8 or larger square)
                // Draw full texture
                context.drawTexture(net.minecraft.client.render.RenderLayer::getGuiTextured, skinTexture, 0, 0, 0.0f,
                        0.0f,
                        8, 8, 8, 8);
            } else {
                // Internal Skin (Full Skin Texture)
                context.drawTexture(net.minecraft.client.render.RenderLayer::getGuiTextured, skinTexture, 0, 0, 8.0f,
                        8.0f,
                        8, 8, 64, 64); // Face
                context.drawTexture(net.minecraft.client.render.RenderLayer::getGuiTextured, skinTexture, 0, 0, 40.0f,
                        8.0f,
                        8, 8, 64, 64); // Hat layer
            }

            context.getMatrices().pop();

            // Player text with smaller scale
            context.getMatrices().push();
            float textScale = 0.8f;
            context.getMatrices().scale(textScale, textScale, 1f);
            float invTextScale = 1f / textScale;

            int textStartX = headX + headSize + 8;
            int nameY = rowY + 4;
            int betY = rowY + 18;
            int totalY = rowY + 30;

            // Name (shifted right to account for head)
            context.drawTextWithShadow(client.textRenderer, Text.literal(player.name),
                    (int) (textStartX * invTextScale), (int) (nameY * invTextScale), 0xFFFFFF);

            // Bet (closer to left edge)
            int betValueX = textStartX + client.textRenderer.getWidth("Bet: ") + 4;
            context.drawTextWithShadow(client.textRenderer, Text.literal("Bet: "), (int) (textStartX * invTextScale),
                    (int) (betY * invTextScale), 0x00FF00);
            context.drawTextWithShadow(client.textRenderer, Text.literal(player.bet), (int) (betValueX * invTextScale),
                    (int) (betY * invTextScale), 0xFFFF00);

            // Total Wagered (closer to left edge)
            double totalWagered = CasinoConfig.getPlayerTotal(player.name);
            context.drawTextWithShadow(client.textRenderer,
                    Text.literal("Total: " + CurrencyHelper.formatAmount(totalWagered)),
                    (int) (textStartX * invTextScale), (int) (totalY * invTextScale), 0xAAAAAA);

            // Wallet (from API) - on the right side
            if (player.stats != null) {
                String wallet = CurrencyHelper.formatAmount(CurrencyHelper.parseAmount(player.stats.money));
                context.drawTextWithShadow(client.textRenderer, Text.literal("Wallet: " + wallet),
                        (int) (115 / 0.8f), (int) ((rowY + 5) / 0.8f), 0x00FFFF);
            }

            // Win/Loss (Player PnL) - on the right side
            double pnl = CasinoConfig.getPlayerProfit(player.name);
            String pnlStr = CurrencyHelper.formatAmount(pnl);
            String pnlPrefix = pnl >= 0 ? "+" : "";
            int pnlColor = pnl >= 0 ? 0x00FF00 : 0xFF0000;
            context.drawTextWithShadow(client.textRenderer, Text.literal("W/L: " + pnlPrefix + pnlStr),
                    (int) (115 / 0.8f), (int) ((rowY + 16) / 0.8f), pnlColor);

            context.getMatrices().pop();
            context.getMatrices().pop();
            rowY += ROW_HEIGHT;
        }

        if (isInteractive) {
            // Resize Handle (Bottom Right)
            int handleSize = 10;
            int handleColor = 0xAAFFFFFF;
            context.fill(PANEL_WIDTH - handleSize, totalHeight - handleSize, PANEL_WIDTH, totalHeight, handleColor);

            // Scale Text
            String scaleText = String.format("%.1fx", scale);
            context.drawTextWithShadow(client.textRenderer, Text.literal(scaleText), PANEL_WIDTH - 25, 5, 0xAAAAAA);
        }

        // Website Text at the bottom
        String websiteText = "casinohelper.net";
        context.getMatrices().push();
        float footerScale = 0.7f;
        context.getMatrices().scale(footerScale, footerScale, 1f);
        int footerTextWidth = client.textRenderer.getWidth(websiteText);
        // Center horizontally: (PANEL_WIDTH / 2) / scale - (textWidth / 2)
        // Position vertically: (totalHeight - 8) / scale
        int footerX = (int) ((PANEL_WIDTH / 2) / footerScale) - (footerTextWidth / 2);
        int footerY = (int) ((totalHeight - 8) / footerScale);
        context.drawTextWithShadow(client.textRenderer, Text.literal(websiteText), footerX, footerY, 0xAAAAAA);
        context.getMatrices().pop();

        // Queue Counter at bottom (above footer) - only when queue has players
        if (hasQueue) {
            int queueY = HEADER_HEIGHT + contentHeight + 4;
            int queueBoxHeight = QUEUE_SECTION_HEIGHT;

            context.fill(5, queueY, PANEL_WIDTH - 5, queueY + queueBoxHeight, queueBgColor);

            // Queue box border
            context.fill(5, queueY, PANEL_WIDTH - 5, queueY + 1, 0xFFFFA500); // Top
            context.fill(5, queueY + queueBoxHeight - 1, PANEL_WIDTH - 5, queueY + queueBoxHeight, 0xFFFFA500); // Bottom
            context.fill(5, queueY, 6, queueY + queueBoxHeight, 0xFFFFA500); // Left
            context.fill(PANEL_WIDTH - 6, queueY, PANEL_WIDTH - 5, queueY + queueBoxHeight, 0xFFFFA500); // Right

            context.getMatrices().push();
            float queueScale = 0.7f;
            context.getMatrices().scale(queueScale, queueScale, 1f);

            int queueSize = queue.size();
            String queueText = "Waiting:";
            int textX = (int) (8 / queueScale);
            int textY = (int) ((queueY + 4) / queueScale);
            context.drawTextWithShadow(client.textRenderer, Text.literal(queueText), textX, textY, 0xFFA500);

            int badgeX = textX + client.textRenderer.getWidth(queueText) + 4;
            int badgeY = textY - 1;
            context.fill(badgeX, badgeY, badgeX + 14, badgeY + 10, 0xFFFFA500);
            context.drawBorder(badgeX, badgeY, 14, 10, 0xFFFFFFFF);
            String countStr = String.valueOf(queueSize);
            int countTextX = badgeX + (14 - client.textRenderer.getWidth(countStr)) / 2;
            context.drawTextWithShadow(client.textRenderer, Text.literal(countStr), countTextX, badgeY + 1, 0xFF000000);

            int boxStartX = badgeX + 18;
            int iconY = badgeY + 1;
            int icons = Math.min(queueSize, 5);
            for (int i = 0; i < icons; i++) {
                int boxX = boxStartX + (i * 12);
                context.fill(boxX, iconY, boxX + 10, iconY + 8, 0xFFFFA500);
                context.drawBorder(boxX, iconY, 10, 8, 0xFFFFFFFF);
            }

            if (queueSize > icons) {
                context.drawTextWithShadow(client.textRenderer, Text.literal("+"), boxStartX + (icons * 12), textY,
                        0xFFA500);
            }

            context.getMatrices().pop();
        }

        context.getMatrices().pop();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = CasinoConfig.x;
        int y = CasinoConfig.y;
        float scale = CasinoConfig.scale;

        // Let UI widgets capture clicks first (e.g., Settings button, player buttons)
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Check Min/Max Text Clicks (Left Click +, Right Click -)
        // Min Text Area: (10, 18) to approx (80, 28) relative to panel
        // Max Text Area: (110, 18) to approx (180, 28) relative to panel

        double relX = (mouseX - x) / scale;
        double relY = (mouseY - y) / scale;

        if (relY >= 15 && relY <= 30) {
            if (relX >= 10 && relX <= 90) {
                // Min Bet Clicked
                if (button == 0)
                    adjustMinBet(1_000_000); // Left Click +
                else if (button == 1)
                    adjustMinBet(-1_000_000); // Right Click -
                return true;
            } else if (relX >= 110 && relX <= 190) {
                // Max Bet Clicked
                if (button == 0)
                    adjustMaxBet(5_000_000); // Left Click +
                else if (button == 1)
                    adjustMaxBet(-5_000_000); // Right Click -
                return true;
            }
        }

        if (button == 0) {
            int contentHeight = players.size() * ROW_HEIGHT;
            boolean hasQueue = !queue.isEmpty();
            int queueSpace = hasQueue ? (QUEUE_SECTION_HEIGHT + 4) : 0;
            int totalHeight = (int) ((HEADER_HEIGHT + contentHeight + queueSpace + FOOTER_PADDING) * scale);
            int panelWidth = (int) (PANEL_WIDTH * scale);
            int headerHeight = (int) (HEADER_HEIGHT * scale);

            // Check Resize Handle (Bottom Right)
            int handleSize = (int) (10 * scale);
            if (mouseX >= x + panelWidth - handleSize && mouseX <= x + panelWidth &&
                    mouseY >= y + totalHeight - handleSize && mouseY <= y + totalHeight) {
                isResizing = true;
                resizeStartScale = scale;
                resizeStartY = (int) mouseY;
                return true;
            }

            // Check header click for drag
            if (mouseX >= x && mouseX <= x + panelWidth && mouseY >= y && mouseY <= y + headerHeight) {
                isDragging = true;
                dragOffsetX = (int) mouseX - x;
                dragOffsetY = (int) mouseY - y;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDragging = false;
        isResizing = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isResizing) {
            // Simple vertical drag to scale
            double diff = mouseY - resizeStartY;
            float newScale = resizeStartScale + (float) (diff / 100.0);
            newScale = Math.max(0.5f, Math.min(3.0f, newScale)); // Clamp scale

            if (newScale != CasinoConfig.scale) {
                CasinoConfig.scale = newScale;
                refreshButtons(); // Re-layout buttons with new scale
            }
            return true;
        }

        if (isDragging) {
            CasinoConfig.x = (int) mouseX - dragOffsetX;
            CasinoConfig.y = (int) mouseY - dragOffsetY;
            refreshButtons();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Prevent ENTER from re-triggering last button press; close screen instead.
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            if (this.client != null) {
                this.client.setScreen(null);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static void loadMinotarSkin(PlayerData p) {
        if (p.loadingSkin || p.headTexture != null)
            return;
        p.loadingSkin = true;

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                // Bedrock Support: Strip leading dot
                String requestName = p.name.startsWith(".") ? p.name.substring(1) : p.name;
                java.net.URL url = java.net.URI.create("https://minotar.net/avatar/" + requestName).toURL();
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() == 200) {
                    try (java.io.InputStream in = conn.getInputStream()) {
                        net.minecraft.client.texture.NativeImage image = net.minecraft.client.texture.NativeImage
                                .read(in);

                        // Schedule texture registration on main thread
                        MinecraftClient.getInstance().execute(() -> {
                            net.minecraft.client.texture.NativeImageBackedTexture texture = new net.minecraft.client.texture.NativeImageBackedTexture(
                                    image);
                            net.minecraft.util.Identifier id = net.minecraft.util.Identifier.of("casinohelper",
                                    "avatar/" + p.name.toLowerCase());
                            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
                            p.headTexture = id;
                            p.loadingSkin = false;
                        });
                    }
                } else {
                    p.loadingSkin = false; // Retry later or fail silently
                }
            } catch (Exception e) {
                e.printStackTrace();
                p.loadingSkin = false;
            }
        });
    }

    public static class PlayerData {
        public String name;
        public String bet;
        public String total;
        public String txId;
        public DonutSMPApi.Stats stats;
        public float slideAnimation = 0.0f;
        public net.minecraft.util.Identifier headTexture = null;
        public boolean loadingSkin = false;

        public PlayerData(String name, String bet, String total, String txId) {
            this.name = name;
            this.bet = bet;
            this.total = total;
            this.txId = txId;
        }
    }
}
