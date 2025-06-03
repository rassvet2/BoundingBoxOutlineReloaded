package com.irtimaled.bbor.client.renderers;

import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.irtimaled.bbor.client.Camera;
import com.irtimaled.bbor.client.models.Point;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Util;
import net.minecraft.util.math.Box;

import java.awt.*;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.mojang.blaze3d.vertex.VertexFormat;

/**
 * Intended to be reused. This class is not thread-safe.
 */
public class RenderingContext {

    private final BufferAllocator quadBuffAllocatorNonMasked = new BufferAllocator(2097152);
    private final BufferAllocator quadBuffAllocatorMasked = new BufferAllocator(2097152);
    private final BufferAllocator lineBuffAllocator = new BufferAllocator(2097152);

    private BufferBuilder quadBufferBuilderNonMasked;
    private BufferBuilder quadBufferBuilderMasked;
    private BufferBuilder lineBufferBuilder;

    private final RenderHelper.RenderLayerHelper quadRenderInfo = new RenderHelper.RenderLayerHelper(RenderHelper.DEBUG_QUADS);
    private final RenderHelper.RenderLayerHelper maskedQuadRenderInfo = new RenderHelper.RenderLayerHelper(RenderHelper.DEBUG_QUADS);
    private final RenderHelper.RenderLayerHelper lineRenderInfo = new RenderHelper.RenderLayerHelper(RenderHelper.DEBUG_LINES);

    private long quadNonMaskedCount;
    private long quadMaskedCount;
    private long lineCount;

    private long lastBuildStartTime = System.nanoTime();
    private long lastBuildDurationNanos;
    private long lastRenderDurationNanos;

    private volatile double baseX;
    private volatile double baseY;
    private volatile double baseZ;

    private static final Queue<Runnable> uploadQueue = Queues.newConcurrentLinkedQueue();

    public RenderingContext() {
        reset();
    }

    public void reset() {
        this.baseX = Camera.getX();
        this.baseY = Camera.getY();
        this.baseZ = Camera.getZ();

        this.quadNonMaskedCount = 0;
        this.quadMaskedCount = 0;
        this.lineCount = 0;
    }

    public void hardReset() {
        reset();
        this.quadRenderInfo.clear();
        this.maskedQuadRenderInfo.clear();
        this.lineRenderInfo.clear();
    }

    public double getBaseX() {
        return this.baseX;
    }

    public double getBaseY() {
        return this.baseY;
    }

    public double getBaseZ() {
        return this.baseZ;
    }

