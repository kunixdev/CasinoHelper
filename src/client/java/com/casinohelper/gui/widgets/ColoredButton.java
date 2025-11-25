package com.casinohelper.gui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class ColoredButton extends ButtonWidget {
    private final int color;
    private final int hoverColor;
    private float scale = 1.0f;
    // Base text scale; final scale follows button height so it grows/shrinks with the GUI
    private static final float BASE_TEXT_SCALE = 0.55f;
    private static final int BASE_HEIGHT = 14;

    public ColoredButton(int x, int y, int width, int height, Text message, PressAction onPress, int color, int hoverColor) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        this.color = color;
        this.hoverColor = hoverColor;
    }

    @Override
    public void onPress() {
        super.onPress();
        this.scale = 0.9f; // Click shrink effect
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        // Animate scale back to 1.0
        if (scale < 1.0f) {
            scale += 0.05f;
            if (scale > 1.0f) scale = 1.0f;
        }

        int renderColor = this.isSelected() ? hoverColor : color;
        
        context.getMatrices().push();
        
        // Scale around center
        float centerX = this.getX() + this.width / 2.0f;
        float centerY = this.getY() + this.height / 2.0f;
        
        context.getMatrices().translate(centerX, centerY, 0);
        context.getMatrices().scale(scale, scale, 1.0f);
        context.getMatrices().translate(-centerX, -centerY, 0);

        // Draw colored rectangle
        context.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, renderColor);
        // Subtle bevel for a nicer look
        context.fill(this.getX() + 1, this.getY() + 1, this.getX() + this.width - 1, this.getY() + (this.height / 2), 0x22FFFFFF);
        context.fill(this.getX() + 1, this.getY() + (this.height / 2), this.getX() + this.width - 1, this.getY() + this.height - 1, 0x22000000);
        
        // Draw border with inner accent
        int borderColor = 0xFFFFFFFF;
        context.drawBorder(this.getX(), this.getY(), this.width, this.height, borderColor);
        context.drawBorder(this.getX() + 1, this.getY() + 1, this.width - 2, this.height - 2, 0x55FFFFFF);

        // Draw text centered
        int textColor = 0xFFFFFF;
        float heightScale = BASE_TEXT_SCALE * (this.height / (float) BASE_HEIGHT);
        int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(this.getMessage());
        int usableWidth = Math.max(8, this.width - 6);
        float widthScale = textWidth == 0 ? 1.0f : (usableWidth / (float) textWidth);
        float textScale = Math.max(0.45f, Math.min(1.0f, Math.min(heightScale, widthScale)));
        context.getMatrices().push();
        context.getMatrices().scale(textScale, textScale, 1.0f);
        float inv = 1.0f / textScale;
        int fontHeight = MinecraftClient.getInstance().textRenderer.fontHeight;
        float textYUnscaled = this.getY() + (this.height - (fontHeight * textScale)) / 2.0f;
        int textX = Math.round((float)((this.getX() + this.width / 2.0f) * inv));
        int textY = Math.round(textYUnscaled * inv);
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, this.getMessage(), textX, textY, textColor);
        context.getMatrices().pop();
        
        context.getMatrices().pop();
    }
}

