package com.irtimaled.bbor.client;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class AsyncRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger("BBOR AsyncRenderer");

    private static final Executor EXECUTOR = Executors.newSingleThreadExecutor(
            new ThreadFactoryBuilder().setNameFormat("BBOR Building Thread").setDaemon(true).build()
    );

    private static final RenderingContext SYNC_CONTEXT = new RenderingContext();

    private static final RenderingContext[] asyncContexts = new RenderingContext[] {
            new RenderingContext(),
            new RenderingContext(),
    };
    private static int currentAsyncContext = -1;
    private static CompletableFuture<Void> buildingFuture = null;
    private static boolean lastActive = false;
    private static boolean toDiscardBuild = false;
    private static long lastBuildTime = System.currentTimeMillis();
    private static AtomicLong lastDurationNanos = new AtomicLong(0L);
    private static DimensionId lastDimID = null;
    private static RenderingContext lastCtx;
    private static final Queue<AbstractBoundingBox> syncRenders = Queues.newConcurrentLinkedQueue();
    private static final AtomicInteger runningBuilds = new AtomicInteger();

    static void render(DimensionId dimensionId) {
        runCleanup();

        if (!ClientRenderer.getActive()) {
            if (lastActive) {
                lastActive = false;
                SYNC_CONTEXT.hardReset();
                // invalidate async things
                if (buildingFuture != null || currentAsyncContext != -1) {
                    currentAsyncContext = -1;
                    toDiscardBuild = true;
                }
                if (buildingFuture == null) {
                    for (RenderingContext context : asyncContexts) {
                        context.hardReset();
                    }
                }
            }
            if (runningBuilds.get() == 0) {
                ClientRenderer.doCleanup();
            }
            return;
        }

        lastActive = true;

        long startTime = System.nanoTime();

        final Boolean useAsync = ConfigManager.asyncBuilding.get();
        if (useAsync) {
            final int i = getNextAsyncContext(dimensionId);
            if (i != -1) {
                final RenderingContext ctx = asyncContexts[i];

                draw(ctx);
                buildSyncRenders();
                draw(SYNC_CONTEXT);

                lastCtx = ctx;
            } else {
                lastCtx = null;
            }
        } else {
            // invalidate async things
            currentAsyncContext = -1;
            toDiscardBuild = true;

            final RenderingContext ctx = SYNC_CONTEXT;


            build0(dimensionId, ctx);
            draw(ctx);
            RenderCulling.flushRendering();

            lastCtx = ctx;
        }

        RenderingContext.handleRenderTask();
        lastDurationNanos.set(System.nanoTime() - startTime);
    }

    private static void draw(RenderingContext ctx) {
        var stack = RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.getProjectionMatrix().translate(0.0f, 0.0f, 0.01f);
        stack.translate(
                (float) -Camera.getX(),
                (float) -Camera.getY(),
                (float) -Camera.getZ()
        );
        ctx.doDrawing();
        stack.popMatrix();
        RenderSystem.backupProjectionMatrix();
    }

    private static int getNextAsyncContext(DimensionId dimensionId) {
        if (dimensionId != lastDimID) {
            currentAsyncContext = -1;
            lastDimID = dimensionId;
            toDiscardBuild = true;
        }
        if ((buildingFuture != null && !buildingFuture.isDone()) || lastBuildTime + 2000 >= System.currentTimeMillis()) {
            return currentAsyncContext;
        }

        lastBuildTime = System.currentTimeMillis();

        RenderingContext ctx = asyncContexts[(currentAsyncContext + 1) % asyncContexts.length];
        buildingFuture = CompletableFuture
                .runAsync(() -> build0(dimensionId, ctx), EXECUTOR)
                .exceptionallyAsync(throwable -> {
                    LOGGER.error("Error occurred while building buffers async", throwable);
                    MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(
                            Text.of("[BBOR] Error occurred while building buffers async, check logs and try re-enabling rendering: " + throwable.toString())
                                    .copy().styled(style -> style.withBold(true).withColor(TextColor.fromFormatting(Formatting.RED))));
                    return null;
                }, MinecraftClient.getInstance());
        return currentAsyncContext;
    }

    private static void runCleanup() {
        if (buildingFuture != null && buildingFuture.isDone()) {
            if (!toDiscardBuild) {
                currentAsyncContext = (currentAsyncContext + 1) % asyncContexts.length;
            } else {
                toDiscardBuild = false;
            }
            buildingFuture = null;
        }
    }

    private static void build0(DimensionId dimensionId, RenderingContext ctx) {
        ctx.reset();
        ctx.beginBatch();
        runningBuilds.incrementAndGet();

        try {
            final List<AbstractBoundingBox> boundingBoxes = ClientRenderer.getBoundingBoxes(dimensionId);
            final List<AbstractBoundingBox> syncs = new ObjectArrayList<>();
            RenderCulling.flushPreRendering();
            for (AbstractBoundingBox key : boundingBoxes) {
                AbstractRenderer renderer = key.getRenderer();
                if (renderer == null) continue;

                profiler.push(renderer.getClass().getSimpleName());

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

    public static List<String> renderingDebugStrings() {
        final RenderingContext ctx = lastCtx;
        if (ctx == null) {
            return List.of("[BBOR] Preparing rendering...");
        } else {
            if (currentAsyncContext != -1) {
                return List.of(
                        "[BBOR] Async" + (currentAsyncContext == 0 ? "*" : " ") + ": " + asyncContexts[0].debugString(),
                        "[BBOR] Async" + (currentAsyncContext == 1 ? "*" : " ") + ": " + asyncContexts[1].debugString(),
                        "[BBOR] Sync: " + SYNC_CONTEXT.debugString()
                );
            } else {
                return List.of("[BBOR] " + ctx.debugString());
            }
        }
    }
}
