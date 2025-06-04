package com.irtimaled.bbor.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.Queues;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.irtimaled.bbor.client.config.ConfigManager;
import com.irtimaled.bbor.client.renderers.AbstractRenderer;
import com.irtimaled.bbor.client.renderers.RenderingContext;
import com.irtimaled.bbor.common.models.AbstractBoundingBox;
import com.irtimaled.bbor.common.models.DimensionId;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.apache.commons.compress.utils.Lists;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class AsyncRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger("BBOR AsyncRenderer");

    private static final Executor EXECUTOR = Executors.newSingleThreadExecutor(
            new ThreadFactoryBuilder().setNameFormat("BBOR Building Thread").setDaemon(true).build()
    );

    private static final RenderingContext SYNC_CONTEXT = new RenderingContext();

    private static final Queue<RenderingContext> activeContexts = Queues.newConcurrentLinkedQueue();
    private static final Queue<RenderingContext> buildingContexts = Queues.newConcurrentLinkedQueue();
    private static final Queue<RenderingContext> dirtyContexts = Queues.newConcurrentLinkedQueue();
    private static final Queue<RenderingContext> contextPool = Util.make(Queues.newConcurrentLinkedQueue(), (queue) -> {
        queue.add(new RenderingContext());
        queue.add(new RenderingContext());
    });
    private static long lastBuildTime = 0;
    private static final AtomicLong lastDurationNanos = new AtomicLong(0L);
    private static final Queue<AbstractBoundingBox> syncRenders = Queues.newConcurrentLinkedQueue();
    private static final AtomicInteger runningBuilds = new AtomicInteger();

    private static boolean lastActive = false;
    private static DimensionId lastDimID = null;

    public static void render(DimensionId dimensionId) {
        runCleanup(dimensionId);
        if (!ClientRenderer.getActive()) return;

        long startTime = System.nanoTime();

        if (ConfigManager.asyncBuilding.get()) {
            startAsyncBuild(dimensionId);

            for (RenderingContext ctx : activeContexts) draw(ctx);
            buildSyncRenders();
            draw(SYNC_CONTEXT);
        } else {
            build0(dimensionId, SYNC_CONTEXT);
            draw(SYNC_CONTEXT);
            RenderCulling.flushRendering();
        }

        RenderingContext.handleRenderTask();
        lastDurationNanos.set(System.nanoTime() - startTime);
    }

    public static void requestRebuild() {
        lastBuildTime = 0;
    }

    private static void draw(RenderingContext ctx) {
        var stack = RenderSystem.getModelViewStack().pushMatrix();
        stack.translate((float) -Camera.getX(), (float) -Camera.getY(), (float) -Camera.getZ());
        RenderSystem.backupProjectionMatrix();
        RenderSystem.getProjectionMatrix().translate(0.0f, 0.0f, 0.01f);
        ctx.doDrawing();
        stack.popMatrix();
        RenderSystem.backupProjectionMatrix();
    }

    private static void startAsyncBuild(DimensionId dimensionId) {
        if (System.currentTimeMillis() - lastBuildTime <= ConfigManager.asyncRebuildInterval.get()) {
            return;
        }

        RenderingContext ctx0 = contextPool.poll();
        if (ctx0 == null && activeContexts.size() > 1) ctx0 = activeContexts.poll();
        if (ctx0 == null) return;
        RenderingContext ctx = ctx0;

        lastBuildTime = System.currentTimeMillis();
        CompletableFuture
                .runAsync(() -> {
                    try {
                        buildingContexts.add(ctx);
                        build0(dimensionId, ctx);
                    } finally {
                        buildingContexts.remove(ctx);
                        transferContexts(activeContexts, contextPool, null);
                        activeContexts.add(ctx);
                    }
                }, EXECUTOR)
                .exceptionallyAsync(throwable -> {
                    LOGGER.error("Error occurred while building buffers async", throwable);
                    MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(
                            Text.of("[BBOR] Error occurred while building buffers async, check logs and try re-enabling rendering: " + throwable.toString())
                                    .copy().styled(style -> style.withBold(true).withColor(TextColor.fromFormatting(Formatting.RED))));
                    return null;
                }, MinecraftClient.getInstance());
    }

    private static void runCleanup(DimensionId dimensionId) {
        // if dimension changes: discard builds
        if (dimensionId != lastDimID) {
            transferContexts(activeContexts, contextPool, null);
        }
        lastDimID = dimensionId;

        // if async building disabled: discard async builds & reset contexts
        if (!ConfigManager.asyncBuilding.get()) {
            transferContexts(activeContexts, dirtyContexts, null);
        }

        // if rendering disabled: discard async & sync builds
        if (!ClientRenderer.getActive() && lastActive) {
            SYNC_CONTEXT.hardReset();
            transferContexts(activeContexts, dirtyContexts, null);
            transferContexts(contextPool, dirtyContexts, null);
        }
        // if rendering enabled: render now
        if (ClientRenderer.getActive() && !lastActive) {
            requestRebuild();
        }
        lastActive = ClientRenderer.getActive();

        // reset dirty contexts
        transferContexts(dirtyContexts, contextPool, RenderingContext::hardReset);

        if (!ClientRenderer.getActive() && runningBuilds.get() == 0) {
            ClientRenderer.doCleanup();
        }
    }

    private static void build0(DimensionId dimensionId, RenderingContext ctx) {
        ctx.reset();
        ctx.beginBatch();
        runningBuilds.incrementAndGet();

        try {
            final List<AbstractBoundingBox> boundingBoxes = ClientRenderer.getBoundingBoxes(dimensionId);
            RenderCulling.flushPreRendering();
            for (AbstractBoundingBox key : boundingBoxes) {
                AbstractRenderer renderer = key.getRenderer();
                if (renderer == null) continue;

                renderer.render(ctx, key);
                if (key.needSyncRendering()) {
                    if (RenderSystem.isOnRenderThread()) {
                        renderer.renderSync(ctx, key);
                    } else {
                        syncRenders.add(key);
                    }
                }
            }
        } finally {
            runningBuilds.decrementAndGet();
            ctx.endBatch();
        }
    }

    private static void buildSyncRenders() {
        if (syncRenders.isEmpty()) return;

        AsyncRenderer.SYNC_CONTEXT.reset();
        AsyncRenderer.SYNC_CONTEXT.beginBatch();

        try {
            AbstractBoundingBox key;
            while ((key = syncRenders.poll()) != null) {
                AbstractRenderer renderer = key.getRenderer();
                if (renderer != null) {
                    renderer.renderSync(AsyncRenderer.SYNC_CONTEXT, key);
                }
            }
        } finally {
            AsyncRenderer.SYNC_CONTEXT.endBatch();
        }
    }

    public static long getLastDurationNanos() {
        return lastDurationNanos.get();
    }

    private static void transferContexts(
            Queue<RenderingContext> src,
            Queue<RenderingContext> dest,
            @Nullable Consumer<RenderingContext> action) {

        Preconditions.checkArgument(src != dest, "src and dest must be different");

        RenderingContext ctx;
        while ((ctx = src.poll()) != null) {
            try {
                if (action != null) action.accept(ctx);
            } finally {
                dest.add(ctx);
            }
        }
    }

    public static List<String> renderingDebugStrings() {
        List<String> msg = Lists.newArrayList();
        if (ClientRenderer.getActive()) {
            if (ConfigManager.asyncBuilding.get() && activeContexts.isEmpty()) {
                msg.add("[BBOR] Preparing rendering...");
            }
        }
        if (!activeContexts.isEmpty() || !buildingContexts.isEmpty() || !dirtyContexts.isEmpty()) {
            for (RenderingContext ctx : activeContexts) {
                msg.add("[BBOR] Active: " + ctx.debugString());
                msg.add("[BBOR] Active: " + ctx.debugMemoryString());
            }
            for (RenderingContext ctx : buildingContexts) {
                msg.add("[BBOR] Build: " + ctx.debugString());
                msg.add("[BBOR] Build: " + ctx.debugMemoryString());
            }
            for (RenderingContext ctx : dirtyContexts) {
                msg.add("[BBOR] Dirty: " + ctx.debugString());
                msg.add("[BBOR] Dirty: " + ctx.debugMemoryString());
            }
            for (RenderingContext ctx : contextPool) {
                msg.add("[BBOR] Pool: " + ctx.debugString());
                msg.add("[BBOR] Pool: " + ctx.debugMemoryString());
            }
        }
        if (ClientRenderer.getActive()) {
            msg.add("[BBOR] Sync: " + SYNC_CONTEXT.debugString());
            msg.add("[BBOR] Sync: " + SYNC_CONTEXT.debugMemoryString());
        }
        return msg;
    }
}
