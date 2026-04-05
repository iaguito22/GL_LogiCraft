package com.gl.logicraft.gui;

import com.gl.logicraft.blockentity.LogicChipBlockEntity;
import com.gl.logicraft.circuit.GuiComponent;
import com.gl.logicraft.circuit.Wire;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client-side circuit editor GUI — a Logisim-style full-screen overlay.
 *
 * Layout:
 *   [0 .. PALETTE_W]     = palette panel
 *   [PALETTE_W .. width] = 30×30 grid
 *
 * Fixed input nodes (left edge of grid):  REDST_IN, IN_0 … IN_3
 * Fixed output nodes (right edge of grid): REDST_OUT, OUT_0 … OUT_3
 */
public class LogicScreen extends HandledScreen<LogicScreenHandler> {

    // -----------------------------------------------------------------------
    // Layout constants
    // -----------------------------------------------------------------------

    private static final int PALETTE_W   = 120;
    private static final int CELL_SIZE   = 20;
    private static final int GRID_COLS   = 30;
    private static final int GRID_ROWS   = 30;
    private static final int PORT_RADIUS = 3; // half-size of port square

    // Colors
    private static final int COL_BG        = 0xFF1A1A2E;
    private static final int COL_PALETTE   = 0xFF16213E;
    private static final int COL_GRID_LINE = 0xFF2A2A4A;
    private static final int COL_GATE      = 0xFF0F3460;
    private static final int COL_GATE_SEL  = 0xFF533483;
    private static final int COL_GATE_BORD = 0xFF7A86D8;
    private static final int COL_PORT_OFF  = 0xFF555555;
    private static final int COL_PORT_ON   = 0xFF44FF44;
    private static final int COL_PORT_ERR  = 0xFFFF4444;
    private static final int COL_WIRE_OFF  = 0xFF888888;
    private static final int COL_WIRE_ON   = 0xFF44FF44;
    private static final int COL_WIRE_ERR  = 0xFFFF4444;
    private static final int COL_TEXT      = 0xFFEEEEEE;
    private static final int COL_BTN       = 0xFF0F3460;
    private static final int COL_BTN_SEL   = 0xFF533483;
    private static final int COL_NODE_BG   = 0xFF0D2137;

    // Palette entries: (label, component type)
    private static final String[][] PALETTE = {
            {"AND",  "and"},
            {"OR",   "or"},
            {"NOT",  "not"},
            {"PASS", "pass"},
    };

    // Fixed node IDs
    private static final String[] INPUT_NODES  = {"REDST_IN", "IN_0", "IN_1", "IN_2", "IN_3"};
    private static final String[] OUTPUT_NODES = {"REDST_OUT", "OUT_0", "OUT_1", "OUT_2", "OUT_3"};
    private static final String[] INPUT_LABELS  = {"redst↓in", "in_1",  "in_2",  "in_3",  "in_4"};
    private static final String[] OUTPUT_LABELS = {"redst↑out","out_1", "out_2", "out_3", "out_4"};

    // -----------------------------------------------------------------------
    // GUI state
    // -----------------------------------------------------------------------

    private String selectedPaletteType = null;  // gate type chosen from palette

    // Wire-drawing drag state
    private boolean  drawingWire    = false;
    private String   wireFromId     = null;
    private int      wireFromPort   = 0;
    private boolean  wireFromOutput = true; // true = dragging from output → input
    private int      wireCurX, wireCurY;    // current mouse pos

    // Grid origin (top-left pixel of cell 0,0)
    private int gridOriginX, gridOriginY;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public LogicScreen(LogicScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
    }

    @Override
    protected void init() {
        this.backgroundWidth  = this.width;
        this.backgroundHeight = this.height;
        this.x = 0;
        this.y = 0;

        // Grid origin: right of palette panel, vertically centered
        int gridPixW = GRID_COLS * CELL_SIZE;
        int gridPixH = GRID_ROWS * CELL_SIZE;
        int availW   = this.width - PALETTE_W;
        this.gridOriginX = PALETTE_W + (availW - gridPixW) / 2;
        this.gridOriginY = (this.height - gridPixH) / 2;
    }

