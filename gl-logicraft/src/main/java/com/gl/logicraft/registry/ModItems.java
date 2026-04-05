package com.gl.logicraft.registry;

import com.gl.logicraft.GLLogiCraft;
import com.gl.logicraft.item.LogicChipItem;
import com.gl.logicraft.item.WrenchItem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

/**
 * Registry class for all items added by GL_LogiCraft.
 */
public class ModItems {

    public static final RegistryKey<Item> LOGIC_CHIP_KEY = RegistryKey.of(RegistryKeys.ITEM,
            Identifier.of(GLLogiCraft.MOD_ID, "logic_chip"));
    public static final RegistryKey<Item> WRENCH_KEY = RegistryKey.of(RegistryKeys.ITEM,
            Identifier.of(GLLogiCraft.MOD_ID, "wrench"));

    /** Block item so the LogicChip can exist in inventory and be placed. */
    public static final Item LOGIC_CHIP = Registry.register(
            Registries.ITEM,
            LOGIC_CHIP_KEY,
            new LogicChipItem(ModBlocks.LOGIC_CHIP,
                    new Item.Settings().registryKey(LOGIC_CHIP_KEY).useBlockPrefixedTranslationKey()));

    /** The Logic Wrench — opens the circuit editor GUI and removes chips. */
    public static final Item WRENCH = Registry.register(
            Registries.ITEM,
            WRENCH_KEY,
            new WrenchItem(new Item.Settings().registryKey(WRENCH_KEY).maxCount(1)));

    /** Creative tab for GL_LogiCraft items. */
    public static final ItemGroup ITEM_GROUP = Registry.register(
            Registries.ITEM_GROUP,
            Identifier.of(GLLogiCraft.MOD_ID, "group"),
            FabricItemGroup.builder()
                    .displayName(Text.translatable("itemGroup.gllogicraft.group"))
                    .icon(() -> new net.minecraft.item.ItemStack(WRENCH))
                    .entries((ctx, entries) -> {
                        entries.add(LOGIC_CHIP);
                        entries.add(WRENCH);
                    })
                    .build());

    public static void register() {
        GLLogiCraft.LOGGER.info("Registering GL_LogiCraft Items...");
        // Static initialiser triggers field assignment.
    }
}
