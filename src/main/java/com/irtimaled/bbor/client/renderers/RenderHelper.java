package com.irtimaled.bbor.client.renderers;

import com.irtimaled.bbor.mixin.client.renderer.access.IMultiPhaseParametersBuilder;
import com.irtimaled.bbor.mixin.client.renderer.access.IRenderLayer;
import com.irtimaled.bbor.mixin.client.renderer.access.IRenderPipelines;
import com.mojang.blaze3d.buffers.BufferType;
import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormats;

import net.minecraft.util.Identifier;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class RenderHelper {
    private  static final int DEFAULT_SIZE = 4 * 1024 * 1024;
     private static final RenderPipeline.Snippet SNIPPET = RenderPipeline.builder(IRenderPipelines.POSITION_COLOR_SNIPPET())
         .withCull(false)
         .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
         .withDepthWrite(true)
         .buildSnippet();

     public static final RenderLayer DEBUG_LINES = IRenderLayer.of(
             "bbor_lines", DEFAULT_SIZE, false,  false,
             IRenderPipelines.register(
                     RenderPipeline.builder(SNIPPET).withLocation(Identifier.of("bbor", "lines"))
                             .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                             .build()
             ),
             builder(builder(RenderLayer.MultiPhaseParameters.builder())
                     .lineWidth0(new RenderPhase.LineWidth(OptionalDouble.of(1.0))))
                     .build0(false)
     );

     public static final RenderLayer DEBUG_LINES_NO_DEPTH_TEST = IRenderLayer.of(
             "bbor_lines_no_depth_test", DEFAULT_SIZE, false, false,
             IRenderPipelines.register(
                     RenderPipeline.builder(SNIPPET).withLocation(Identifier.of("bbor", "lines_no_depth_test"))
                             .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                             .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                             .build()
             ),
             builder(builder(RenderLayer.MultiPhaseParameters.builder())
                     .lineWidth0(new RenderPhase.LineWidth(OptionalDouble.of(1.0))))
                     .build0(false)
     );


    public static final RenderLayer DEBUG_QUADS = IRenderLayer.of(
            "bbor_quads", DEFAULT_SIZE, false,  true,
            IRenderPipelines.register(
                    RenderPipeline.builder(SNIPPET).withLocation(Identifier.of("bbor", "quads"))
                            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                            .build()
            ),
            builder(RenderLayer.MultiPhaseParameters.builder()).build0(false)
    );

    public static final RenderLayer DEBUG_QUADS_NO_DEPTH_TEST = IRenderLayer.of(
            "bbor_quads_no_depth_test", DEFAULT_SIZE, false, true,
            IRenderPipelines.register(
                    RenderPipeline.builder(SNIPPET).withLocation(Identifier.of("bbor", "quads_no_depth_test"))
                            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                            .build()
            ),
            builder(RenderLayer.MultiPhaseParameters.builder()).build0(false)
    );

    private static IMultiPhaseParametersBuilder builder(RenderLayer.MultiPhaseParameters.Builder builder) {
        return (IMultiPhaseParametersBuilder) builder;
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
        private final RenderLayer layer;
        private GpuBuffer vertexBuffer;
        private GpuBuffer indexBuffer;
        private VertexFormat.IndexType indexType;
        private int indexCount;
        private boolean uploaded = false;

        public RenderLayerHelper(
                RenderLayer layer,
                GpuBuffer vertexBuffer,
                GpuBuffer indexBuffer
        ) {
            this.layer = layer;
            this.vertexBuffer = vertexBuffer;
            this.indexBuffer = indexBuffer;
        }

        public RenderLayerHelper(RenderLayer layer) {
            this(layer, null, null);
        }

        public void upload(final BuiltBuffer buffer) {
            RenderSystem.assertOnRenderThread();

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
//                if (this.indexBuffer != null) this.indexBuffer.close();
                RenderSystem.ShapeIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(param.mode());
                this.indexBuffer = shapeIndexBuffer.getIndexBuffer(param.indexCount());
                this.indexType = shapeIndexBuffer.getIndexType();
            } else {
                ByteBuffer indexBuffer = buffer.getSortedBuffer();
                if (this.indexBuffer == null || this.indexBuffer.size() < indexBuffer.remaining()) {
                    if (this.indexBuffer != null) this.indexBuffer.close();
                    this.indexBuffer = RenderHelper.createIndexBuffer(layer, (int) (indexBuffer.remaining() * 1.5));
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

            layer.startDrawing();

            Framebuffer framebuffer = layer.getTarget();

            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    framebuffer.getColorAttachment(),
                    OptionalInt.empty(),
                    framebuffer.useDepthAttachment ? framebuffer.getDepthAttachment() : null,
                    OptionalDouble.empty()
            )) {
                pass.setPipeline(layer.getPipeline());
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

            layer.endDrawing();
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
            if (this.indexBuffer != null) this.indexBuffer.close();
            clear();
            vertexBuffer = null;
            indexBuffer = null;
        }
    }
}
