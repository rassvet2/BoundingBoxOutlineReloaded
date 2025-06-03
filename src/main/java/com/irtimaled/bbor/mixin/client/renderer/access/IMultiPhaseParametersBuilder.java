package com.irtimaled.bbor.mixin.client.renderer.access;

import net.minecraft.client.render.RenderLayer.MultiPhaseParameters;
import net.minecraft.client.render.RenderLayer.MultiPhaseParameters.Builder;
import net.minecraft.client.render.RenderPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Builder.class)
public interface IMultiPhaseParametersBuilder {
    @Invoker("lineWidth")
    Builder lineWidth0(RenderPhase.LineWidth lineWidth);

    @Invoker("target")
    Builder target0(RenderPhase.Target target);

    @Invoker("build")
    MultiPhaseParameters build0(boolean affectsOutline);
}
