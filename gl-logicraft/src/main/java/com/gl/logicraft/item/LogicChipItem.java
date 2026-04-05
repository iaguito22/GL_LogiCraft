package com.gl.logicraft.item;

import net.minecraft.block.Block;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class LogicChipItem extends BlockItem {
    public LogicChipItem(Block block, Settings settings) {
        super(block, settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        if (Screen.hasShiftDown()) {
            tooltip.add(Text.literal("A chip that you can attach to blocks").formatted(Formatting.GRAY));
            tooltip.add(Text.literal("to add a logic circuit to them.").formatted(Formatting.GRAY));
            tooltip.add(Text.literal("Crafted with: 3x Gold Ingot, 4x Redstone,").formatted(Formatting.DARK_GRAY));
            tooltip.add(Text.literal("1x Nether Quartz (center)").formatted(Formatting.DARK_GRAY));
            tooltip.add(Text.literal("Use the Logic Wrench to place and configure.").formatted(Formatting.YELLOW));
        } else {
            tooltip.add(Text.literal("A chip that you can attach to blocks to add a logic circuit to them.").formatted(Formatting.GRAY));
            tooltip.add(Text.literal("Crafted with: Gold Ingot, Redstone, Quartz").formatted(Formatting.DARK_GRAY));
            tooltip.add(Text.literal("[Hold Shift to expand]").formatted(Formatting.DARK_GRAY).formatted(Formatting.ITALIC));
        }
    }
}
