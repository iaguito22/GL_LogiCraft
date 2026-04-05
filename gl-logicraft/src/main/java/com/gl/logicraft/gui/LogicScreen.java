package com.gl.logicraft.gui;

import com.gl.logicraft.circuit.GuiComponent;
import com.gl.logicraft.circuit.Wire;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side circuit editor GUI — a Logisim-style full-screen overlay.
 */
public class LogicScreen extends HandledScreen<LogicScreenHandler> {

    // -----------------------------------------------------------------------
    // Layout constants
    // -----------------------------------------------------------------------

    private static final int PALETTE_W = 60;
    private static final int CELL_SIZE = 12;
    private static final int GRID_ROWS = 30; // Minimum, grid expands
    private static final int PORT_RADIUS = 2; // Reduced to 2px

    // Colors
    private static final int COL_BG = 0xFF0D0D1A; // Grid background
    private static final int COL_PALETTE = 0xFF1A1A2E;
    private static final int COL_GRID_LINE = 0xFF1E1E3A;
    private static final int COL_GRID_BORD = 0xFF4A4A6A;

    private static final int COL_BTN = 0xFF2A2A3E;
    private static final int COL_BTN_BORD = 0xFF4A9EFF; // highlight color

    // Component Colors
    private static final int COL_COMP_BG = 0xFF2A2A4A;
    private static final int COL_COMP_BORD = 0xFF4A4A6A;

    // Wire & Port Colors
    private static final int COL_WIRE_OFF = 0xFF555555;
    private static final int COL_WIRE_ON = 0xFF00CC44;
    private static final int COL_WIRE_ERR = 0xFFCC2200;
    private static final int COL_WIRE_PEND = 0xFF888888;

    private static final int COL_TEXT = 0xFFFFFFFF;

    // Palette entries: (label, component type)
    private static final String[][] PALETTE = {
            { "AND", "and" },
            { "OR", "or" },
            { "NOT", "not" },
            { "XOR", "XOR" },
            { "PASS", "pass" },
            { "1", "1" },
            { "0", "0" }
    };

    // Fixed node IDs
    private static final String[] INPUT_NODES = { "REDST_IN", "IN_0", "IN_1", "IN_2", "IN_3" };
    private static final String[] OUTPUT_NODES = { "REDST_OUT", "OUT_0", "OUT_1", "OUT_2", "OUT_3" };
    private static final String[] INPUT_LABELS = { "redst_in", "in_1", "in_2", "in_3", "in_4" };
    private static final String[] OUTPUT_LABELS = { "redst_out", "out_1", "out_2", "out_3", "out_4" };

    // -----------------------------------------------------------------------
    // GUI state
    // -----------------------------------------------------------------------

    private String selectedPaletteType = null; // gate type chosen from palette

    // Grid origin (top-left pixel of cell 0,0)
    private int gridOriginX, gridOriginY;

    // Wire drawing state
    private Wire pendingWire = null;
    private int wireCurX, wireCurY;

    private boolean helpOpen = false;

    private GuiComponent draggingComponent = null;
    private int dragOffsetX, dragOffsetY;
    private int dragOriginGridX, dragOriginGridY;

    private int paletteScrollOffset = 0;
    private int helpScrollOffset = 0;

    // Simulation simulation
    private final Map<PortRef, Boolean> signals = new HashMap<>(); // Holds current signals
    private final boolean[] currentOutputs = new boolean[5];
    private boolean[] currentInputs;

    record PortRef(String componentId, int portIndex, boolean isOutput) {
    }

    record PortHit(String componentId, int portIndex, boolean isOutput) {
    }

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public LogicScreen(LogicScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.currentInputs = new boolean[5];
        System.arraycopy(handler.startingInputs, 0, this.currentInputs, 0, 5);
    }

    @Override
    protected void init() {
        super.init();
        // Full screen
        this.backgroundWidth = this.width;
        this.backgroundHeight = this.height;
        this.x = 0;
        this.y = 0;

        // Grid starts exactly where palette ends.
        this.gridOriginX = PALETTE_W;
        int gridPixH = GRID_ROWS * CELL_SIZE;
        this.gridOriginY = (this.height - gridPixH) / 2;
    }

    private int getComponentHeight(String type) {
        if (type == null)
            return 1;
        return switch (type.toLowerCase()) {
            case "and", "or", "xor" -> 2;
            default -> 1;
        };
    }

    // -----------------------------------------------------------------------
    // Simulation
    // -----------------------------------------------------------------------

