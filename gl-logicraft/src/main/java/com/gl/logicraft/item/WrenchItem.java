package com.gl.logicraft.item;

import com.gl.logicraft.block.LogicChipBlock;
import com.gl.logicraft.blockentity.LogicChipBlockEntity;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

import com.gl.logicraft.gui.LogicScreenHandler;

/**
 * The Logic Wrench item.
 * - Right-click on a LogicChipBlock → opens the circuit editor GUI.
 * - Left-click (mining) → breaks the chip normally and drops it as an item.
 */
public class WrenchItem extends Item {

    public WrenchItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        PlayerEntity player = context.getPlayer();

        if (!(world.getBlockState(pos).getBlock() instanceof LogicChipBlock)) {
            return ActionResult.PASS;
        }

        // Client side: return success to prevent ghost hand animation
        if (world.isClient())
            return ActionResult.SUCCESS;

        // Server side: open the GUI if the block entity exists
        if (player instanceof ServerPlayerEntity serverPlayer
                && world.getBlockEntity(pos) instanceof LogicChipBlockEntity chip) {

            serverPlayer.openHandledScreen(new ExtendedScreenHandlerFactory<NbtCompound>() {

                @Override
                public NbtCompound getScreenOpeningData(ServerPlayerEntity _p) {
                    // Pack pos + circuit layout into a single NbtCompound
                    NbtCompound data = new NbtCompound();
                    data.putInt("posX", pos.getX());
                    data.putInt("posY", pos.getY());
                    data.putInt("posZ", pos.getZ());
                    data.put("GuiData", chip.serializeGuiData());
                    return data;
                }

                @Override
                public Text getDisplayName() {
                    return Text.translatable("container.gllogicraft.logic_chip");
                }

                @Override
                public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity _p) {
                    // Server-side handler — constructed from BlockPos
                    return new LogicScreenHandler(syncId, inv, pos);
                }
            });
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    @Override
    public void appendTooltip(net.minecraft.item.ItemStack stack, TooltipContext context, List<Text> tooltip,
            TooltipType type) {
        tooltip.add(Text.literal("Essential tool to use the Logic Chip.").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Crafted with:").formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.literal("Ender Pearl, Blaze Rod,").formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.literal("Gold Block, Netherite Ingot").formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.literal("Right-click chip: open circuit editor.").formatted(Formatting.YELLOW));
        tooltip.add(Text.literal("Left-click chip: remove chip.").formatted(Formatting.YELLOW));
    }
}
