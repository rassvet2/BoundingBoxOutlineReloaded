package com.irtimaled.bbor.mixin.client.renderer.access;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gl.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderPipelines.class)
public interface IRenderPipelines {
    @Invoker("register")
    static RenderPipeline register(RenderPipeline pipeline) {
        throw new AssertionError();
    }

    @Accessor("POSITION_COLOR_SNIPPET")
    static RenderPipeline.Snippet POSITION_COLOR_SNIPPET() {
        throw new AssertionError();
    }
}