    private void simulateCircuit() {
        signals.clear();
        for (int i = 0; i < 5; i++) {
            currentOutputs[i] = false;
        }

        // Seed inputs
        for (int i = 0; i < 5; i++) {
            signals.put(new PortRef(INPUT_NODES[i], 0, true), currentInputs[i]);
        }

        // Seed constants first
        for (GuiComponent gc : handler.getGuiComponents()) {
            if ("1".equals(gc.type) || "0".equals(gc.type)) {
                boolean val = evalGate(gc.type, new boolean[0]);
                signals.put(new PortRef(gc.id, 0, true), val);
            }
        }

        int passes = handler.getGuiComponents().size() + 1;
        boolean stabilized = false;

        for (int iter = 0; iter < 20; iter++) {
            boolean changedInIter = false;

            for (GuiComponent gc : handler.getGuiComponents()) {
                int inCount = gc.getInputCount();
                boolean[] gateInputs = new boolean[inCount];

                for (Wire w : handler.getWires()) {
                    if (w.toId.equals(gc.id)) {
                        PortRef srcRef = new PortRef(w.fromId, w.fromPort, true);
                        if (signals.getOrDefault(srcRef, false)) {
                            gateInputs[w.toPort] = true;
                        }
                    }
                }

                boolean result = evalGate(gc.type, gateInputs);
                PortRef outRef = new PortRef(gc.id, 0, true);
                if (signals.getOrDefault(outRef, false) != result) {
                    signals.put(outRef, result);
                    changedInIter = true;
                }
            }

            if (!changedInIter && iter >= passes - 1) {
                stabilized = true;
                break; // stable
            }
        }

        // Update wire signals + invalid looping (cc2200 for remaining/unstable)
        for (Wire w : handler.getWires()) {
            PortRef srcRef = new PortRef(w.fromId, w.fromPort, true);
            w.signal = signals.getOrDefault(srcRef, false);
            // We use 'stabilized' below
        }
        isGlobalStable = stabilized;

        // Write outputs
        for (int i = 0; i < 5; i++) {
            for (Wire w : handler.getWires()) {
                if (w.toId.equals(OUTPUT_NODES[i])) {
                    if (signals.getOrDefault(new PortRef(w.fromId, w.fromPort, true), false)) {
                        currentOutputs[i] = true;
                        break;
                    }
                }
            }
            signals.put(new PortRef(OUTPUT_NODES[i], 0, false), currentOutputs[i]);
        }
    }

    private boolean evalGate(String type, boolean[] inputs) {
        return switch (type.toLowerCase()) {
            case "and" -> {
                boolean r = true;
                for (boolean b : inputs)
                    r &= b;
                yield r;
            }
            case "or" -> {
                boolean r = false;
                for (boolean b : inputs)
                    r |= b;
                yield r;
            }
            case "xor" -> inputs.length >= 2 && (inputs[0] ^ inputs[1]);
            case "not" -> inputs.length > 0 && !inputs[0];
            case "1" -> true;
            case "0" -> false;
            default -> inputs.length > 0 && inputs[0]; // pass
        };
    }

    // -----------------------------------------------------------------------
    // Rendering
    // -----------------------------------------------------------------------

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        simulateCircuit();
        super.render(ctx, mx, my, delta);
        if (selectedPaletteType != null) {
            ctx.drawText(textRenderer, Text.literal("Placing: " + selectedPaletteType.toUpperCase()), PALETTE_W + 4, 4,
                    0xFFAAAAAA, false);
        }

        // Draw Help Button [?]
        int bx = this.width - 20;
        int by = 5;
        ctx.fill(bx, by, bx + 15, by + 15, COL_BTN);
        if (mx >= bx && mx <= bx + 15 && my >= by && my <= by + 15) {
            ctx.drawBorder(bx, by, 15, 15, COL_BTN_BORD);
        }
        ctx.drawCenteredTextWithShadow(textRenderer, "?", bx + 8, by + 4, COL_TEXT);

        if (helpOpen) {
            renderHelpOverlay(ctx);
        }

