package com.irtimaled.bbor.client.renderers;

import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.irtimaled.bbor.client.Camera;
import com.irtimaled.bbor.client.models.Point;
import com.mojang.blaze3d.systems.RenderSystem;

import com.mojang.blaze3d.systems.VertexSorter;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.util.Util;
import net.minecraft.util.math.Box;

import java.awt.*;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import net.minecraft.util.math.ColorHelper;
import org.joml.Vector3f;

/**
 * Intended to be reused. This class is not thread-safe.
 */
public class RenderingContext {

    private final RenderHelper.Allocators allocators = new RenderHelper.Allocators();

    private BufferBuilder quadBufferBuilderNonMasked;
    private BufferBuilder quadBufferBuilderMasked;
    private BufferBuilder lineBufferBuilder;

    private RenderHelper.RenderLayerHelper quadRenderInfo = new RenderHelper.RenderLayerHelper(RenderHelper.getDebugQuads());
    private RenderHelper.RenderLayerHelper maskedQuadRenderInfo = new RenderHelper.RenderLayerHelper(RenderHelper.getDebugQuads());
    private RenderHelper.RenderLayerHelper lineRenderInfo = new RenderHelper.RenderLayerHelper(RenderHelper.getDebugLines());

    private long quadNonMaskedCount;
    private long quadMaskedCount;
    private long lineCount;

    private long lastBuildStartTime = System.nanoTime();
    private long lastBuildDurationNanos;
    private long lastRenderDurationNanos;

    private static final Queue<Runnable> uploadQueue = Queues.newConcurrentLinkedQueue();

    public RenderingContext() {
        reset();
    }

    public void reset() {
        this.quadNonMaskedCount = 0;
        this.quadMaskedCount = 0;
        this.lineCount = 0;
    }

    public void hardReset() {
        reset();
        this.quadRenderInfo.close();
        this.maskedQuadRenderInfo.close();
        this.lineRenderInfo.close();
        this.quadRenderInfo = new RenderHelper.RenderLayerHelper(RenderHelper.getDebugQuads());
        this.maskedQuadRenderInfo = new RenderHelper.RenderLayerHelper(RenderHelper.getDebugQuads());
        this.lineRenderInfo = new RenderHelper.RenderLayerHelper(RenderHelper.getDebugLines());
    }

    public void beginBatch() {
        lastBuildStartTime = System.nanoTime();
        quadBufferBuilderMasked = allocators.getBufferBuilder(RenderHelper.DEBUG_QUADS);
        quadBufferBuilderNonMasked = allocators.getBufferBuilder(RenderHelper.DEBUG_QUADS);
        lineBufferBuilder = allocators.getBufferBuilder(RenderHelper.DEBUG_LINES);
    }

    public void drawSolidBox(Box box, Color color, int alpha, boolean mask) {
        final Vector3f v000 = new Vector3f((float) box.minX, (float) box.minY, (float) box.minZ);
        final Vector3f v100 = new Vector3f((float) box.maxX, (float) box.minY, (float) box.minZ);
        final Vector3f v010 = new Vector3f((float) box.minX, (float) box.maxY, (float) box.minZ);
        final Vector3f v110 = new Vector3f((float) box.maxX, (float) box.maxY, (float) box.minZ);
        final Vector3f v001 = new Vector3f((float) box.minX, (float) box.minY, (float) box.maxZ);
        final Vector3f v101 = new Vector3f((float) box.maxX, (float) box.minY, (float) box.maxZ);
        final Vector3f v011 = new Vector3f((float) box.minX, (float) box.maxY, (float) box.maxZ);
        final Vector3f v111 = new Vector3f((float) box.maxX, (float) box.maxY, (float) box.maxZ);

        if (box.getLengthX() > 0 && box.getLengthZ() > 0) {
            drawFilledFace(v000, v100, v101, v001, color, alpha, mask);
            if (box.getLengthY() > 0) {
                drawFilledFace(v010, v011, v111, v110, color, alpha, mask);
            }
        }

        if (box.getLengthX() > 0 && box.getLengthY() > 0) {
            drawFilledFace(v000, v010, v110, v100, color, alpha, mask);
            if (box.getLengthZ() > 0) {
                drawFilledFace(v001, v101, v111, v011, color, alpha, mask);
            }
        }

        if (box.getLengthY() > 0 && box.getLengthZ() > 0) {
            drawFilledFace(v000, v001, v011, v010, color, alpha, mask);
            if (box.getLengthX() > 0) {
                drawFilledFace(v100, v110, v111, v101, color, alpha, mask);
            }
        }
    }

