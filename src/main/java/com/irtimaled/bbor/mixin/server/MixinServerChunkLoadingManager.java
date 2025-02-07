package com.irtimaled.bbor.mixin.server;

import com.irtimaled.bbor.common.interop.CommonInterop;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkLoadingManager.class)
public class MixinServerChunkLoadingManager {

    @Inject(method = "sendToPlayers", at = @At("HEAD"))
    private void onChunkLoad(ChunkHolder chunkHolder, WorldChunk chunk, CallbackInfo ci) {
        CommonInterop.chunkLoaded(chunk);
    }
}
