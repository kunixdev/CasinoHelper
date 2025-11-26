package com.casinohelper.gui;

import com.casinohelper.config.CasinoConfig;
import com.casinohelper.gui.widgets.ColoredButton;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class MultiplierInputScreen extends Screen {
    private final Screen parent;
    private final CasinoScreen.PlayerData player;
    private final MultiplierCallback callback;
    private TextFieldWidget multiplierField;

    public interface MultiplierCallback {
        void onMultiplierEntered(int multiplier);
    }

    public MultiplierInputScreen(Screen parent, CasinoScreen.PlayerData player, MultiplierCallback callback) {
        super(Text.literal("Enter Multiplier"));
        this.parent = parent;
        this.player = player;
        this.callback = callback;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Create text field for multiplier input
        this.multiplierField = new TextFieldWidget(this.textRenderer, centerX - 40, centerY - 10, 80, 20, Text.literal("Multiplier"));
        this.multiplierField.setMaxLength(3); // Max 3 digits (e.g. 999)
        this.multiplierField.setTextPredicate(s -> s.matches("\\d*")); // Only numbers
        this.multiplierField.setText(""); // Start empty so user can type directly
        this.multiplierField.setFocused(true);
        this.addDrawableChild(multiplierField);
        this.setFocused(multiplierField); // Ensure screen focus is set to the text field

        // Confirm button
        this.addDrawableChild(new ColoredButton(centerX - 94, centerY + 20, 88, 18, Text.literal("Confirm"), btn -> {
            confirmMultiplier();
        }, 0xFF00AA00, 0xFF55FF55));

        // Cancel button
        this.addDrawableChild(new ColoredButton(centerX + 6, centerY + 20, 88, 18, Text.literal("Cancel"), btn -> {
            this.close();
        }, 0xFFAA0000, 0xFFFF5555));
    }

    private void confirmMultiplier() {
        String text = multiplierField.getText();
        if (text.isEmpty()) {
            return;
        }

        try {
            int multiplier = Integer.parseInt(text);
            if (multiplier < 1) multiplier = 1;
            if (multiplier > 999) multiplier = 999;

            // invoke callback which handles opening the chat
            callback.onMultiplierEntered(multiplier);

        } catch (NumberFormatException e) {
            // Invalid input, ignore
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int boxWidth = 210;
        int boxHeight = 100;
        int boxX1 = (this.width - boxWidth) / 2;
        int boxY1 = (this.height - boxHeight) / 2 - 10;
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

        context.drawCenteredTextWithShadow(this.textRenderer, "Enter Multiplier", this.width / 2, boxY1 + 8, 0x00FFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, "Player: " + player.name + " | Bet: " + player.bet, this.width / 2, boxY1 + 22, 0xFFFFFF);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Intentionally empty to avoid dim/blur behind this popup.
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter key confirms
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            confirmMultiplier();
            return true;
        }
        // Escape closes
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }
}

