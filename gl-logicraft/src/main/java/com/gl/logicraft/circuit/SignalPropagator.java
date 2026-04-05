package com.gl.logicraft.circuit;

import com.gl.logicraft.blockentity.LogicChipBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Handles cross-chip signal propagation.
 *
 * When a LogicChip's outputs change, this class checks all 6 neighbouring
 * block positions for other LogicChipBlockEntities and forwards the output
 * signals to them using OR-merge semantics (a true from ANY neighbour wins).
 *
 * Anti-loop protection is done via the isPropagating flag on each entity.
 */
public class SignalPropagator {

    private SignalPropagator() {}  // static utility class

    /**
     * Check all 6 neighbours of {@code pos}. If any of them contains a
     * {@link LogicChipBlockEntity}, call {@code receiveSignals(outputs)} on it.
     *
     * @param world   the server-side world
     * @param pos     position of the chip that just finished evaluating
     * @param outputs the chip's current output array (length == CircuitState.SIGNAL_COUNT)
     */
    public static void propagate(World world, BlockPos pos, boolean[] outputs) {
        if (world.isClient()) return; // server-side only

        for (Direction dir : Direction.values()) {
            BlockPos neighbourPos = pos.offset(dir);
            if (world.getBlockEntity(neighbourPos) instanceof LogicChipBlockEntity neighbour) {
                neighbour.receiveSignals(outputs);
            }
        }
    }
}
