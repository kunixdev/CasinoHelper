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
    private ColoredButton copyStyleButton;
    private ColoredButton soundToggleButton;
    private int panelX, panelY, panelW, panelH;

    public SettingsPopupScreen(Screen parent) {
        super(Text.literal("Casino Settings"));
        this.parent = parent;
    }

    private net.minecraft.client.gui.widget.TextFieldWidget multiplierInput;

    @Override
    protected void init() {
        panelW = 230;
        panelH = 200; // Increased height
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        int centerX = this.width / 2;
        int baseY = panelY + 40;

        opacitySlider = new OpacitySlider(centerX - 90, baseY, 180, 16, CasinoConfig.backgroundOpacity);
        this.addDrawableChild(opacitySlider);

        int buttonW = 170;
        int buttonH = 16;

        copyStyleButton = new ColoredButton(centerX - (buttonW / 2), baseY + 24, buttonW, buttonH,
                Text.literal(copyLabel()), button -> {
                    CasinoConfig.copyCommandWithSlash = !CasinoConfig.copyCommandWithSlash;
                    CasinoConfig.save();
                    copyStyleButton.setMessage(Text.literal(copyLabel()));
                }, 0xFF3A3F46, 0xFF4A515B);
        this.addDrawableChild(copyStyleButton);

        soundToggleButton = new ColoredButton(centerX - (buttonW / 2), baseY + 48, buttonW, buttonH,
                Text.literal(soundLabel()), button -> {
                    CasinoConfig.soundEnabled = !CasinoConfig.soundEnabled;
                    CasinoConfig.save();
                    soundToggleButton.setMessage(Text.literal(soundLabel()));
                }, 0xFF3A3F46, 0xFF4A515B);
        this.addDrawableChild(soundToggleButton);

        // Multiplier Management
        int multY = baseY + 72;
        int multBtnW = 20;
        int multBtnH = 14;
        int startX = centerX - 80;

        // List Buttons
        int currentX = startX;
        for (int m : CasinoConfig.multipliers) {
            this.addDrawableChild(
                    new ColoredButton(currentX, multY, multBtnW, multBtnH, Text.literal(m + "x"), button -> {
                        // Remove on click
                        if (CasinoConfig.multipliers.size() > 1) {
                            CasinoConfig.multipliers.remove(Integer.valueOf(m));
                            CasinoConfig.save();
                            this.clearChildren();
                            this.init(); // Refresh UI
                        }
                    }, 0xFF555555, 0xFFAA0000)); // Grey normal, Red hover (to indicate delete)
            currentX += multBtnW + 4;
        }

        // Input Field (Small box for custom number)
        int inputW = 30;
        multiplierInput = new net.minecraft.client.gui.widget.TextFieldWidget(this.textRenderer, currentX, multY,
                inputW, multBtnH, Text.literal(""));
        multiplierInput.setMaxLength(3); // Max 3 digits (e.g. 999)
        multiplierInput.setTextPredicate(s -> s.matches("\\d*")); // Only numbers
        this.addDrawableChild(multiplierInput);
        currentX += inputW + 4;

        // Add Button
        this.addDrawableChild(new ColoredButton(currentX, multY, multBtnW, multBtnH, Text.literal("+"), button -> {
            // Limit to 5
            if (CasinoConfig.multipliers.size() >= 5) {
                return; // Do nothing if limit reached
            }

            String text = multiplierInput.getText();
            if (text.isEmpty())
                return;

            try {
                int val = Integer.parseInt(text);
                if (val < 2)
                    val = 2; // Min 2x
                if (val > 100)
                    val = 100; // Cap at 100x

                if (!CasinoConfig.multipliers.contains(val)) {
                    CasinoConfig.multipliers.add(val);
                    java.util.Collections.sort(CasinoConfig.multipliers);
                    CasinoConfig.save();
                    this.clearChildren();
                    this.init();
                }
            } catch (NumberFormatException e) {
                // Ignore invalid input
            }
        }, 0xFF00AA00, 0xFF55FF55));

        this.addDrawableChild(new ColoredButton(centerX - (buttonW / 2), baseY + 104, buttonW, buttonH,
                Text.literal("Reset Stats"), button -> {
                    CasinoConfig.resetStats();
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client != null && client.inGameHud != null) {
                        client.inGameHud.getChatHud().addMessage(Text.literal("[CasinoHelper] Stats reset to 0."));
                    }
                }, 0xFF3A3F46, 0xFF4A515B));

        this.addDrawableChild(new ColoredButton(centerX - (buttonW / 2), baseY + 128, buttonW, buttonH,
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
        context.drawCenteredTextWithShadow(this.textRenderer, "Visuals & commands", this.width / 2, panelY + 22,
                0xCCCCCC);

        // Render children (buttons, sliders, text fields)
        super.render(context, mouseX, mouseY, delta);

        // Draw limit warning if maxed
        // Draw limit warning if maxed
        // if (CasinoConfig.multipliers.size() >= 5) {
        // context.drawText(this.textRenderer, "Max 5 multipliers", this.width / 2 - 40,
        // panelY + 100, 0xFFAA0000,
        // true);
        // }
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

    private String copyLabel() {
        return CasinoConfig.copyCommandWithSlash ? "Copy cmd: with /" : "Copy cmd: without /";
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