    // -----------------------------------------------------------------------
    // Rendering
    // -----------------------------------------------------------------------

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mx, int my) {
        // Full-screen background
        ctx.fill(0, 0, this.width, this.height, COL_BG);

        // Palette panel
        ctx.fill(0, 0, PALETTE_W, this.height, COL_PALETTE);
        ctx.drawVerticalLine(PALETTE_W - 1, 0, this.height, COL_GATE_BORD);

        // Palette title
        ctx.drawText(textRenderer, Text.literal("Components"), 8, 8, COL_TEXT, false);

        // Palette buttons
        int by = 30;
        for (String[] entry : PALETTE) {
            String label = entry[0];
            String type  = entry[1];
            int bg = type.equals(selectedPaletteType) ? COL_BTN_SEL : COL_BTN;
            ctx.fill(8, by, PALETTE_W - 8, by + 22, bg);
            ctx.drawBorder(8, by, PALETTE_W - 16, 22, COL_GATE_BORD);
            ctx.drawText(textRenderer, Text.literal(label), 14, by + 7, COL_TEXT, false);
            by += 28;
        }

        drawGrid(ctx);
        drawFixedNodes(ctx);
        drawComponents(ctx);
        drawWires(ctx);
        drawActiveWire(ctx, mx, my);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        // Cursor tooltip when palette type is selected
        if (selectedPaletteType != null) {
            ctx.drawText(textRenderer,
                    Text.literal("Click grid to place " + selectedPaletteType.toUpperCase()),
                    PALETTE_W + 4, 4, 0xFFAAAAAA, false);
        }
    }

    private void drawGrid(DrawContext ctx) {
        int w = GRID_COLS * CELL_SIZE;
        int h = GRID_ROWS * CELL_SIZE;

        // Grid background
        ctx.fill(gridOriginX, gridOriginY, gridOriginX + w, gridOriginY + h, 0xFF111122);
        ctx.drawBorder(gridOriginX, gridOriginY, w, h, COL_GATE_BORD);

        // Grid lines
        for (int col = 1; col < GRID_COLS; col++) {
            int px = gridOriginX + col * CELL_SIZE;
            ctx.drawVerticalLine(px, gridOriginY, gridOriginY + h, COL_GRID_LINE);
        }
        for (int row = 1; row < GRID_ROWS; row++) {
            int py = gridOriginY + row * CELL_SIZE;
            ctx.drawHorizontalLine(gridOriginX, gridOriginX + w, py, COL_GRID_LINE);
        }
    }

    private void drawFixedNodes(DrawContext ctx) {
        // Input nodes — left edge, rows 3,6,9,12,15 (spaced out)
        for (int i = 0; i < INPUT_NODES.length; i++) {
            int row = 2 + i * 4;
            int px  = gridOriginX - 2;
            int py  = gridOriginY + row * CELL_SIZE;
            // Small labeled box
            ctx.fill(px - 52, py + 2, px, py + CELL_SIZE - 2, COL_NODE_BG);
            ctx.drawBorder(px - 52, py + 2, 52, CELL_SIZE - 4, COL_GATE_BORD);
            ctx.drawText(textRenderer, Text.literal(INPUT_LABELS[i]), px - 50, py + 7, COL_TEXT, false);
            // Output port dot (right side of node box, connects into grid)
            drawPort(ctx, px, py + CELL_SIZE / 2, COL_PORT_OFF);
        }
        // Output nodes — right edge
        int rightX = gridOriginX + GRID_COLS * CELL_SIZE;
        for (int i = 0; i < OUTPUT_NODES.length; i++) {
            int row = 2 + i * 4;
            int px  = rightX + 2;
            int py  = gridOriginY + row * CELL_SIZE;
            ctx.fill(px, py + 2, px + 52, py + CELL_SIZE - 2, COL_NODE_BG);
            ctx.drawBorder(px, py + 2, 52, CELL_SIZE - 4, COL_GATE_BORD);
            ctx.drawText(textRenderer, Text.literal(OUTPUT_LABELS[i]), px + 4, py + 7, COL_TEXT, false);
            // Input port dot
            drawPort(ctx, px, py + CELL_SIZE / 2, COL_PORT_OFF);
        }
    }

    private void drawComponents(DrawContext ctx) {
        for (GuiComponent gc : handler.getGuiComponents()) {
            int px = gridOriginX + gc.gridX * CELL_SIZE;
            int py = gridOriginY + gc.gridY * CELL_SIZE;

            // Gate body
            ctx.fill(px + 1, py + 1, px + CELL_SIZE - 1, py + CELL_SIZE - 1, COL_GATE);
            ctx.drawBorder(px + 1, py + 1, CELL_SIZE - 2, CELL_SIZE - 2, COL_GATE_BORD);

            // Label
            String label = gc.getLabel();
            int lx = px + (CELL_SIZE - textRenderer.getWidth(label)) / 2;
            int ly = py + (CELL_SIZE - 8) / 2;
            ctx.drawText(textRenderer, Text.literal(label), lx, ly, COL_TEXT, false);

            // Input ports (left side)
            int inCount = gc.getInputCount();
            for (int i = 0; i < inCount; i++) {
                int portY = py + (i + 1) * CELL_SIZE / (inCount + 1);
                drawPort(ctx, px + 1, portY, COL_PORT_OFF);
            }
            // Output port (right side, center)
            drawPort(ctx, px + CELL_SIZE - 1, py + CELL_SIZE / 2, COL_PORT_OFF);
        }
    }

    private void drawWires(DrawContext ctx) {
        for (Wire w : handler.getWires()) {
            int[] from = getPortPixel(w.fromId, w.fromPort, true);
            int[] to   = getPortPixel(w.toId,   w.toPort,  false);
            if (from == null || to == null) {
                // Disconnected — red
                continue;
            }
            int col = COL_WIRE_OFF; // TODO: use COL_WIRE_ON when signal is true
            int midX = (from[0] + to[0]) / 2;
            drawWireLine(ctx, from[0], from[1], midX, from[1], col);
            drawWireLine(ctx, midX, from[1], midX, to[1], col);
            drawWireLine(ctx, midX, to[1], to[0], to[1], col);
        }
    }

    private void drawActiveWire(DrawContext ctx, int mx, int my) {
        if (!drawingWire) return;
        int[] from = wireFromOutput
                ? getPortPixel(wireFromId, wireFromPort, true)
                : getPortPixel(wireFromId, wireFromPort, false);
        if (from == null) return;
        drawWireLine(ctx, from[0], from[1], mx, my, COL_WIRE_ERR);
    }

    private void drawWireLine(DrawContext ctx, int x1, int y1, int x2, int y2, int col) {
        if (x1 == x2) {
            ctx.drawVerticalLine(x1, Math.min(y1, y2), Math.max(y1, y2), col);
        } else if (y1 == y2) {
            ctx.drawHorizontalLine(Math.min(x1, x2), Math.max(x1, x2), y1, col);
        } else {
            // diagonal: draw H then V
            ctx.drawHorizontalLine(Math.min(x1, x2), Math.max(x1, x2), y1, col);
            ctx.drawVerticalLine(x2, Math.min(y1, y2), Math.max(y1, y2), col);
        }
    }

    private void drawPort(DrawContext ctx, int cx, int cy, int color) {
        ctx.fill(cx - PORT_RADIUS, cy - PORT_RADIUS,
                 cx + PORT_RADIUS, cy + PORT_RADIUS, color);
    }

    // -----------------------------------------------------------------------
    // Port pixel helper
    // Returns [x, y] pixel center of a port, or null if component not found.
    // isOutput=true gets the output port of the given component.
    // -----------------------------------------------------------------------
    private int[] getPortPixel(String id, int portIndex, boolean isOutput) {
        // Fixed input nodes (they have one output port on the right)
        for (int i = 0; i < INPUT_NODES.length; i++) {
            if (INPUT_NODES[i].equals(id)) {
                int row = 2 + i * 4;
                int px  = gridOriginX - 2;
                int py  = gridOriginY + row * CELL_SIZE + CELL_SIZE / 2;
                return new int[]{px, py};
            }
        }
        // Fixed output nodes (they have one input port on the left)
        int rightX = gridOriginX + GRID_COLS * CELL_SIZE + 2;
        for (int i = 0; i < OUTPUT_NODES.length; i++) {
            if (OUTPUT_NODES[i].equals(id)) {
                int row = 2 + i * 4;
                int py  = gridOriginY + row * CELL_SIZE + CELL_SIZE / 2;
                return new int[]{rightX, py};
            }
        }
        // Gate components
        for (GuiComponent gc : handler.getGuiComponents()) {
            if (!gc.id.equals(id)) continue;
            int px = gridOriginX + gc.gridX * CELL_SIZE;
            int py = gridOriginY + gc.gridY * CELL_SIZE;
            if (isOutput) {
                return new int[]{px + CELL_SIZE - 1, py + CELL_SIZE / 2};
            } else {
                int inCount = gc.getInputCount();
                int portY   = py + (portIndex + 1) * CELL_SIZE / (inCount + 1);
                return new int[]{px + 1, portY};
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Input handling
    // -----------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int imx = (int) mx, imy = (int) my;

        // --- Palette click (left button) ---
        if (button == 0 && imx < PALETTE_W) {
            int by = 30;
            for (String[] entry : PALETTE) {
                if (imy >= by && imy < by + 22) {
                    selectedPaletteType = entry[1].equals(selectedPaletteType) ? null : entry[1];
                    return true;
                }
                by += 28;
            }
            return true;
        }

        // --- Grid area ---
        if (isOnGrid(imx, imy)) {
            int cellX = (imx - gridOriginX) / CELL_SIZE;
            int cellY = (imy - gridOriginY) / CELL_SIZE;

            if (button == 0) {
                // Check output port click (start wire)
                String portHit = hitTestOutputPort(imx, imy);
                if (portHit != null) {
                    String[] parts = portHit.split(":");
                    wireFromId     = parts[0];
                    wireFromPort   = Integer.parseInt(parts[1]);
                    wireFromOutput = true;
                    drawingWire    = true;
                    wireCurX = imx; wireCurY = imy;
                    return true;
                }
                // Place component
                if (selectedPaletteType != null) {
                    if (cellX >= 0 && cellX < GRID_COLS && cellY >= 0 && cellY < GRID_ROWS) {
                        if (handler.getGuiComponents().size() < LogicChipBlockEntity.MAX_COMPONENTS
                                && !cellOccupied(cellX, cellY)) {
                            handler.getGuiComponents().add(
                                    new GuiComponent(UUID.randomUUID().toString(), selectedPaletteType, cellX, cellY));
                        }
                    }
                    return true;
                }
            } else if (button == 1) {
                // Right-click: remove component under cursor
                GuiComponent hit = componentAt(cellX, cellY);
                if (hit != null) {
                    handler.getGuiComponents().remove(hit);
                    handler.getWires().removeIf(w -> w.fromId.equals(hit.id) || w.toId.equals(hit.id));
                    return true;
                }
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (drawingWire) {
            wireCurX = (int) mx; wireCurY = (int) my;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (drawingWire && button == 0) {
            drawingWire = false;
            // Try to connect to an input port under cursor
            String portHit = hitTestInputPort((int) mx, (int) my);
            if (portHit != null) {
                String[] parts = portHit.split(":");
                String toId   = parts[0];
                int    toPort = Integer.parseInt(parts[1]);
                // Don't allow self-connection
                if (!toId.equals(wireFromId)) {
                    // Remove conflicting wire on target input
                    handler.getWires().removeIf(w -> w.toId.equals(toId) && w.toPort == toPort);
                    handler.getWires().add(new Wire(wireFromId, wireFromPort, toId, toPort));
                }
            }
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    // -----------------------------------------------------------------------
    // Close → save to server
    // -----------------------------------------------------------------------

    @Override
    public void close() {
        handler.saveToServer();
        super.close();
    }

    // -----------------------------------------------------------------------
    // Hit-test helpers
    // -----------------------------------------------------------------------

    private boolean isOnGrid(int px, int py) {
        return px >= gridOriginX && px < gridOriginX + GRID_COLS * CELL_SIZE
            && py >= gridOriginY && py < gridOriginY + GRID_ROWS * CELL_SIZE;
    }

    private boolean cellOccupied(int cx, int cy) {
        for (GuiComponent gc : handler.getGuiComponents()) {
            if (gc.gridX == cx && gc.gridY == cy) return true;
        }
        return false;
    }

    private GuiComponent componentAt(int cx, int cy) {
        for (GuiComponent gc : handler.getGuiComponents()) {
            if (gc.gridX == cx && gc.gridY == cy) return gc;
        }
        return null;
    }

    /**
     * Returns "componentId:portIndex" if (px,py) is near an output port, else null.
     */
    private String hitTestOutputPort(int px, int py) {
        // Fixed input nodes export one output port right of their box
        for (int i = 0; i < INPUT_NODES.length; i++) {
            int row = 2 + i * 4;
            int portX = gridOriginX - 2;
            int portY = gridOriginY + row * CELL_SIZE + CELL_SIZE / 2;
            if (Math.abs(px - portX) <= PORT_RADIUS + 2 && Math.abs(py - portY) <= PORT_RADIUS + 2)
                return INPUT_NODES[i] + ":0";
        }
        // Gate output ports
        for (GuiComponent gc : handler.getGuiComponents()) {
            int portX = gridOriginX + gc.gridX * CELL_SIZE + CELL_SIZE - 1;
            int portY = gridOriginY + gc.gridY * CELL_SIZE + CELL_SIZE / 2;
            if (Math.abs(px - portX) <= PORT_RADIUS + 2 && Math.abs(py - portY) <= PORT_RADIUS + 2)
                return gc.id + ":0";
        }
        return null;
    }

    /**
     * Returns "componentId:portIndex" if (px,py) is near an input port, else null.
     */
    private String hitTestInputPort(int px, int py) {
        // Fixed output nodes accept one input port
        int rightX = gridOriginX + GRID_COLS * CELL_SIZE + 2;
        for (int i = 0; i < OUTPUT_NODES.length; i++) {
            int row = 2 + i * 4;
            int portY = gridOriginY + row * CELL_SIZE + CELL_SIZE / 2;
            if (Math.abs(px - rightX) <= PORT_RADIUS + 2 && Math.abs(py - portY) <= PORT_RADIUS + 2)
                return OUTPUT_NODES[i] + ":0";
        }
        // Gate input ports
        for (GuiComponent gc : handler.getGuiComponents()) {
            int inCount = gc.getInputCount();
            for (int i = 0; i < inCount; i++) {
                int portX = gridOriginX + gc.gridX * CELL_SIZE + 1;
                int portY = gridOriginY + gc.gridY * CELL_SIZE + (i + 1) * CELL_SIZE / (inCount + 1);
                if (Math.abs(px - portX) <= PORT_RADIUS + 2 && Math.abs(py - portY) <= PORT_RADIUS + 2)
                    return gc.id + ":" + i;
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Boilerplate
    // -----------------------------------------------------------------------

    @Override
    public boolean shouldPause() { return false; }
}