    public void drawFilledFace(Vector3f point1, Vector3f point2, Vector3f point3, Vector3f point4, Color color, int alpha, boolean mask) {
        if (mask) quadMaskedCount++;
        else quadNonMaskedCount++;

        final BufferBuilder bufferBuilder = mask ? quadBufferBuilderMasked : quadBufferBuilderNonMasked;

        final int colorArgb = ColorHelper.getArgb(alpha, color.getRed(), color.getGreen(), color.getBlue());
        bufferBuilder.vertex(point1).color(colorArgb);
        bufferBuilder.vertex(point2).color(colorArgb);
        bufferBuilder.vertex(point3).color(colorArgb);
        bufferBuilder.vertex(point4).color(colorArgb);
    }

    public void drawLine(Point startPoint, Point endPoint, Color color, int alpha) {
        lineCount++;

        final int colorArgb = ColorHelper.getArgb(alpha, color.getRed(), color.getGreen(), color.getBlue());
        lineBufferBuilder
                .vertex((float) (startPoint.getX()),
                        (float) (startPoint.getY()),
                        (float) (startPoint.getZ()))
                .color(colorArgb);
        lineBufferBuilder
                .vertex((float) (endPoint.getX()),
                        (float) (endPoint.getY()),
                        (float) (endPoint.getZ()))
                .color(colorArgb);
    }

    public void endBatch() {
        List<CompletableFuture<?>> futures = Lists.newArrayListWithExpectedSize(4);

        var sorter = VertexSorter.byDistance((float) Camera.getX(), (float) Camera.getY(), (float) Camera.getZ());

        if (this.quadBufferBuilderNonMasked != null) {
            final BuiltBuffer quadBufferNonMasked = this.quadBufferBuilderNonMasked.endNullable();
            this.quadBufferBuilderNonMasked = null;
            if (quadBufferNonMasked != null) {
                quadBufferNonMasked.sortQuads(allocators.getAllocator(RenderHelper.DEBUG_QUADS), sorter);
            }
            futures.add(CompletableFuture.runAsync(() -> {
                quadRenderInfo.clear();
                if (quadBufferNonMasked == null) return;
                quadRenderInfo.upload(quadBufferNonMasked);
                quadBufferNonMasked.close();
            }, this::postRenderTask));
        }

        if (this.quadBufferBuilderNonMasked != null) {
            final BuiltBuffer quadBufferMasked = this.quadBufferBuilderNonMasked.endNullable();
            this.quadBufferBuilderNonMasked = null;
            if (quadBufferMasked != null) {
                quadBufferMasked.sortQuads(allocators.getAllocator(RenderHelper.DEBUG_QUADS), sorter);
            }
            futures.add(CompletableFuture.runAsync(() -> {
                maskedQuadRenderInfo.clear();
                if (quadBufferMasked == null) return;
                maskedQuadRenderInfo.upload(quadBufferMasked);
                quadBufferMasked.close();
            }, this::postRenderTask));
        }

        if (this.lineBufferBuilder != null) {
            final BuiltBuffer lineBuffer = this.lineBufferBuilder.endNullable();
            this.lineBufferBuilder = null;
            futures.add(CompletableFuture.runAsync(() -> {
                lineRenderInfo.clear();
                if (lineBuffer == null) return;
                lineRenderInfo.upload(lineBuffer);
                lineBuffer.close();
            }, this::postRenderTask));
        }

        Util.combine(futures).join();
        lastBuildDurationNanos = System.nanoTime() - lastBuildStartTime;
    }

    public void doDrawing() {
        RenderSystem.assertOnRenderThread();
        handleRenderTask();

        long startTime = System.nanoTime();

        try {
            if (lineRenderInfo.isUploaded()) lineRenderInfo.draw();
            if (quadRenderInfo.isUploaded()) quadRenderInfo.draw();
            if (maskedQuadRenderInfo.isUploaded()) maskedQuadRenderInfo.draw();
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.lastRenderDurationNanos = System.nanoTime() - startTime;
    }

    public String debugString() {
        return String.format("Faces: %d+%d Lines: %d @ (%.2fms Build, %.2fms Draw)",
                quadMaskedCount, quadNonMaskedCount, lineCount,
                lastBuildDurationNanos / 1_000_000.0, lastRenderDurationNanos / 1_000_000.0);
    }

    public void postRenderTask(Runnable task) {
        if (RenderSystem.isOnRenderThread()) {
            task.run();
        } else {
            uploadQueue.add(task);
        }
    }

    public static void handleRenderTask() {
        RenderSystem.assertOnRenderThread();
        Runnable task;
        while ((task = uploadQueue.poll()) != null) {
            task.run();
        }
    }

}