    public void beginBatch() {
        lastBuildStartTime = System.nanoTime();
        quadBufferBuilderMasked = new BufferBuilder(quadBuffAllocatorMasked, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        quadBufferBuilderNonMasked = new BufferBuilder(quadBuffAllocatorNonMasked, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        lineBufferBuilder = new BufferBuilder(lineBuffAllocator, VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
    }

    public void drawSolidBox(Box box, Color color, int alpha, boolean mask, boolean sameX, boolean sameY, boolean sameZ) {
        final float minX = (float) (box.minX - baseX);
        final float minY = (float) (box.minY - baseY);
        final float minZ = (float) (box.minZ - baseZ);
        final float maxX = (float) (box.maxX - baseX);
        final float maxY = (float) (box.maxY - baseY);
        final float maxZ = (float) (box.maxZ - baseZ);
        final int red = color.getRed();
        final int green = color.getGreen();
        final int blue = color.getBlue();

        final BufferBuilder bufferBuilder = mask ? quadBufferBuilderMasked : quadBufferBuilderNonMasked;

        if (!sameX && !sameZ) {
            if (mask) quadMaskedCount++;
            else quadNonMaskedCount++;
            bufferBuilder.vertex(minX, minY, minZ).color(red, green, blue, alpha);
            bufferBuilder.vertex(maxX, minY, minZ).color(red, green, blue, alpha);
            bufferBuilder.vertex(maxX, minY, maxZ).color(red, green, blue, alpha);
            bufferBuilder.vertex(minX, minY, maxZ).color(red, green, blue, alpha);
            if (!sameY) {
                if (mask) quadMaskedCount++;
                else quadNonMaskedCount++;
                bufferBuilder.vertex(minX, maxY, minZ).color(red, green, blue, alpha);
                bufferBuilder.vertex(minX, maxY, maxZ).color(red, green, blue, alpha);
                bufferBuilder.vertex(maxX, maxY, maxZ).color(red, green, blue, alpha);
                bufferBuilder.vertex(maxX, maxY, minZ).color(red, green, blue, alpha);
            }
        }

        if (!sameX && !sameY) {
            if (mask) quadMaskedCount++;
            else quadNonMaskedCount++;
            bufferBuilder.vertex(minX, minY, minZ).color(red, green, blue, alpha);
            bufferBuilder.vertex(minX, maxY, minZ).color(red, green, blue, alpha);
            bufferBuilder.vertex(maxX, maxY, minZ).color(red, green, blue, alpha);
            bufferBuilder.vertex(maxX, minY, minZ).color(red, green, blue, alpha);
            if (!sameZ) {
                if (mask) quadMaskedCount++;
                else quadNonMaskedCount++;
                bufferBuilder.vertex(minX, minY, maxZ).color(red, green, blue, alpha);
                bufferBuilder.vertex(maxX, minY, maxZ).color(red, green, blue, alpha);
                bufferBuilder.vertex(maxX, maxY, maxZ).color(red, green, blue, alpha);
                bufferBuilder.vertex(minX, maxY, maxZ).color(red, green, blue, alpha);
            }
        }

        if (!sameY && !sameZ) {
            if (mask) quadMaskedCount++;
            else quadNonMaskedCount++;
            bufferBuilder.vertex(minX, minY, minZ).color(red, green, blue, alpha);
            bufferBuilder.vertex(minX, minY, maxZ).color(red, green, blue, alpha);
            bufferBuilder.vertex(minX, maxY, maxZ).color(red, green, blue, alpha);
            bufferBuilder.vertex(minX, maxY, minZ).color(red, green, blue, alpha);
            if (!sameX) {
                if (mask) quadMaskedCount++;
                else quadNonMaskedCount++;
                bufferBuilder.vertex(maxX, minY, minZ).color(red, green, blue, alpha);
                bufferBuilder.vertex(maxX, maxY, minZ).color(red, green, blue, alpha);
                bufferBuilder.vertex(maxX, maxY, maxZ).color(red, green, blue, alpha);
                bufferBuilder.vertex(maxX, minY, maxZ).color(red, green, blue, alpha);
            }
        }
    }

    public void drawFilledFace(Point point1, Point point2, Point point3, Point point4, Color color, int alpha, boolean mask) {
        if (mask) quadMaskedCount++;
        else quadNonMaskedCount++;

        final BufferBuilder bufferBuilder = mask ? quadBufferBuilderMasked : quadBufferBuilderNonMasked;

        final float x1 = (float) (point1.getX() - baseX);
        final float y1 = (float) (point1.getY() - baseY);
        final float z1 = (float) (point1.getZ() - baseZ);
        bufferBuilder.vertex(x1, y1, z1).color(color.getRed(), color.getGreen(), color.getBlue(), alpha);

        final float x2 = (float) (point2.getX() - baseX);
        final float y2 = (float) (point2.getY() - baseY);
        final float z2 = (float) (point2.getZ() - baseZ);
        bufferBuilder.vertex(x2, y2, z2).color(color.getRed(), color.getGreen(), color.getBlue(), alpha);

        final float x3 = (float) (point3.getX() - baseX);
        final float y3 = (float) (point3.getY() - baseY);
        final float z3 = (float) (point3.getZ() - baseZ);
        bufferBuilder.vertex(x3, y3, z3).color(color.getRed(), color.getGreen(), color.getBlue(), alpha);

        final float x4 = (float) (point4.getX() - baseX);
        final float y4 = (float) (point4.getY() - baseY);
        final float z4 = (float) (point4.getZ() - baseZ);
        bufferBuilder.vertex(x4, y4, z4).color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    public void drawLine(Point startPoint, Point endPoint, Color color, int alpha) {
        lineCount++;

        lineBufferBuilder
                .vertex((float) (startPoint.getX() - baseX),
                        (float) (startPoint.getY() - baseY),
                        (float) (startPoint.getZ() - baseZ))
                .color(color.getRed(), color.getGreen(), color.getBlue(), alpha)
        ;
        lineBufferBuilder
                .vertex((float) (endPoint.getX() - baseX),
                        (float) (endPoint.getY() - baseY),
                        (float) (endPoint.getZ() - baseZ))
                .color(color.getRed(), color.getGreen(), color.getBlue(), alpha)
        ;
    }

    public void endBatch() {
        List<CompletableFuture<?>> futures = Lists.newArrayListWithExpectedSize(4);

        if (this.quadBufferBuilderNonMasked != null) {
            final BuiltBuffer quadBufferNonMasked = this.quadBufferBuilderNonMasked.endNullable();
            this.quadBufferBuilderNonMasked = null;
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

    public void doDrawing(MatrixStack stack) {
        RenderSystem.assertOnRenderThread();
        handleRenderTask();

        long startTime = System.nanoTime();

        final MatrixStack.Entry top = stack.peek();

        try {
            RenderSystem.getModelViewStack().pushMatrix();
//            RenderSystem.getModelViewStack().translate(top.getPositionMatrix().getTranslation(new Vector3f()));
            RenderSystem.getModelViewStack().mulAffine(top.getPositionMatrix());

            if (lineRenderInfo.isUploaded()) lineRenderInfo.draw();
            if (quadRenderInfo.isUploaded()) quadRenderInfo.draw();
            if (maskedQuadRenderInfo.isUploaded()) maskedQuadRenderInfo.draw();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            RenderSystem.getModelViewStack().popMatrix();
        }

        this.lastRenderDurationNanos = System.nanoTime() - startTime;
    }

    public String debugString() {
        return String.format("Statistics: Filled faces: %d+%d Lines: %d @ (%.2fms Build, %.2fms Draw)",
                quadMaskedCount, quadNonMaskedCount, lineCount,
                lastBuildDurationNanos / 1_000_000.0, lastRenderDurationNanos / 1_000_000.0);
    }

    public void postRenderTask(Runnable task) {
        if (RenderSystem.isOnRenderThread()) {
            task.run();
        } else {
            System.out.println("queueing");
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
