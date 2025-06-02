package com.irtimaled.bbor.client.gui;

import com.irtimaled.bbor.common.MathHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.VertexFormat;

public class ControlList implements IControlSet {
    private static final Identifier OPTIONS_BACKGROUND_TEXTURE = Identifier.tryParse("bbor:textures/gui/options_background.png");

    public static final int CONTROLS_WIDTH = 310;
    protected static final int PADDING = 8;

    protected final int listLeft;
    protected final List<ControlListEntry> entries = new ArrayList<>();
    private final int scrollBarLeft;
    private final int listHeight;
    private final int width;
    private final int height;
    private final int top;
    private final int bottom;

    protected int contentHeight = PADDING;
    private double amountScrolled;
    private boolean clickedScrollbar;
    private IControl focused;
    private boolean isDragging;

    private boolean isFocused;

    ControlList(int width, int height, int top, int bottom) {
        this.width = width;
        this.scrollBarLeft = width - 6;
        this.height = height;
        this.top = top;
        this.bottom = bottom;
        this.listHeight = bottom - top;
        this.listLeft = width / 2 - CONTROLS_WIDTH / 2;
    }

    void add(ControlListEntry entry) {
        entry.index = entries.size();
        addEntry(entry);
    }

    private void addEntry(ControlListEntry entry) {
        this.entries.add(entry);
        this.contentHeight += entry.getControlHeight();
    }

    public void filter(String lowerValue) {
        int height = 0;

        for (ControlListEntry entry : entries) {
            entry.filter(lowerValue);
            if (entry.isVisible()) {
                height += entry.getControlHeight();
            } else if (entry == focused) {
                focused = null;
            }
        }
        this.contentHeight = height + PADDING;
    }

    void close() {
        this.entries.forEach(ControlListEntry::close);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.clickedScrollbar = button == 0 && mouseX >= (double) this.scrollBarLeft;
        return isMouseOver(mouseX, mouseY) &&
                (IControlSet.super.mouseClicked(mouseX, mouseY, button) ||
                        this.clickedScrollbar);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseY >= (double) this.top && mouseY <= (double) this.bottom;
    }

    @Override
    public void setFocused(boolean focused) {
        this.isFocused = focused;
    }

    @Override
    public boolean isFocused() {
        return this.isFocused;
    }

    // TODO
//    @Override
//    public boolean changeFocus(boolean moveForward) {
//        boolean newControlFocused = IControlSet.super.changeFocus(moveForward);
//        if (newControlFocused) {
//            this.ensureVisible((ControlListEntry) this.getFocused());
//        }
//
//        return newControlFocused;
//    }

    private void ensureVisible(ControlListEntry control) {
        int controlTop = control.getControlTop();
        int controlHeight = control.getControlHeight();
        int distanceAboveTop = this.top - controlTop;
        if (distanceAboveTop > 0) {
            this.amountScrolled -= Math.max(controlHeight, distanceAboveTop + PADDING);
            return;
        }

        int distanceBelowBottom = controlTop + controlHeight - this.bottom;
        if (distanceBelowBottom > 0) {
            this.amountScrolled += Math.max(controlHeight, distanceBelowBottom + PADDING);
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double p_mouseDragged_6_, double p_mouseDragged_8_) {
        if (IControlSet.super.mouseDragged(mouseX, mouseY, button, p_mouseDragged_6_, p_mouseDragged_8_)) {
            return true;
        }
        if (button == 0 && this.clickedScrollbar) {
            if (mouseY < (double) this.top) {
                this.amountScrolled = 0.0D;
            } else if (mouseY > (double) this.bottom) {
                this.amountScrolled = this.getMaxScroll();
            } else {
                double maxScroll = this.getMaxScroll();
                if (maxScroll < 1.0D) {
                    maxScroll = 1.0D;
                }

                double amountScrolled = maxScroll / (double) (this.listHeight - getScrollBarHeight());
                if (amountScrolled < 1.0D) {
                    amountScrolled = 1.0D;
                }

                this.amountScrolled += p_mouseDragged_8_ * amountScrolled;
            }

            return true;
        }
        return false;
    }

    private int getMaxScroll() {
        return Math.max(0, this.contentHeight - (this.listHeight - 4));
    }

    private int getScrollBarHeight() {
        return MathHelper.clamp((int) ((float) (this.listHeight * this.listHeight) / (float) this.contentHeight),
                32,
                this.listHeight - PADDING);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollAmount, double verticalAmount) {
        this.amountScrolled -= scrollAmount * 10;
        return true;
    }

    public void render(DrawContext ctx, int mouseX, int mouseY) {
        this.amountScrolled = MathHelper.clamp(this.amountScrolled, 0.0D, this.getMaxScroll());

        int listTop = this.top + PADDING - (int) this.amountScrolled;

        Screen.renderBackgroundTexture(ctx, MinecraftClient.getInstance().world != null ? Screen.INWORLD_MENU_BACKGROUND_TEXTURE : Screen.MENU_BACKGROUND_TEXTURE, 0, top, 0.0F, 0.0F, width, height);
        ctx.enableScissor(0, this.top, this.width, this.bottom);
        drawEntries(ctx, mouseX, mouseY, listTop);
        ctx.disableScissor();

        this.overlayBackground(0, this.top);
        this.overlayBackground(this.bottom, this.height);
        drawOverlayShadows();

        int maxScroll = this.getMaxScroll();
        if (maxScroll > 0) {
            drawScrollBar(maxScroll);
        }
    }

    private void drawEntries(DrawContext ctx, int mouseX, int mouseY, int top) {
        for (ControlListEntry entry : this.entries) {
            if (!entry.isVisible()) continue;

            entry.setX(this.listLeft);
            entry.setY(top);

            int height = entry.getControlHeight();
            int bottom = top + height;
            if (bottom >= this.top && top <= this.bottom) {
                drawEntry(ctx, mouseX, mouseY, top, entry, height);
            } else {
                entry.update();
            }
            top = bottom;
        }
    }

    protected void drawEntry(DrawContext ctx, int mouseX, int mouseY, int top, ControlListEntry entry, int height) {
        entry.render(ctx, mouseX, mouseY);
    }

    private void overlayBackground(int top, int bottom) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        bufferBuilder
                .vertex(0, bottom, 100F)
                .texture(0.0F, (float) bottom / 32.0F)
                .color(64, 64, 64, 255);
        bufferBuilder
                .vertex(this.width, bottom, 100F)
                .texture((float) this.width / 32.0F, (float) bottom / 32.0F)
                .color(64, 64, 64, 255);
        bufferBuilder
                .vertex(this.width, top, 100F)
                .texture((float) this.width / 32.0F, (float) top / 32.0F)
                .color(64, 64, 64, 255);
        bufferBuilder
                .vertex(0, top, 100F)
                .texture(0.0f, (float) top / 32.0F)
                .color(64, 64, 64, 255);

        RenderLayer.getGuiTexturedOverlay(OPTIONS_BACKGROUND_TEXTURE).draw(bufferBuilder.end());
    }

