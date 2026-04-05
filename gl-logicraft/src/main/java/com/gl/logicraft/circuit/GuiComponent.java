package com.gl.logicraft.circuit;

import net.minecraft.nbt.NbtCompound;

/**
 * Represents a visual component placed on the circuit editor grid.
 * This is the GUI-side data model (not the execution-side LogicComponent).
 * When the user saves, the GuiComponent+Wire graph is evaluated directly.
 */
public class GuiComponent {

    public final String id;   // unique ID used by Wire references
    public final String type; // "and", "or", "not", "pass"
    public int gridX, gridY;  // cell coordinates on the 30×30 grid

    public GuiComponent(String id, String type, int gridX, int gridY) {
        this.id = id;
        this.type = type;
        this.gridX = gridX;
        this.gridY = gridY;
    }

    /** How many input ports does this gate type have? */
    public static int inputCountFor(String type) {
        return switch (type) {
            case "and", "or" -> 2;
            default           -> 1; // not, pass
        };
    }

    public int getInputCount()  { return inputCountFor(type); }
    public int getOutputCount() { return 1; }

    /** Display label shown inside the grid cell. */
    public String getLabel() {
        return switch (type) {
            case "and"  -> "AND";
            case "or"   -> "OR";
            case "not"  -> "NOT";
            case "pass" -> "PASS";
            default     -> type.toUpperCase();
        };
    }

    // -----------------------------------------------------------------------
    // NBT
    // -----------------------------------------------------------------------

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("id",   id);
        nbt.putString("type", type);
        nbt.putInt("x", gridX);
        nbt.putInt("y", gridY);
        return nbt;
    }

    public static GuiComponent fromNbt(NbtCompound nbt) {
        return new GuiComponent(
            nbt.getString("id"),
            nbt.getString("type"),
            nbt.getInt("x"),
            nbt.getInt("y")
        );
    }
}
