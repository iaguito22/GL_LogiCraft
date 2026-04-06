package com.gl.logicraft.block;

import com.gl.logicraft.blockentity.LogicChipBlockEntity;
import com.gl.logicraft.registry.ModBlockEntities;
import com.gl.logicraft.registry.ModItems;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * LogicChipBlock — a nearly-invisible attachment block.
 *
 * Placement rules:
 * - Can be placed on any face of any block.
 * - FACING = the direction from the host block toward the chip.
 * (e.g., placed on top of a block → FACING = UP, host is below.)
 * - Has a 1-pixel-thick hitbox that hugs the face it's attached to.
 *
 * Redstone:
 * - Reads host block's incoming redstone → CircuitState.inputs[0].
 * - Emits getWeakRedstonePower from CircuitState.outputs[0].
 *
 * Interaction:
 * - Right-click with an item tagged `gllogicraft:wrench` → opens GUI.
 */
public class LogicChipBlock extends BlockWithEntity {

    @Override
    protected com.mojang.serialization.MapCodec<? extends BlockWithEntity> getCodec() {
        return createCodec(LogicChipBlock::new);
    }

    // -----------------------------------------------------------------------
    // Block state property
    // -----------------------------------------------------------------------

    /** Direction pointing FROM the chip TOWARD its host block. */
    public static final EnumProperty<Direction> FACING = Properties.FACING;

    // -----------------------------------------------------------------------
    // Hitbox shapes (0.125 thick, full on the other two axes)
    // -----------------------------------------------------------------------

    // Shapes are 1 pixel thick (0 to 1 / 15 to 16)
    // We don't need the static map anymore if we use the switch in getOutlineShape.

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public LogicChipBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.DOWN));
    }

    // -----------------------------------------------------------------------
    // Block state
    // -----------------------------------------------------------------------

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /**
     * On placement, set FACING = opposite of the side the player clicked.
     * e.g., player clicks top face (side = UP) → chip placed above host → FACING =
     * DOWN.
     */
    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getSide());
    }

    @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        if (!world.isClient) {
            world.scheduleBlockTick(pos, this, 1);
        }
    }

    @Override
    protected void scheduledTick(BlockState state, net.minecraft.server.world.ServerWorld world, BlockPos pos,
            net.minecraft.util.math.random.Random random) {
        if (world.getBlockEntity(pos) instanceof LogicChipBlockEntity chip) {
            chip.serverEvaluate();
        }
    }

    // -----------------------------------------------------------------------
    // Hitbox
    // -----------------------------------------------------------------------

    @Override
    public float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
        if (player.getMainHandStack().isOf(ModItems.WRENCH)) {
            return 1.0f; // instant with wrench
        }
        return 0.0f; // unbreakable without wrench
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
        return switch (state.get(FACING)) {
            case DOWN -> Block.createCuboidShape(0, 15, 0, 16, 16, 16);
            case UP -> Block.createCuboidShape(0, 0, 0, 16, 1, 16);
            case NORTH -> Block.createCuboidShape(0, 0, 15, 16, 16, 16);
            case SOUTH -> Block.createCuboidShape(0, 0, 0, 16, 16, 1);
            case EAST -> Block.createCuboidShape(0, 0, 0, 1, 16, 16);
            case WEST -> Block.createCuboidShape(15, 0, 0, 16, 16, 16);
        };
    }

    // -----------------------------------------------------------------------
    // Render type — invisible
    // -----------------------------------------------------------------------

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }

    // -----------------------------------------------------------------------
    // Block entity
    // -----------------------------------------------------------------------

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new LogicChipBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient)
            return null;
        return validateTicker(type, ModBlockEntities.LOGIC_CHIP_BLOCK_ENTITY, LogicChipBlockEntity::tick);
    }

    // -----------------------------------------------------------------------
    // Redstone output
    // -----------------------------------------------------------------------

    @Override
    public boolean emitsRedstonePower(BlockState state) {
        return true;
    }

    @Override
    protected int getStrongRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        Direction facing = state.get(FACING);
        // Respond only to the host block querying us (host is in facing.getOpposite()
        // direction)
        // Host asks from direction 'facing' (pointing from host toward chip)
        if (direction != facing)
            return 0;
        if (!(world.getBlockEntity(pos) instanceof LogicChipBlockEntity chip))
            return 0;
        return chip.getCircuitState().outputs[0] ? 15 : 0;
    }

    @Override
    protected int getWeakRedstonePower(
            BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return getStrongRedstonePower(state, world, pos, direction);
    }

    // -----------------------------------------------------------------------
    // Neighbour update — read host block's redstone
    // -----------------------------------------------------------------------

    @Override
    protected void neighborUpdate(
            BlockState state, World world, BlockPos pos,
            Block sourceBlock, @Nullable net.minecraft.world.block.WireOrientation orientation, boolean notify) {

        if (world.isClient())
            return;

        if (world.getBlockEntity(pos) instanceof LogicChipBlockEntity be) {
            BlockPos hostPos = pos.offset(state.get(FACING).getOpposite());
            Direction chipDir = state.get(FACING);
            int power = 0;
            for (Direction dir : Direction.values()) {
                if (dir == chipDir)
                    continue;
                power = Math.max(power, world.getEmittedRedstonePower(hostPos.offset(dir), dir));
            }
            be.pendingRedstoneInput = power > 0;
            world.scheduleBlockTick(pos, this, 1);
        }
    }

    // -----------------------------------------------------------------------
    // Right-click (wrench) — GUI placeholder
    // -----------------------------------------------------------------------

    @Override
    public ActionResult onUse(
            BlockState state, World world, BlockPos pos,
            PlayerEntity player, BlockHitResult hit) {
        return ActionResult.PASS;
    }
}
