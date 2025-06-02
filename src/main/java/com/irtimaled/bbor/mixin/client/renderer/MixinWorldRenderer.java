package com.irtimaled.bbor.mixin.client.renderer;

import com.google.common.base.Preconditions;
import com.irtimaled.bbor.client.Player;
import com.irtimaled.bbor.client.RenderCulling;
import com.irtimaled.bbor.client.interop.ClientInterop;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.*;
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
    private void onRender(Fog fog, RenderTickCounter renderTickCounter, Camera camera, Profiler profiler, Matrix4f positionMatrix, Matrix4f projectionMatrix, Handle<Framebuffer> mainFramebuffer, Handle<Framebuffer> translucentFramebuffer, boolean renderBlockOutline, Frustum frustum, Handle<Framebuffer> itemEntityFramebuffer, Handle<Framebuffer> entityOutlineFramebuffer, CallbackInfo ci, @Local float f) {
        profiler.swap("bbor-mixin");
        Preconditions.checkNotNull(this.client.player);
        RenderCulling.setFrustum(frustum);
        Player.setPosition(f, this.client.player);

        MatrixStack matrixStack = new MatrixStack();
        matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));
        ClientInterop.render(matrixStack, this.client.player);
    }

}
