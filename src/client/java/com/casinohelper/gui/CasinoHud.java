package com.casinohelper.gui;

import com.casinohelper.config.CasinoConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class CasinoHud implements HudRenderCallback {

    @Override
    public void onHudRender(DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter) {
        if (!CasinoConfig.hudVisible) return;
        
        // Don't render HUD if Screen is open (avoid double render)
        if (MinecraftClient.getInstance().currentScreen instanceof CasinoScreen) return;

        // Use shared rendering logic
        CasinoScreen.renderHudContent(context, -1, -1, false);
    }
}
