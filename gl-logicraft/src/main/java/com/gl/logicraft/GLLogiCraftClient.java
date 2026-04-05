package com.gl.logicraft;

import com.gl.logicraft.block.LogicChipBlock;
import com.gl.logicraft.gui.LogicScreen;
import com.gl.logicraft.registry.ModItems;
import com.gl.logicraft.registry.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

/**
 * Client-side initializer for GL_LogiCraft.
 * Registers the circuit editor screen and client-only event handlers.
 */
public class GLLogiCraftClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Fix: use explicit method reference so the compiler can resolve the generic type
        HandledScreens.<com.gl.logicraft.gui.LogicScreenHandler, LogicScreen>register(
                ModScreenHandlers.LOGIC_SCREEN,
                LogicScreen::new
        );

        GLLogiCraft.LOGGER.info("GL_LogiCraft client initialized.");

        // Glowing outline for Logic Chip blocks when holding the Wrench
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null) return;

            // Check if player holds wrench in main or off hand
            ItemStack main = client.player.getMainHandStack();
            ItemStack off = client.player.getOffHandStack();
            boolean holdingWrench = main.isOf(ModItems.WRENCH) || off.isOf(ModItems.WRENCH);
            if (!holdingWrench) return;

            // Scan blocks in a 10-block radius around the player
            BlockPos playerPos = client.player.getBlockPos();
            int radius = 10;
            for (BlockPos pos : BlockPos.iterate(
                    playerPos.add(-radius, -radius, -radius),
                    playerPos.add(radius, radius, radius))) {
                BlockState state = client.world.getBlockState(pos);
                if (!(state.getBlock() instanceof LogicChipBlock)) continue;

                // Render a colored outline box using the block's outline shape
                VoxelShape shape = state.getOutlineShape(client.world, pos, ShapeContext.absent());
                // Draw outline in color #4a9eff (same blue as palette selection highlight)
                MatrixStack matrices = context.matrixStack();
                matrices.push();
                Vec3d cam = context.camera().getPos();
                matrices.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);

                VertexConsumer lineConsumer = context.consumers().getBuffer(RenderLayer.getLines());
                VertexRendering.drawOutline(
                    matrices, lineConsumer, shape,
                    0, 0, 0,
                    0xFF4A9EFF  // ABGR or ARGB? Minecraft usually uses ARGB for standard utilities.
                );
                matrices.pop();
            }
        });
    }
}
