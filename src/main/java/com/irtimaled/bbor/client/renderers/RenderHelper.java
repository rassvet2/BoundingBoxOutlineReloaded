package com.irtimaled.bbor.client.renderers;

import com.irtimaled.bbor.client.config.ConfigManager;
import com.irtimaled.bbor.mixin.client.renderer.access.IRenderLayer;
import com.irtimaled.bbor.mixin.client.renderer.access.IRenderPhase;
import com.irtimaled.bbor.mixin.client.renderer.access.IRenderPipelines;
import com.mojang.blaze3d.buffers.BufferType;
import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.*;

import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.apache.commons.io.FileUtils;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

public class RenderHelper {
    private  static final int DEFAULT_SIZE = 4 * 1024 * 1024;
     private static final RenderPipeline.Snippet SNIPPET = RenderPipeline.builder(IRenderPipelines.POSITION_COLOR_SNIPPET())
         .withCull(false)
         .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
         .withDepthWrite(true)
         .buildSnippet();

     public static final RenderLayer DEBUG_LINES = IRenderLayer.of(
             "bbor_lines", DEFAULT_SIZE, false, false,
             IRenderPipelines.register(
                     RenderPipeline.builder(SNIPPET).withLocation(Identifier.of("bbor", "lines"))
                             .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                             .build()
             ),
             RenderLayer.MultiPhaseParameters.builder()
                     .lineWidth(IRenderPhase.FULL_LINE_WIDTH())
                     .build(false)
     );

    public static final RenderLayer DEBUG_LINES_NO_DEPTH_TEST = IRenderLayer.of(
             "bbor_lines_no_depth_test", DEFAULT_SIZE, false, false,
             IRenderPipelines.register(
                     RenderPipeline.builder(SNIPPET).withLocation(Identifier.of("bbor", "lines_no_depth_test"))
                             .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                             .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                             .build()
             ),
             RenderLayer.MultiPhaseParameters.builder()
                     .lineWidth(IRenderPhase.FULL_LINE_WIDTH())
                     .build(false)
     );


    public static final RenderLayer DEBUG_QUADS = IRenderLayer.of(
            "bbor_quads", DEFAULT_SIZE, false,  true,
            IRenderPipelines.register(
                    RenderPipeline.builder(SNIPPET).withLocation(Identifier.of("bbor", "quads"))
                            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                            .withDepthWrite(false)
                            .build()
            ),
            RenderLayer.MultiPhaseParameters.builder()
                    .target(IRenderPhase.TRANSLUCENT_TARGET())
                    .build(false)
    );

    public static final RenderLayer DEBUG_QUADS_NO_DEPTH_TEST = IRenderLayer.of(
            "bbor_quads_no_depth_test", DEFAULT_SIZE, false, true,
            IRenderPipelines.register(
                    RenderPipeline.builder(SNIPPET).withLocation(Identifier.of("bbor", "quads_no_depth_test"))
                            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                            .withDepthWrite(false)
                            .build()
            ),
            RenderLayer.MultiPhaseParameters.builder()
                    .target(IRenderPhase.TRANSLUCENT_TARGET())
                    .build(false)
    );

    public static final List<RenderLayer> LAYER_LIST = List.of(
            DEBUG_LINES,
            DEBUG_LINES_NO_DEPTH_TEST,
            DEBUG_QUADS,
            DEBUG_QUADS_NO_DEPTH_TEST
    );

    public static RenderLayer getDebugLines() {
        return ConfigManager.alwaysVisible.get() ? DEBUG_LINES_NO_DEPTH_TEST : DEBUG_LINES;
    }

    public static RenderLayer getDebugQuads() {
        return ConfigManager.alwaysVisible.get() ? DEBUG_QUADS_NO_DEPTH_TEST : DEBUG_QUADS;
    }

    public static GpuBuffer createBuffer(RenderLayer layer, int size) {
        return RenderSystem.getDevice().createBuffer(
                () -> "IndexBuffer for " + layer.getVertexFormat(),
                BufferType.VERTICES,
                BufferUsage.DYNAMIC_WRITE,
                size
        );
    }
    public static GpuBuffer createIndexBuffer(RenderLayer layer, int size) {
        return RenderSystem.getDevice().createBuffer(
                () -> "IndexBuffer for " + layer.getVertexFormat(),
                BufferType.INDICES,
                BufferUsage.DYNAMIC_WRITE,
                size
        );
    }

    public static final class RenderLayerHelper implements Closeable {
        private final Supplier<RenderLayer> layer;
        private RenderLayer currentLayer;
        private GpuBuffer vertexBuffer;
        private GpuBuffer indexBuffer;
        private VertexFormat.IndexType indexType;
        private int indexCount;

        private boolean uploaded = false;
        private boolean shouldIndexBufferClose = false;

        public RenderLayerHelper(Supplier<RenderLayer> layer) {
            this.layer = layer;
            this.currentLayer = layer.get();
        }

