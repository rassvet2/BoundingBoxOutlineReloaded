package com.irtimaled.bbor.client.renderers;

import com.irtimaled.bbor.client.Camera;
import com.irtimaled.bbor.client.models.Point;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.GlUsage;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

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

    private boolean isFreshBuffers = true;
    private VertexBuffer quadBufferNonMaskedUploaded = new VertexBuffer(GlUsage.DYNAMIC_WRITE);
    private boolean quadBufferNonMaskedUploadedEmpty = true;
    private VertexBuffer quadBufferMaskedUploaded = new VertexBuffer(GlUsage.DYNAMIC_WRITE);
    private boolean quadBufferMaskedUploadedEmpty = true;
    private VertexBuffer lineBufferUploaded = new VertexBuffer(GlUsage.DYNAMIC_WRITE);
    private boolean lineBufferUploadedEmpty = true;

    private long quadNonMaskedCount;
    private long quadMaskedCount;
    private long lineCount;

    private long lastBuildStartTime = System.nanoTime();
    private long lastBuildDurationNanos;
    private long lastRenderDurationNanos;

    private volatile double baseX;
    private volatile double baseY;
    private volatile double baseZ;

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
        if (!isFreshBuffers) {
            this.lineBufferUploaded.close();
            this.quadBufferMaskedUploaded.close();
            this.quadBufferNonMaskedUploaded.close();
            this.lineBufferUploaded = new VertexBuffer(GlUsage.DYNAMIC_WRITE);
            this.quadBufferMaskedUploaded = new VertexBuffer(GlUsage.DYNAMIC_WRITE);
            this.quadBufferNonMaskedUploaded = new VertexBuffer(GlUsage.DYNAMIC_WRITE);
        }
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
        isFreshBuffers = false;

        CompletableFuture<?>[] futures = new CompletableFuture[3];

        final Executor executor = command -> {
            if (RenderSystem.isOnRenderThread()) command.run();
            else RenderSystem.recordRenderCall(command::run);
        };

        final BuiltBuffer quadBufferMasked = this.quadBufferBuilderMasked.endNullable();
        quadBufferMaskedUploadedEmpty = quadBufferMasked == null;
        futures[0] = CompletableFuture.runAsync(() -> {
            if (!quadBufferMaskedUploadedEmpty) {
                quadBufferMaskedUploaded.bind();
                quadBufferMaskedUploaded.upload(quadBufferMasked);
                VertexBuffer.unbind();
            }
        }, executor);

        final BuiltBuffer quadBufferNonMasked = this.quadBufferBuilderNonMasked.endNullable();
        quadBufferNonMaskedUploadedEmpty = quadBufferNonMasked == null;
        futures[1] = CompletableFuture.runAsync(() -> {
            if (!quadBufferNonMaskedUploadedEmpty) {
                quadBufferNonMaskedUploaded.bind();
                quadBufferNonMaskedUploaded.upload(quadBufferNonMasked);
                VertexBuffer.unbind();
            }
        }, executor);

        final BuiltBuffer lineBuffer = this.lineBufferBuilder.endNullable();
        lineBufferUploadedEmpty = lineBuffer == null;
        futures[2] = CompletableFuture.runAsync(() -> {
            if (!lineBufferUploadedEmpty) {
                lineBufferUploaded.bind();
                lineBufferUploaded.upload(lineBuffer);
                VertexBuffer.unbind();
            }
        }, executor);

        CompletableFuture.allOf(futures).join();
        lastBuildDurationNanos = System.nanoTime() - lastBuildStartTime;
    }

    public void doDrawing(MatrixStack stack) {
        long startTime = System.nanoTime();

        final MatrixStack.Entry top = stack.peek();

        RenderSystem.depthMask(true);
        if (!lineBufferUploadedEmpty) {
            lineBufferUploaded.bind();
            lineBufferUploaded.draw(top.getPositionMatrix(), RenderSystem.getProjectionMatrix(), RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR));
        }
        if (!quadBufferMaskedUploadedEmpty) {
            quadBufferMaskedUploaded.bind();
            quadBufferMaskedUploaded.draw(top.getPositionMatrix(), RenderSystem.getProjectionMatrix(), RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR));
        }

        RenderSystem.depthMask(false);
        if (!quadBufferNonMaskedUploadedEmpty) {
            quadBufferNonMaskedUploaded.bind();
            quadBufferNonMaskedUploaded.draw(top.getPositionMatrix(), RenderSystem.getProjectionMatrix(), RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR));
        }

        VertexBuffer.unbind();
        RenderSystem.depthMask(true);

        this.lastRenderDurationNanos = System.nanoTime() - startTime;
    }

    public String debugString() {
        return String.format("Statistics: Filled faces: %d+%d Lines: %d @ (%.2fms Build, %.2fms Draw)",
                quadMaskedCount, quadNonMaskedCount, lineCount,
                lastBuildDurationNanos / 1_000_000.0, lastRenderDurationNanos / 1_000_000.0);
    }

}
