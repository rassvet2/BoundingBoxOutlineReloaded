package com.irtimaled.bbor.mixin.client.renderer;

import com.google.common.base.Preconditions;
import com.irtimaled.bbor.client.Player;
import com.irtimaled.bbor.client.RenderCulling;
import com.irtimaled.bbor.client.interop.ClientInterop;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.Handle;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.profiler.Profiler;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class MixinWorldRenderer {

    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "method_62214", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V", args = "ldc=blockentities"))
    private void onRender(Fog fog, RenderTickCounter renderTickCounter, Camera camera, Profiler profiler, Matrix4f matrix4f, Matrix4f matrix4f2, Handle handle, Handle handle2, Handle handle3, Handle handle4, boolean bl, Frustum frustum, Handle handle5, CallbackInfo ci, @Local float f) {
        Preconditions.checkNotNull(this.client.player);
        RenderCulling.setFrustum(frustum);
        Player.setPosition(f, this.client.player);

        MatrixStack matrixStack = new MatrixStack();
        matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));
        ClientInterop.render(matrixStack, this.client.player);
    }

}
