package com.gl.logicraft.block;

import com.gl.logicraft.GLLogiCraft;
import com.gl.logicraft.blockentity.LogicChipBlockEntity;
import com.gl.logicraft.registry.ModBlockEntities;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * LogicChipBlock — a nearly-invisible attachment block.
 *
 * Placement rules:
 *  - Can be placed on any face of any block.
 *  - FACING = the direction from the chip toward its host block.
 *    (e.g., placed on top of a block → FACING = DOWN, host is below.)
 *  - Has a 0.125-thick hitbox that hugs the face it's attached to.
 *
 * Redstone:
 *  - Reads host block's incoming redstone → CircuitState.inputs[0].
 *  - Emits getWeakRedstonePower from CircuitState.outputs[0].
 *
 * Interaction:
 *  - Right-click with an item tagged `gllogicraft:wrench` → opens GUI (TODO).
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

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        double t = 0.125;
        SHAPES.put(Direction.DOWN,  VoxelShapes.cuboid(0, 0,     0, 1, t,     1));
        SHAPES.put(Direction.UP,    VoxelShapes.cuboid(0, 1 - t, 0, 1, 1,     1));
        SHAPES.put(Direction.NORTH, VoxelShapes.cuboid(0, 0,     1 - t, 1, 1, 1));
        SHAPES.put(Direction.SOUTH, VoxelShapes.cuboid(0, 0,     0,     1, 1, t));
        SHAPES.put(Direction.WEST,  VoxelShapes.cuboid(1 - t, 0, 0, 1, 1,     1));
        SHAPES.put(Direction.EAST,  VoxelShapes.cuboid(0,     0, 0, t, 1,     1));
    }

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
     * e.g., player clicks top face (side = UP) → chip placed above host → FACING = DOWN.
     */
    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction hitFace = ctx.getSide(); // face of the target block that was clicked
        // FACING points from chip toward host → opposite of hit face
        return getDefaultState().with(FACING, hitFace.getOpposite());
    }

    // -----------------------------------------------------------------------
    // Hitbox
    // -----------------------------------------------------------------------

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES.get(state.get(FACING));
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
        // No tick needed; all logic is event-driven
        return null;
    }

    // -----------------------------------------------------------------------
    // Redstone output
    // -----------------------------------------------------------------------

    @Override
    public boolean emitsRedstonePower(BlockState state) {
        return true;
    }

    @Override
    public int getWeakRedstonePower(
            BlockState state, BlockView world, BlockPos pos, Direction direction) {
        if (world.getBlockEntity(pos) instanceof LogicChipBlockEntity be) {
            return be.getCircuitState().writeRedstone();
        }
        return 0;
    }

    // -----------------------------------------------------------------------
    // Neighbour update — read host block's redstone
    // -----------------------------------------------------------------------

    @Override
    protected void neighborUpdate(
            BlockState state, World world, BlockPos pos,
            Block sourceBlock, @Nullable net.minecraft.world.block.WireOrientation orientation, boolean notify) {

        if (world.isClient()) return;

        if (world.getBlockEntity(pos) instanceof LogicChipBlockEntity be) {
            // The host block sits in the FACING direction from this chip
            BlockPos hostPos = pos.offset(state.get(FACING));
            be.getCircuitState().readRedstone(world, hostPos);
            be.evaluate();
        }
    }

    // -----------------------------------------------------------------------
    // Right-click (wrench) — GUI placeholder
    // -----------------------------------------------------------------------

    @Override
    public ActionResult onUse(
            BlockState state, World world, BlockPos pos,
            PlayerEntity player, BlockHitResult hit) {

        ItemStack held = player.getMainHandStack();
        TagKey<Item> wrenchTag = TagKey.of(RegistryKeys.ITEM, Identifier.of(GLLogiCraft.MOD_ID, "wrench"));
        if (held.isIn(wrenchTag)) {
            if (!world.isClient()) {
                // TODO: Open the logic programming GUI
                player.sendMessage(
                        net.minecraft.text.Text.literal("[GL_LogiCraft] GUI not yet implemented."), true);
            }
            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }
}
