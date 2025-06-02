package com.irtimaled.bbor.client.gui;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Colors;

public class SelectableControlList extends ControlList {
    private final int listRight;

    private int selectedElement;
    private boolean isFocused;

    SelectableControlList(int width, int height, int top, int bottom) {
        super(width, height, top, bottom);
        this.listRight = this.listLeft + CONTROLS_WIDTH;
        this.selectedElement = -1;
    }

    @Override
    public void filter(String lowerValue) {
        super.filter(lowerValue);
        if (selectedElement >= 0) {
            if (selectNextVisibleElement(true, selectedElement) ||
                    selectNextVisibleElement(true, 0)) return;
            selectedElement = -1;
        }
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key != 264 && key != 265 && key != 257) return false;

        if (key == 257) {
            if (selectedElement >= 0) {
                getSelectedEntry().done();
                return true;
            }
            return false;
        }

        boolean moveForward = key == 264;
        if (selectedElement >= 0) {
            int newIndex = selectedElement + (moveForward ? 1 : 0);
            if (selectNextVisibleElement(moveForward, newIndex)) return true;
        }
        if (selectNextVisibleElement(moveForward, moveForward ? 0 : entries.size())) return true;

        this.selectedElement = -1;
        return false;
    }

    private boolean selectNextVisibleElement(boolean moveForward, int index) {
        return ListHelper.findNextMatch(entries, index, moveForward, ControlListEntry::isVisible,
                entry -> this.selectedElement = entry.index);
    }

    ControlListEntry getSelectedEntry() {
        return this.selectedElement >= 0 && this.selectedElement < this.entries.size() ?
                this.entries.get(this.selectedElement) :
                null;
    }

    void setSelectedEntry(ControlListEntry entry) {
        if (entry != null) {
            this.selectedElement = entry.index;
        } else {
            this.selectedElement = -1;
        }
    }

    // TODO
//    @Override
//    public boolean changeFocus(boolean moveForward) {
//        if (contentHeight == PADDING) return false;
//
//        isFocused = !isFocused;
//        if (getSelectedEntry() == null && this.entries.size() > 0) {
//            setSelectedEntry(this.entries.get(0));
//        }
//        return isFocused;
//    }

    @Override
    protected void drawEntry(DrawContext ctx, int mouseX, int mouseY, int top, ControlListEntry entry, int height) {
        if (this.selectedElement == entry.index) {
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder bufferBuilder = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

            int color = this.isFocused ? Colors.WHITE : Colors.GRAY;
            var mat = ctx.getMatrices().peek();

            bufferBuilder.vertex(mat, this.listLeft - 2f, (top + height) - 2f, 0f).color(color);
            bufferBuilder.vertex(mat, this.listRight + 2f, (top + height) - 2f, 0f).color(color);
            bufferBuilder.vertex(mat, this.listRight + 2f, top - 2f, 0f).color(color);
            bufferBuilder.vertex(mat, this.listLeft - 2f, top - 2f, 0f).color(color);

            bufferBuilder.vertex(mat, this.listLeft - 1f, (top + height) - 3f, 0f).color(color);
            bufferBuilder.vertex(mat, this.listRight + 1f, (top + height) - 3f, 0f).color(color);
            bufferBuilder.vertex(mat, this.listRight + 1f, top - 1f, 0f).color(color);
            bufferBuilder.vertex(mat, this.listLeft - 1f, top - 1f, 0f).color(color);

            RenderLayer.getGuiOverlay().draw(bufferBuilder.end());
        }
        super.drawEntry(ctx, mouseX, mouseY, top, entry, height);
    }

    @Override
    public void clearFocus() {
        this.isFocused = false;
    }
}