    private void drawScrollBar(int maxScroll) {
        int scrollBarHeight = this.getScrollBarHeight();
        int scrollBarTop = (int) this.amountScrolled * (this.listHeight - scrollBarHeight) / maxScroll + this.top;
        if (scrollBarTop < this.top) {
            scrollBarTop = this.top;
        }

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        bufferBuilder.vertex(this.scrollBarLeft, this.bottom, 0.0F).color(0, 0, 0, 255);
        bufferBuilder.vertex(this.width, this.bottom, 0.0F).color(0, 0, 0, 255);
        bufferBuilder.vertex(this.width, this.top, 0.0F).color(0, 0, 0, 255);
        bufferBuilder.vertex(this.scrollBarLeft, this.top, 0.0F).color(0, 0, 0, 255);

        bufferBuilder.vertex(this.scrollBarLeft, scrollBarTop + scrollBarHeight, 0.0F).color(128, 128, 128, 255);
        bufferBuilder.vertex(this.width, scrollBarTop + scrollBarHeight, 0.0F).color(128, 128, 128, 255);
        bufferBuilder.vertex(this.width, scrollBarTop, 0.0F).color(128, 128, 128, 255);
        bufferBuilder.vertex(this.scrollBarLeft, scrollBarTop, 0.0F).color(128, 128, 128, 255);

        bufferBuilder.vertex(this.scrollBarLeft, scrollBarTop + scrollBarHeight - 1, 0.0F).color(192, 192, 192, 255);
        bufferBuilder.vertex(this.width - 1, scrollBarTop + scrollBarHeight - 1, 0.0F).color(192, 192, 192, 255);
        bufferBuilder.vertex(this.width - 1, scrollBarTop, 0.0F).color(192, 192, 192, 255);
        bufferBuilder.vertex(this.scrollBarLeft, scrollBarTop, 0.0F).color(192, 192, 192, 255);

        RenderLayer.getGui().draw(bufferBuilder.end());
    }

    private void drawOverlayShadows() {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        bufferBuilder.vertex(0, this.top + 4, 0.0F).color(0, 0, 0, 0);
        bufferBuilder.vertex(this.width, this.top + 4, 0.0F).color(0, 0, 0, 0);
        bufferBuilder.vertex(this.width, this.top, 0.0F).color(0, 0, 0, 255);
        bufferBuilder.vertex(0, this.top, 0.0F).color(0, 0, 0, 255);

        bufferBuilder.vertex(this.width, this.bottom - 4, 0.0F).color(0, 0, 0, 0);
        bufferBuilder.vertex(0, this.bottom - 4, 0.0F).color(0, 0, 0, 0);
        bufferBuilder.vertex(0, this.bottom, 0.0F).color(0, 0, 0, 255);
        bufferBuilder.vertex(this.width, this.bottom, 0.0F).color(0, 0, 0, 255);

        RenderLayer.getGuiOverlay().draw(bufferBuilder.end());
    }

    ControlList section(String title, CreateControl... createControls) {
        this.add(new ControlListSection(title, -1, createControls));
        return this;
    }

    ControlList section(String title, int columnCount, CreateControl... createControls) {
        this.add(new ControlListSection(title, columnCount, createControls));
        return this;
    }

    @Override
    public List<? extends IControl> controls() {
        return entries;
    }

    @Override
    public IControl getFocused() {
        return focused;
    }

    @Override
    public void setFocused(IControl control) {
        this.focused = control;
    }

    @Override
    public boolean isDragging() {
        return isDragging;
    }

    @Override
    public void setDragging(boolean dragging) {
        this.isDragging = dragging;
    }
}
