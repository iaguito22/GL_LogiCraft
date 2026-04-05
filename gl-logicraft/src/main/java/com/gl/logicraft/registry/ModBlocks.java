package com.gl.logicraft.registry;

import com.gl.logicraft.GLLogiCraft;
import com.gl.logicraft.block.LogicChipBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

/**
 * Registry class for all blocks added by GL_LogiCraft.
 * Add new blocks here as static fields and register via registerBlock().
 */
public class ModBlocks {

    public static final RegistryKey<Block> LOGIC_CHIP_KEY = RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(GLLogiCraft.MOD_ID, "logic_chip"));

    /**
     * Logic Chip — a nearly-invisible attachment block.
     * Settings: no collision, non-opaque, does not block light.
     */
    public static final LogicChipBlock LOGIC_CHIP = Registry.register(
            Registries.BLOCK,
            LOGIC_CHIP_KEY,
            new LogicChipBlock(
                    AbstractBlock.Settings.create()
                            .registryKey(LOGIC_CHIP_KEY)
                            .mapColor(MapColor.CLEAR)
                            .noCollision()
                            .nonOpaque()
                            .hardness(1.0f)
                            .resistance(1.0f)
                            .pistonBehavior(net.minecraft.block.piston.PistonBehavior.DESTROY)
            )
    );

    // -----------------------------------------------------------------------

    public static void register() {
        GLLogiCraft.LOGGER.info("Registering GL_LogiCraft Blocks...");
        // Static fields are initialised when this class is first loaded;
        // calling register() ensures that happens at the right time.
    }

    private static Block registerBlock(String name, Block block) {
        return Registry.register(Registries.BLOCK, Identifier.of(GLLogiCraft.MOD_ID, name), block);
    }
}
