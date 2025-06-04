package com.irtimaled.bbor.mixin.client.renderer.access;

import net.minecraft.client.render.RenderPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderPhase.class)
public interface IRenderPhase {
    @Accessor("TRANSLUCENT_TARGET")
    static RenderPhase.Target TRANSLUCENT_TARGET() {
        throw new AssertionError();
    }

    @Accessor("FULL_LINE_WIDTH")
    static RenderPhase.LineWidth FULL_LINE_WIDTH() {
        throw new AssertionError();
    }
}