        public void upload(final BuiltBuffer buffer) {
            RenderSystem.assertOnRenderThread();
            RenderLayer layer = this.layer.get();
            if (layer != this.currentLayer) this.close();
            this.currentLayer = layer;

            BuiltBuffer.DrawParameters param = buffer.getDrawParameters();

            ByteBuffer vertexBuffer = buffer.getBuffer();
            if (this.vertexBuffer == null || this.vertexBuffer.size() < vertexBuffer.remaining()) {
                if (this.vertexBuffer != null) this.vertexBuffer.close();
                this.vertexBuffer = RenderHelper.createBuffer(layer, (int) (vertexBuffer.remaining() * 1.5));
            }

            RenderSystem.getDevice().createCommandEncoder()
                    .writeToBuffer(this.vertexBuffer, vertexBuffer, 0);

            if (buffer.getSortedBuffer() == null) {
                // don't close shared buffer
                if (this.indexBuffer != null && this.shouldIndexBufferClose) this.indexBuffer.close();
                RenderSystem.ShapeIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(param.mode());
                this.indexBuffer = shapeIndexBuffer.getIndexBuffer(param.indexCount());
                this.indexType = shapeIndexBuffer.getIndexType();
                this.shouldIndexBufferClose = false;
            } else {
                ByteBuffer indexBuffer = buffer.getSortedBuffer();
                if (this.indexBuffer == null || this.indexBuffer.size() < indexBuffer.remaining()) {
                    if (this.indexBuffer != null && this.shouldIndexBufferClose) this.indexBuffer.close();
                    this.indexBuffer = RenderHelper.createIndexBuffer(layer, (int) (indexBuffer.remaining() * 1.5));
                    this.shouldIndexBufferClose = true;
                }

                RenderSystem.getDevice().createCommandEncoder()
                        .writeToBuffer(this.indexBuffer, indexBuffer, 0);
                this.indexType = param.indexType();
            }

            this.indexCount = param.indexCount();
            this.uploaded = true;
        }

        public void draw() {
            RenderSystem.assertOnRenderThread();
            if (!uploaded) throw new IllegalStateException("Not uploaded");

            currentLayer.startDrawing();

            Framebuffer framebuffer = currentLayer.getTarget();

            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    framebuffer.getColorAttachment(),
                    OptionalInt.empty(),
                    framebuffer.useDepthAttachment ? framebuffer.getDepthAttachment() : null,
                    OptionalDouble.empty()
            )) {
                pass.setPipeline(currentLayer.getPipeline());
                pass.setVertexBuffer(0, vertexBuffer);

                if (RenderSystem.SCISSOR_STATE.isEnabled()) {
                    pass.enableScissor(RenderSystem.SCISSOR_STATE);
                }

//                for (int i = 0; i < 12; i++) {
//                    GpuTexture gpuTexture = RenderSystem.getShaderTexture(i);
//                    if (gpuTexture != null) {
//                        pass.bindSampler("Sampler" + i, gpuTexture);
//                    }
//                }

                pass.setIndexBuffer(indexBuffer, indexType);
                pass.drawIndexed(0, indexCount);
            }

            currentLayer.endDrawing();
        }

        public void clear() {
            this.uploaded = false;
        }

        public boolean isUploaded() {
            return uploaded;
        }

        @Override
        public void close() {
            if (this.vertexBuffer != null) this.vertexBuffer.close();
            if (this.indexBuffer != null && this.shouldIndexBufferClose) this.indexBuffer.close();
            clear();
            vertexBuffer = null;
            indexBuffer = null;
        }

        public RenderLayer getLayer() {
            return currentLayer;
        }

        public String debugString() {
            if (this.vertexBuffer == null && this.indexBuffer == null && this.indexCount == 0) return "unallocated";
            return String.format("VB: %s, IB: %s, C: %d",
                    (this.vertexBuffer != null ? FileUtils.byteCountToDisplaySize(this.vertexBuffer.size()) : "null"),
                    (this.indexBuffer != null ? FileUtils.byteCountToDisplaySize(this.indexBuffer.size()) : "null"),
                    this.indexCount);
        }
    }

    public static final class Allocators implements Closeable {
        private final Map<RenderLayer, BufferAllocator> allocators = Util.make(new Reference2ObjectArrayMap<>(4), map -> {
            for (RenderLayer renderLayer : RenderHelper.LAYER_LIST) {
                map.put(renderLayer, new BufferAllocator(renderLayer.getExpectedBufferSize()));
            }
        });

        public BufferAllocator getAllocator(RenderLayer layer) {
            return this.allocators.get(layer);
        }
        public BufferBuilder getBufferBuilder(RenderLayer layer) {
            return new BufferBuilder(getAllocator(layer), layer.getDrawMode(), layer.getVertexFormat());
        }

        public void clear() {
            this.allocators.values().forEach(BufferAllocator::clear);
        }

        public void reset() {
            this.allocators.values().forEach(BufferAllocator::reset);
        }

        public void close() {
            this.allocators.values().forEach(BufferAllocator::close);
        }
    }
}
