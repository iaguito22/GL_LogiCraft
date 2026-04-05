package com.gl.logicraft.registry;

import com.gl.logicraft.GLLogiCraft;
import com.gl.logicraft.blockentity.LogicChipBlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

/**
 * Registry class for all block entities added by GL_LogiCraft.
 */
public class ModBlockEntities {

    /**
     * Block entity type for LogicChipBlock.
     * Must be registered AFTER ModBlocks (so LOGIC_CHIP is already initialised).
     */
    public static final BlockEntityType<LogicChipBlockEntity> LOGIC_CHIP_BLOCK_ENTITY =
            Registry.register(
                    Registries.BLOCK_ENTITY_TYPE,
                    Identifier.of(GLLogiCraft.MOD_ID, "logic_chip"),
                    FabricBlockEntityTypeBuilder.create(LogicChipBlockEntity::new, ModBlocks.LOGIC_CHIP).build()
            );

    public static void register() {
        GLLogiCraft.LOGGER.info("Registering GL_LogiCraft Block Entities...");
    }
}