        if (draggingComponent != null) {
            renderDraggingFeedback(ctx, mx, my);
        }
    }

    private void renderDraggingFeedback(DrawContext ctx, int mx, int my) {
        // 1. Ghost at origin
        int gx = gridOriginX + dragOriginGridX * CELL_SIZE;
        int gy = gridOriginY + dragOriginGridY * CELL_SIZE;
        int gh = getComponentHeight(draggingComponent.type) * CELL_SIZE;
        ctx.drawBorder(gx, gy, CELL_SIZE, gh, 0xFF4A9EFF);

        // 2. Hover highlight
        int cellX = (mx - gridOriginX) / CELL_SIZE;
        int cellY = (my - gridOriginY) / CELL_SIZE;
        if (mx >= PALETTE_W) {
            boolean valid = !cellOccupiedExcluding(cellX, cellY, draggingComponent);
            int hx = gridOriginX + cellX * CELL_SIZE;
            int hy = gridOriginY + cellY * CELL_SIZE;
            ctx.fill(hx, hy, hx + CELL_SIZE, hy + gh, valid ? 0x444A9EFF : 0x44CC2200);
        }

        // 3. Dragged component at 50% opacity
        int dx = mx - dragOffsetX;
        int dy = my - dragOffsetY;

        ctx.getMatrices().push();
        // Shift bit for transparency is tricky with DrawContext.fill,
        // we can use a separate method or just draw with alpha.
        drawComponentAt(ctx, dx, dy, draggingComponent, true);
        ctx.getMatrices().pop();
    }

    private void drawComponentAt(DrawContext ctx, int px, int py, GuiComponent gc, boolean transparent) {
        int pixH = getComponentHeight(gc.type) * CELL_SIZE;
        int bg = transparent ? 0x882A2A4A : COL_COMP_BG;
        int border = transparent ? 0x884A4A6A : COL_COMP_BORD;
        int text = transparent ? 0x88FFFFFF : COL_TEXT;

        ctx.fill(px + 1, py + 1, px + CELL_SIZE - 1, py + pixH - 1, bg);
        ctx.drawBorder(px, py, CELL_SIZE, pixH, border);

        String label = gc.getLabel();
        ctx.getMatrices().push();
        float scale = 0.5f;
        float tw = textRenderer.getWidth(label) * scale;
        float th = 8 * scale;
        float lx = px + (CELL_SIZE - tw) / 2.0f;
        float ly = py + (pixH - th) / 2.0f;
        ctx.getMatrices().translate(lx, ly, 0);
        ctx.getMatrices().scale(scale, scale, 1.0f);
        ctx.drawText(textRenderer, Text.literal(label), 0, 0, text, false);
        ctx.getMatrices().pop();

        // Don't draw ports during drag for simplicity, or draw them dimmed
        if (!transparent) {
            int inCount = getEffectiveInputCount(gc.type);
            for (int i = 0; i < inCount; i++) {
                int portY = py + (i + 1) * pixH / (inCount + 1);
                drawPort(ctx, px, portY, getPortColor(gc.id, i, false));
            }
            drawPort(ctx, px + CELL_SIZE, py + pixH / 2, getPortColor(gc.id, 0, true));
        }
    }

    private void renderHelpOverlay(DrawContext ctx) {
        int midX = this.width / 2;
        int midY = this.height / 2;
        int w = 300, h = 260; // Slightly larger for all info
        int x1 = midX - w / 2, y1 = midY - h / 2;

        ctx.fill(0, 0, this.width, this.height, 0xAA000000); // Full screen dim
        ctx.fill(x1, y1, x1 + w, y1 + h, 0xCC000000);
        ctx.drawBorder(x1, y1, w, h, COL_BTN_BORD);

        // Title (Fixed)
        ctx.drawCenteredTextWithShadow(textRenderer, "LOGIC EDITOR HELP", midX, y1 + 10, 0xFFFFFF00);

        int ox = x1 + 10;
        int oy = y1 + 30 - helpScrollOffset; // All content below title starts here

        ctx.enableScissor(x1, y1 + 25, x1 + w, y1 + h - 20);

        // COMPONENTS
        ctx.drawText(textRenderer, Text.literal("COMPONENTS").formatted(Formatting.BOLD, Formatting.UNDERLINE), ox, oy,
                COL_TEXT, false);
        oy += 10;
        String[][] comps = {
                { "AND", "Output ON only if ALL inputs are ON" },
                { "OR", "Output ON if ANY input is ON" },
                { "NOT", "Inverts the input signal" },
                { "XOR", "Output ON if inputs are DIFFERENT" },
                { "PASS", "Passes the signal through unchanged." },
                { "", "Useful to split one output into multiple inputs." },
                { "1", "Always outputs ON (1)" },
                { "0", "Always outputs OFF (0)" }
        };
        for (String[] c : comps) {
            if (c[0].isEmpty()) {
                ctx.drawText(textRenderer, Text.literal("      " + c[1]).formatted(Formatting.GRAY), ox + 5, oy, -1,
                        false);
            } else {
                ctx.drawText(textRenderer, Text.literal(c[0] + ": ").formatted(Formatting.WHITE)
                        .append(Text.literal(c[1]).formatted(Formatting.GRAY)), ox + 5, oy, -1, false);
            }
            oy += 9;
        }
        oy += 5;

        // I/O NODES
        ctx.drawText(textRenderer, Text.literal("I/O NODES").formatted(Formatting.BOLD, Formatting.UNDERLINE), ox, oy,
                COL_TEXT, false);
        oy += 10;
        ctx.drawText(textRenderer, Text.literal("redst_in/out: ").formatted(Formatting.WHITE)
                .append(Text.literal("Signals to/from host block").formatted(Formatting.GRAY)), ox + 5, oy, -1, false);
        oy += 9;
        ctx.drawText(textRenderer,
                Text.literal("in_1..4 / out_1..4: ").formatted(Formatting.WHITE)
                        .append(Text.literal("Signals between adjacent chips").formatted(Formatting.GRAY)),
                ox + 5, oy, -1, false);
        oy += 9;
        oy += 5;

        // WIRES
        ctx.drawText(textRenderer, Text.literal("WIRES").formatted(Formatting.BOLD, Formatting.UNDERLINE), ox, oy,
                COL_TEXT, false);
        oy += 10;
        ctx.fill(ox + 5, oy + 2, ox + 15, oy + 4, COL_WIRE_OFF);
        ctx.drawText(textRenderer, " Gray - Signal is OFF (0)", ox + 18, oy, 0xFFAAAAAA, false);
        oy += 9;
        ctx.fill(ox + 5, oy + 2, ox + 15, oy + 4, COL_WIRE_ON);
        ctx.drawText(textRenderer, " Green - Signal is ON (1)", ox + 18, oy, 0xFFAAAAAA, false);
        oy += 9;
        ctx.fill(ox + 5, oy + 2, ox + 15, oy + 4, COL_WIRE_ERR);
        ctx.drawText(textRenderer, " Red - Disconnected / Loop Error", ox + 18, oy, 0xFFAAAAAA, false);
        oy += 9;
        oy += 5;

        // CONTROLS
        ctx.drawText(textRenderer, Text.literal("CONTROLS").formatted(Formatting.BOLD, Formatting.UNDERLINE), ox, oy,
                COL_TEXT, false);
        oy += 10;
        String[] ctrls = {
                "Click palette -> select component",
                "Click grid -> place component",
                "Right-click component -> remove it",
                "Drag from output port -> draw wire",
                "Right-click wire -> delete wire",
                "ESC -> save and close"
        };
        for (String s : ctrls) {
            ctx.drawText(textRenderer, Text.literal(s).formatted(Formatting.GRAY), ox + 5, oy, -1, false);
            oy += 9;
        }

        ctx.disableScissor();

        ctx.drawCenteredTextWithShadow(textRenderer, "[Press ESC or click ? to close]", midX, y1 + h - 12, 0xFF888888);
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mx, int my) {
        drawGrid(ctx);
        drawPalette(ctx);
        drawFixedNodes(ctx);
        drawComponents(ctx);
        drawWires(ctx);
        drawPendingWire(ctx);
    }

    private void drawPalette(DrawContext ctx) {
        ctx.fill(0, 0, PALETTE_W, this.height, COL_PALETTE);

        ctx.enableScissor(0, 0, PALETTE_W, this.height);

        String title = "PALETTE";
        int tx = (PALETTE_W - textRenderer.getWidth(title)) / 2;
        ctx.drawText(textRenderer, Text.literal(title), tx, 8 - paletteScrollOffset, COL_TEXT, false);

        int by = 24 - paletteScrollOffset;
        for (String[] entry : PALETTE) {
            String label = entry[0];
            String type = entry[1];
            int bx = 5;

            ctx.fill(bx, by, bx + 50, by + 20, COL_BTN);
            if (type.equals(selectedPaletteType)) {
                ctx.drawBorder(bx, by, 50, 20, COL_BTN_BORD);
            }

            int lx = bx + (50 - textRenderer.getWidth(label)) / 2;
            int ly = by + (20 - 8) / 2;
            ctx.drawText(textRenderer, Text.literal(label), lx, ly, COL_TEXT, false);

            by += 24;
        }

        ctx.disableScissor();

        // Subtle 2px scrollbar
        int totalH = 24 + PALETTE.length * 24;
        int maxScroll = Math.max(0, totalH - this.height);
        if (maxScroll > 0) {
            int barH = Math.max(20, (int) ((float) this.height / totalH * this.height));
            int scrollY = (int) ((float) paletteScrollOffset / maxScroll * (this.height - barH));
            ctx.fill(PALETTE_W - 2, scrollY, PALETTE_W, scrollY + barH, 0xFF333355);
        }
    }

    private void drawGrid(DrawContext ctx) {
        ctx.fill(PALETTE_W, 0, this.width, this.height, COL_BG);

        for (int x = PALETTE_W + CELL_SIZE; x < this.width; x += CELL_SIZE) {
            ctx.drawVerticalLine(x, 1, this.height - 2, COL_GRID_LINE);
        }
        for (int y = gridOriginY; y < this.height; y += CELL_SIZE) {
            ctx.drawHorizontalLine(PALETTE_W + 1, this.width - 2, y, COL_GRID_LINE);
        }
        for (int y = gridOriginY - CELL_SIZE; y > 0; y -= CELL_SIZE) {
            ctx.drawHorizontalLine(PALETTE_W + 1, this.width - 2, y, COL_GRID_LINE);
        }

        ctx.drawBorder(PALETTE_W, 0, this.width - PALETTE_W, this.height, COL_GRID_BORD);
    }

    private void drawFixedNodes(DrawContext ctx) {
        int startY = (this.height - 80) / 2;

        for (int i = 0; i < INPUT_NODES.length; i++) {
            int py = startY + i * 20;
            boolean active = currentInputs[i];
            int col;
            if (i == 0) {
                col = active ? 0xFFFF4444 : 0xFF551111;
            } else {
                col = active ? COL_WIRE_ON : COL_WIRE_OFF;
            }
            int pxL = PALETTE_W + 8;
            ctx.fill(pxL - 6, py - 6, pxL + 6, py + 6, col);
            ctx.drawText(textRenderer, Text.literal(INPUT_LABELS[i]), pxL + 12, py - 4, COL_TEXT, false);

            int portCol = getPortColor(INPUT_NODES[i], 0, true);
            drawPort(ctx, pxL + 6, py, portCol);
        }

        for (int i = 0; i < OUTPUT_NODES.length; i++) {
            int py = startY + i * 20;
            boolean active = currentOutputs[i];
            int col;
            if (i == 0) {
                col = active ? 0xFFFF4444 : 0xFF551111;
            } else {
                col = active ? COL_WIRE_ON : COL_WIRE_OFF;
            }
            int pxR = this.width - 8;
            ctx.fill(pxR - 6, py - 6, pxR + 6, py + 6, col);
            String label = OUTPUT_LABELS[i];
            int tw = textRenderer.getWidth(label);
            ctx.drawText(textRenderer, Text.literal(label), pxR - 12 - tw, py - 4, COL_TEXT, false);

            int portCol = getPortColor(OUTPUT_NODES[i], 0, false);
            drawPort(ctx, pxR - 6, py, portCol);
        }
    }

    private void drawComponents(DrawContext ctx) {
        for (GuiComponent gc : handler.getGuiComponents()) {
            if (gc == draggingComponent)
                continue;
            int px = gridOriginX + gc.gridX * CELL_SIZE;
            int py = gridOriginY + gc.gridY * CELL_SIZE;
            drawComponentAt(ctx, px, py, gc, false);
        }
    }

    private int getPortColor(String id, int portIndex, boolean isOutput) {
        boolean connected = false;
        for (Wire w : handler.getWires()) {
            if (isOutput) {
                if (w.fromId.equals(id) && w.fromPort == portIndex)
                    connected = true;
            } else {
                if (w.toId.equals(id) && w.toPort == portIndex)
                    connected = true;
            }
        }
        if (!connected)
            return COL_WIRE_ERR;

        // For inputs that might be connected via wire, lookup the actual output port
        // driving it.
        // Wait, signals map holds signals of all output ports, so if we can just look
        // up this port directly.
        boolean signalVal = false;
        if (isOutput) {
            signalVal = signals.getOrDefault(new PortRef(id, portIndex, true), false);
        } else {
            // For input port, we find the wire that goes to it
            for (Wire w : handler.getWires()) {
                if (w.toId.equals(id) && w.toPort == portIndex) {
                    signalVal = signals.getOrDefault(new PortRef(w.fromId, w.fromPort, true), false);
                    break;
                }
            }
        }
        return signalVal ? COL_WIRE_ON : COL_WIRE_OFF;
    }

    private void drawPort(DrawContext ctx, int cx, int cy, int color) {
        ctx.fill(cx - PORT_RADIUS, cy - PORT_RADIUS, cx + PORT_RADIUS, cy + PORT_RADIUS, color);
    }

    private void drawWires(DrawContext ctx) {
        for (Wire w : handler.getWires()) {
            int[] from = getPortPixel(w.fromId, w.fromPort, true);
            int[] to = getPortPixel(w.toId, w.toPort, false);
            if (from == null || to == null) {
                // Invalid or unresolvable
                if (from != null)
                    drawWireLine(ctx, from[0], from[1], from[0] + 10, from[1], COL_WIRE_ERR);
                continue;
            }
            int col = w.signal ? COL_WIRE_ON : COL_WIRE_OFF;
            if (isUnstableAndLooped()) {
                col = COL_WIRE_ERR; // simplified anti-loop representation: if overall unstable, error wires
            }
            drawWireLine(ctx, from[0], from[1], to[0], to[1], col);
        }
    }

    private boolean isUnstableAndLooped() {
        return !isGlobalStable;
    }

    private boolean isGlobalStable = true;

    private void drawPendingWire(DrawContext ctx) {
        if (pendingWire == null)
            return;
        int[] from = getPortPixel(pendingWire.fromId, pendingWire.fromPort, true);
        if (from != null) {
            drawWireLine(ctx, from[0], from[1], wireCurX, wireCurY, COL_WIRE_PEND);
        }
    }

    private void drawWireLine(DrawContext ctx, int x1, int y1, int x2, int y2, int col) {
        drawLine(ctx, x1, y1, x2, y2, col);
    }

    private void drawLine(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        if (x1 == x2) {
            ctx.drawVerticalLine(x1, Math.min(y1, y2), Math.max(y1, y2), color);
        } else if (y1 == y2) {
            ctx.drawHorizontalLine(Math.min(x1, x2), Math.max(x1, x2), y1, color);
        } else {
            int midX = (x1 + x2) / 2;
            ctx.drawHorizontalLine(Math.min(x1, midX), Math.max(x1, midX), y1, color);
            ctx.drawVerticalLine(midX, Math.min(y1, y2), Math.max(y1, y2), color);
            ctx.drawHorizontalLine(Math.min(midX, x2), Math.max(midX, x2), y2, color);
        }
    }

    // -----------------------------------------------------------------------
    // Port Logic & Hit Testing
    // -----------------------------------------------------------------------

    private PortHit getPortAt(double mouseX, double mouseY) {
        int startY = (this.height - 80) / 2;
        int pxL = PALETTE_W + 8;
        for (int i = 0; i < INPUT_NODES.length; i++) {
            int py = startY + i * 20;
            int px = pxL + 6;
            if (Math.abs(mouseX - px) <= 5 && Math.abs(mouseY - py) <= 5) {
                return new PortHit(INPUT_NODES[i], 0, true);
            }
        }

        int pxR = this.width - 8;
        for (int i = 0; i < OUTPUT_NODES.length; i++) {
            int py = startY + i * 20;
            int px = pxR - 6;
            if (Math.abs(mouseX - px) <= 5 && Math.abs(mouseY - py) <= 5) {
                return new PortHit(OUTPUT_NODES[i], 0, false);
            }
        }

        for (GuiComponent gc : handler.getGuiComponents()) {
            int px = gridOriginX + gc.gridX * CELL_SIZE;
            int py = gridOriginY + gc.gridY * CELL_SIZE;
            int pixH = getComponentHeight(gc.type) * CELL_SIZE;

            int outPx = px + CELL_SIZE;
            int outPy = py + pixH / 2;
            if (Math.abs(mouseX - outPx) <= 5 && Math.abs(mouseY - outPy) <= 5) {
                return new PortHit(gc.id, 0, true);
            }

            int inCount = getEffectiveInputCount(gc.type);
            for (int i = 0; i < inCount; i++) {
                int inPy = py + (i + 1) * pixH / (inCount + 1);
                if (Math.abs(mouseX - px) <= 5 && Math.abs(mouseY - inPy) <= 5) {
                    return new PortHit(gc.id, i, false);
                }
            }
        }
        return null;
    }

    private int getEffectiveInputCount(String type) {
        if (type == null)
            return 1;
        return switch (type.toLowerCase()) {
            case "and", "or", "xor" -> 2;
            case "1", "0" -> 0;
            default -> 1;
        };
    }

    private int[] getPortPixel(String id, int portIndex, boolean isOutput) {
        int startY = (this.height - 80) / 2;
        if (isOutput) {
            for (int i = 0; i < INPUT_NODES.length; i++) {
                if (INPUT_NODES[i].equals(id))
                    return new int[] { PALETTE_W + 14, startY + i * 20 };
            }
        } else {
            for (int i = 0; i < OUTPUT_NODES.length; i++) {
                if (OUTPUT_NODES[i].equals(id))
                    return new int[] { this.width - 14, startY + i * 20 };
            }
        }

        for (GuiComponent gc : handler.getGuiComponents()) {
            if (gc.id.equals(id)) {
                int px = gridOriginX + gc.gridX * CELL_SIZE;
                int py = gridOriginY + gc.gridY * CELL_SIZE;
                int pixH = getComponentHeight(gc.type) * CELL_SIZE;
                if (isOutput) {
                    return new int[] { px + CELL_SIZE, py + pixH / 2 };
                } else {
                    int inCount = gc.getInputCount();
                    return new int[] { px, py + (portIndex + 1) * pixH / (inCount + 1) };
                }
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

        // Check Help Button [?]
        if (imx >= this.width - 20 && imx <= this.width - 5 && imy >= 5 && imy <= 20) {
            helpOpen = !helpOpen;
            return true;
        }

        if (helpOpen)
            return true; // Consume clicks while help is open

        if (draggingComponent != null) {
            if (button == 1) { // Right click to cancel drag
                draggingComponent = null;
                return true;
            }
            return true;
        }

        if (button == 0 && imx < PALETTE_W) {
            int by = 24 - paletteScrollOffset;
            for (String[] entry : PALETTE) {
                if (imy >= by && imy <= by + 20) {
                    selectedPaletteType = entry[1];
                    return true;
                }
                by += 24;
            }
            return true;
        }

        if (imx >= PALETTE_W) {
            PortHit hit = getPortAt(mx, my);
            int cellX = (imx - gridOriginX) / CELL_SIZE;
            int cellY = (imy - gridOriginY) / CELL_SIZE;
            GuiComponent compAt = componentAt(cellX, cellY);

            if (button == 0) {
                // Drag start logic
                if (compAt != null) {
                    if (hit != null && hit.isOutput) {
                        // Priority: port hit starts wire
                        pendingWire = new Wire(hit.componentId, hit.portIndex, "", 0);
                        wireCurX = imx;
                        wireCurY = imy;
                    } else {
                        // Otherwise start dragging
                        draggingComponent = compAt;
                        dragOriginGridX = compAt.gridX;
                        dragOriginGridY = compAt.gridY;
                        dragOffsetX = imx - (gridOriginX + dragOriginGridX * CELL_SIZE);
                        dragOffsetY = imy - (gridOriginY + dragOriginGridY * CELL_SIZE);
                    }
                    return true;
                }

                if (hit != null && hit.isOutput) {
                    pendingWire = new Wire(hit.componentId, hit.portIndex, "", 0);
                    wireCurX = imx;
                    wireCurY = imy;
                    return true;
                }

                // Also allow toggling input states for testing simulation inside the GUI
                if (hit != null && hit.isOutput) {
                    for (int i = 0; i < INPUT_NODES.length; i++) {
                        if (INPUT_NODES[i].equals(hit.componentId)) {
                            currentInputs[i] = !currentInputs[i];
                            return true; // We also just started drawing a wire, actually let's abort wire if clicked
                                         // node directly to toggle?
                            // Standard logisim allows toggle with a specific tool. The prompt doesn't ask
                            // for toggle inputs... I will stick to drag drop.
                        }
                    }
                }
                // Wait, if an input node is right clicked, I could toggle it. But user didn't
                // ask.

                if (selectedPaletteType != null && hit == null) {
                    if (!cellOccupied(cellX, cellY)) {
                        handler.getGuiComponents().add(
                                new GuiComponent(UUID.randomUUID().toString(), selectedPaletteType, cellX, cellY));
                    }
                    return true;
                }
            } else if (button == 1) { // Right click
                if (attemptDeleteWire(imx, imy)) {
                    return true;
                }
                GuiComponent comp = componentAt(cellX, cellY);
                if (comp != null) {
                    handler.getGuiComponents().remove(comp);
                    handler.getWires().removeIf(w -> w.fromId.equals(comp.id) || w.toId.equals(comp.id));
                    return true;
                }

                // Allow user to toggle inputs with Right Click for debugging
                if (hit != null && hit.isOutput) {
                    for (int i = 0; i < INPUT_NODES.length; i++) {
                        if (INPUT_NODES[i].equals(hit.componentId)) {
                            currentInputs[i] = !currentInputs[i];
                            return true;
                        }
                    }
                }

                selectedPaletteType = null;
                return true;
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (pendingWire != null) {
            wireCurX = (int) mx;
            wireCurY = (int) my;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (helpOpen) {
            helpScrollOffset -= (int) (verticalAmount * 15);
            if (helpScrollOffset < 0)
                helpScrollOffset = 0;
            if (helpScrollOffset > 150)
                helpScrollOffset = 150; // Enough for current descriptions
            return true;
        }
        if (mouseX < PALETTE_W) {
            int totalH = 24 + PALETTE.length * 24;
            int maxScroll = Math.max(0, totalH - this.height);
            if (maxScroll > 0) {
                paletteScrollOffset -= (int) (verticalAmount * 24);
                if (paletteScrollOffset < 0)
                    paletteScrollOffset = 0;
                if (paletteScrollOffset > maxScroll)
                    paletteScrollOffset = maxScroll;
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        int imx = (int) mx, imy = (int) my;

        if (draggingComponent != null && button == 0) {
            int cellX = (imx - gridOriginX) / CELL_SIZE;
            int cellY = (imy - gridOriginY) / CELL_SIZE;

            if (imx >= PALETTE_W && !cellOccupiedExcluding(cellX, cellY, draggingComponent)) {
                draggingComponent.gridX = cellX;
                draggingComponent.gridY = cellY;
            }
            draggingComponent = null;
            return true;
        }

        if (pendingWire != null && button == 0) {
            PortHit hit = getPortAt(mx, my);
            if (hit != null && !hit.isOutput && !hit.componentId.equals(pendingWire.fromId)) {
                // Final validation: check if target is an output node or a valid component
                // input
                boolean isOutputNode = false;
                for (String node : OUTPUT_NODES) {
                    if (node.equals(hit.componentId)) {
                        isOutputNode = true;
                        break;
                    }
                }

                if (isOutputNode) {
                    handler.getWires().removeIf(w -> w.toId.equals(hit.componentId) && w.toPort == hit.portIndex);
                    handler.getWires()
                            .removeIf(w -> w.fromId.equals(pendingWire.fromId) && w.fromPort == pendingWire.fromPort);
                    handler.getWires()
                            .add(new Wire(pendingWire.fromId, pendingWire.fromPort, hit.componentId, hit.portIndex));
                } else {
                    GuiComponent targetComp = null;
                    for (GuiComponent gc : handler.getGuiComponents()) {
                        if (gc.id.equals(hit.componentId)) {
                            targetComp = gc;
                            break;
                        }
                    }
                    if (targetComp != null && getEffectiveInputCount(targetComp.type) > 0) {
                        handler.getWires().removeIf(w -> w.toId.equals(hit.componentId) && w.toPort == hit.portIndex);
                        handler.getWires().removeIf(
                                w -> w.fromId.equals(pendingWire.fromId) && w.fromPort == pendingWire.fromPort);
                        handler.getWires().add(
                                new Wire(pendingWire.fromId, pendingWire.fromPort, hit.componentId, hit.portIndex));
                    }
                }
            }
            pendingWire = null;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private boolean cellOccupiedExcluding(int cx, int cy, GuiComponent excluded) {
        for (GuiComponent gc : handler.getGuiComponents()) {
            if (gc == excluded)
                continue;
            int h = getComponentHeight(gc.type);
            if (gc.gridX == cx && cy >= gc.gridY && cy < gc.gridY + h)
                return true;
        }
        return false;
    }

    private boolean attemptDeleteWire(int mx, int my) {
        for (Wire w : handler.getWires()) {
            int[] from = getPortPixel(w.fromId, w.fromPort, true);
            int[] to = getPortPixel(w.toId, w.toPort, false);
            if (from != null && to != null) {
                int x1 = from[0], y1 = from[1], x2 = to[0], y2 = to[1];
                int midX = (x1 + x2) / 2;
                if (isNearSegment(mx, my, x1, y1, midX, y1) ||
                        isNearSegment(mx, my, midX, y1, midX, y2) ||
                        isNearSegment(mx, my, midX, y2, x2, y2)) {
                    handler.getWires().remove(w);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isNearSegment(int px, int py, int x1, int y1, int x2, int y2) {
        if (x1 == x2) {
            int minY = Math.min(y1, y2);
            int maxY = Math.max(y1, y2);
            return Math.abs(px - x1) <= 4 && py >= minY - 4 && py <= maxY + 4;
        } else if (y1 == y2) {
            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            return Math.abs(py - y1) <= 4 && px >= minX - 4 && px <= maxX + 4;
        }
        return false;
    }

    private boolean cellOccupied(int cx, int cy) {
        for (GuiComponent gc : handler.getGuiComponents()) {
            int h = getComponentHeight(gc.type);
            if (gc.gridX == cx && cy >= gc.gridY && cy < gc.gridY + h)
                return true;
        }
        return false;
    }

    private GuiComponent componentAt(int cx, int cy) {
        for (GuiComponent gc : handler.getGuiComponents()) {
            int h = getComponentHeight(gc.type);
            if (gc.gridX == cx && cy >= gc.gridY && cy < gc.gridY + h)
                return gc;
        }
        return null;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (helpOpen) {
            if (keyCode == 256) { // 256 is ESC
                helpOpen = false;
                return true;
            }
            return true; // Eat other keys? Maybe not strictly necessary but keeps focused.
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        handler.saveToServer();
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
