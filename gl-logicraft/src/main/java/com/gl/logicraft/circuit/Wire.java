package com.gl.logicraft.circuit;

import net.minecraft.nbt.NbtCompound;

/**
 * A wire connects the output port of one component/node to the input port of another.
 *
 * Node IDs for the fixed edge nodes:
 *   Inputs  (left edge): "REDST_IN", "IN_0" … "IN_3"
 *   Outputs (right edge): "REDST_OUT", "OUT_0" … "OUT_3"
 *
 * {@code bitWidth} is always 1 now but included for future multi-bit support.
 */
public class Wire {

    public final String fromId;  // source component/node ID
    public final int    fromPort; // output port index on the source
    public final String toId;    // target component/node ID
    public final int    toPort;  // input port index on the target
    public final int    bitWidth; // reserved for future multi-bit wires (always 1)
    public boolean      signal = false; // explicitly added for state rendering

    public Wire(String fromId, int fromPort, String toId, int toPort) {
        this(fromId, fromPort, toId, toPort, 1);
    }

    public Wire(String fromId, int fromPort, String toId, int toPort, int bitWidth) {
        this.fromId   = fromId;
        this.fromPort = fromPort;
        this.toId     = toId;
        this.toPort   = toPort;
        this.bitWidth = bitWidth;
    }

    // -----------------------------------------------------------------------
    // NBT
    // -----------------------------------------------------------------------

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("from",     fromId);
        nbt.putInt("fromPort",    fromPort);
        nbt.putString("to",       toId);
        nbt.putInt("toPort",      toPort);
        nbt.putInt("bitWidth",    bitWidth);
        return nbt;
    }

    public static Wire fromNbt(NbtCompound nbt) {
        return new Wire(
            nbt.getString("from"),
            nbt.getInt("fromPort"),
            nbt.getString("to"),
            nbt.getInt("toPort"),
            nbt.contains("bitWidth") ? nbt.getInt("bitWidth") : 1
        );
    }
}
