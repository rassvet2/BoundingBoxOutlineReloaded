package com.irtimaled.bbor.client.renderers;

import com.irtimaled.bbor.client.config.ConfigManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;

public class RenderHelper {

    public static void beforeRender() {
        enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
//        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        RenderSystem.disableCull();
        enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);

        if (ConfigManager.alwaysVisible.get()) {
            RenderSystem.disableDepthTest();
        }
    }

    public static void afterRender() {
        disableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        disableDepthTest();
        RenderSystem.enableCull();
//        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }
}
