package com.gl.logicraft.circuit;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Holds the current input and output signal state for a LogicChip.
 * 5 boolean inputs and 5 boolean outputs.
 * inputs[0] maps to the redstone power received from the host block.
 * outputs[0] maps to the redstone power emitted to the world.
 */
public class CircuitState {

    public static final int SIGNAL_COUNT = 5;

    /** Incoming boolean signals: index 0 = redstone from host block. */
    public final boolean[] inputs = new boolean[SIGNAL_COUNT];

    /** Outgoing boolean signals: index 0 = redstone emitted to world. */
    public final boolean[] outputs = new boolean[SIGNAL_COUNT];

    // -----------------------------------------------------------------------
    // Redstone helpers
    // -----------------------------------------------------------------------

    /**
     * Reads the redstone power at the given position and stores it in inputs[0].
     * Any power level > 0 is treated as true.
     */
    public void readRedstone(World world, BlockPos pos) {
        int power = world.getReceivedRedstonePower(pos);
        inputs[0] = power > 0;
    }

    /**
     * Returns the redstone output strength derived from outputs[0].
     * true  → 15 (full power)
     * false →  0 (no power)
     */
    public int writeRedstone() {
        return outputs[0] ? 15 : 0;
    }

    // -----------------------------------------------------------------------
    // NBT
    // -----------------------------------------------------------------------

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        // Pack booleans as single bytes (0/1) for each slot
        for (int i = 0; i < SIGNAL_COUNT; i++) {
            nbt.putBoolean("in" + i, inputs[i]);
            nbt.putBoolean("out" + i, outputs[i]);
        }
        return nbt;
    }

    public void fromNbt(NbtCompound nbt) {
        for (int i = 0; i < SIGNAL_COUNT; i++) {
            inputs[i]  = nbt.getBoolean("in"  + i);
            outputs[i] = nbt.getBoolean("out" + i);
        }
    }

    /** Copies all values from another CircuitState. */
    public void copyFrom(CircuitState other) {
        System.arraycopy(other.inputs,  0, this.inputs,  0, SIGNAL_COUNT);
        System.arraycopy(other.outputs, 0, this.outputs, 0, SIGNAL_COUNT);
    }
}
