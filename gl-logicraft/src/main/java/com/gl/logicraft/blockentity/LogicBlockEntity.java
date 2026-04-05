package com.gl.logicraft.blockentity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;

/**
 * Placeholder block entity class for storing logic state and configurations.
 * This class will hold the programmed logic, current input/output states,
 * and handle synchronization between server and client for the GUI.
 */
public class LogicBlockEntity extends BlockEntity {

    public LogicBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // TODO: Implement readNbt and writeNbt to persist programmed logic
    // TODO: Implement LogicComponent storage and execution (from circuit package)
}
