package com.casinohelper.gui;

import com.casinohelper.config.CasinoConfig;
import com.casinohelper.gui.widgets.ColoredButton;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;

public class CommandPopupScreen extends Screen {
    private final Screen parent;
    private final String command;
    private TextFieldWidget commandField;
    private ColoredButton copyButton;
    private ColoredButton closeButton;
    private final String displayCommand;

    public CommandPopupScreen(Screen parent, String command) {
        super(Text.literal("Command Popup"));
        this.parent = parent;
        this.command = command;
        this.displayCommand = CasinoConfig.copyCommandWithSlash ? "/" + command : command;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.commandField = new TextFieldWidget(this.textRenderer, centerX - 90, centerY - 20, 180, 18, Text.literal("Command"));
        this.commandField.setText(displayCommand);
        this.commandField.setEditable(false);
        this.commandField.setEditableColor(0xD0E8FF); // subtle cyan tint for legibility
        this.commandField.setUneditableColor(0xD0E8FF);
        this.addDrawableChild(commandField);

        this.copyButton = new ColoredButton(centerX - 94, centerY + 10, 88, 16, Text.literal("Copy"), btn -> {
            this.client.keyboard.setClipboard(displayCommand);
            if (this.client.player != null) {
                this.client.inGameHud.getChatHud().addMessage(Text.literal("[CasinoHelper] Command copied."));
            }
            if (this.client != null) {
                this.client.setScreen(null); // fully close menus after copying
            }
        }, 0xFF3A3F46, 0xFF4A515B);
        this.addDrawableChild(copyButton);

        this.closeButton = new ColoredButton(centerX + 6, centerY + 10, 88, 16, Text.literal("Close"), btn -> this.close(), 0xFF3A3F46, 0xFF4A515B);
        this.addDrawableChild(closeButton);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int boxWidth = 210;
        int boxHeight = 95;
        int boxX1 = (this.width - boxWidth) / 2;
        int boxY1 = (this.height - boxHeight) / 2 - 6;
        int boxX2 = boxX1 + boxWidth;
        int boxY2 = boxY1 + boxHeight;

        int alpha = (int)(CasinoConfig.backgroundOpacity * 255);
        alpha = Math.max(0, Math.min(255, alpha));
        int panelColor = (alpha << 24);

        context.fill(boxX1, boxY1, boxX2, boxY2, panelColor);
        // Border echoes the main panel cyan styling
        context.fill(boxX1, boxY1, boxX2, boxY1 + 1, 0xFF00FFFF);
        context.fill(boxX1, boxY2 - 1, boxX2, boxY2, 0xFF00FFFF);
        context.fill(boxX1, boxY1, boxX1 + 1, boxY2, 0xFF00FFFF);
        context.fill(boxX2 - 1, boxY1, boxX2, boxY2, 0xFF00FFFF);

        context.drawCenteredTextWithShadow(this.textRenderer, "Copy & Pay", this.width / 2, boxY1 + 8, 0x00FFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, "Run this command manually:", this.width / 2, boxY1 + 22, 0xFFFFFF);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Intentionally empty to avoid dim/blur behind this popup.
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}

