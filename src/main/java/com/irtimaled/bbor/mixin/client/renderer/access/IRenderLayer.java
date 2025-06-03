package com.irtimaled.bbor.mixin.client.renderer.access;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.render.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderLayer.class)
public interface IRenderLayer {
    @Invoker("of")
    static RenderLayer.MultiPhase of(String name, int size, RenderPipeline pipeline, RenderLayer.MultiPhaseParameters params) {
        throw new AssertionError();
    }

    @Invoker("of")
    static RenderLayer.MultiPhase of(
            String name, int size, boolean hasCrumbling, boolean translucent, RenderPipeline pipeline, RenderLayer.MultiPhaseParameters params
    ) {
        throw new AssertionError();
    }
}
