package com.casinohelper.gui;

import com.casinohelper.config.CasinoConfig;
import com.casinohelper.gui.widgets.ColoredButton;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public class SettingsPopupScreen extends Screen {
    private final Screen parent;
    private OpacitySlider opacitySlider;
    private ColoredButton soundToggleButton;
    private int panelX, panelY, panelW, panelH;

    public SettingsPopupScreen(Screen parent) {
        super(Text.literal("Casino Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelW = 230;
        panelH = 140; // Reduced height since we removed multiplier section and copy style
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        int centerX = this.width / 2;
        int baseY = panelY + 40;

        opacitySlider = new OpacitySlider(centerX - 90, baseY, 180, 16, CasinoConfig.backgroundOpacity);
        this.addDrawableChild(opacitySlider);

        int buttonW = 170;
        int buttonH = 16;

        soundToggleButton = new ColoredButton(centerX - (buttonW / 2), baseY + 24, buttonW, buttonH,
                Text.literal(soundLabel()), button -> {
                    CasinoConfig.soundEnabled = !CasinoConfig.soundEnabled;
                    CasinoConfig.save();
                    soundToggleButton.setMessage(Text.literal(soundLabel()));
                }, 0xFF3A3F46, 0xFF4A515B);
        this.addDrawableChild(soundToggleButton);

        this.addDrawableChild(new ColoredButton(centerX - (buttonW / 2), baseY + 48, buttonW, buttonH,
                Text.literal("Reset Stats"), button -> {
                    CasinoConfig.resetStats();
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client != null && client.inGameHud != null) {
                        client.inGameHud.getChatHud().addMessage(Text.literal("[CasinoHelper] Stats reset to 0."));
                    }
                }, 0xFF3A3F46, 0xFF4A515B));

        this.addDrawableChild(new ColoredButton(centerX - (buttonW / 2), baseY + 72, buttonW, buttonH,
                Text.literal("Close"), button -> this.close(), 0xFF3A3F46, 0xFF4A515B));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int alpha = (int) (CasinoConfig.backgroundOpacity * 255);
        alpha = Math.max(0, Math.min(255, alpha));
        int panelColor = (alpha << 24);

        int innerTop = panelY + 4;
        int innerBottom = panelY + panelH - 4;
        int innerLeft = panelX + 4;
        int innerRight = panelX + panelW - 4;

        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, panelColor);
        // Border matching main UI
        context.fill(panelX, panelY, panelX + panelW, panelY + 2, 0xFF00FFFF);
        context.fill(panelX, panelY + panelH - 2, panelX + panelW, panelY + panelH, 0xFF00FFFF);
        context.fill(panelX, panelY, panelX + 2, panelY + panelH, 0xFF00FFFF);
        context.fill(panelX + panelW - 2, panelY, panelX + panelW, panelY + panelH, 0xFF00FFFF);

        // Soft inner highlight
        context.fill(innerLeft, innerTop, innerRight, innerBottom, 0x11FFFFFF);

        context.drawCenteredTextWithShadow(this.textRenderer, "Casino Helper Settings", this.width / 2, panelY + 8,
                0x00FFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, "Visuals & Audio", this.width / 2, panelY + 22,
                0xCCCCCC);

        // Render children (buttons, sliders, text fields)
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Intentionally empty: keep world visible without blur/dim.
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    private String soundLabel() {
        return CasinoConfig.soundEnabled ? "Sound Effects: ON" : "Sound Effects: OFF";
    }

    private static class OpacitySlider extends SliderWidget {
        OpacitySlider(int x, int y, int width, int height, float value) {
            super(x, y, width, height, Text.literal(""), value);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int percent = (int) Math.round(this.value * 100);
            this.setMessage(Text.literal("Opacity: " + percent + "%"));
        }

        @Override
        protected void applyValue() {
            float clamped = (float) Math.max(0.0, Math.min(1.0, this.value));
            CasinoConfig.backgroundOpacity = clamped;
            CasinoConfig.save();
        }
    }
}
