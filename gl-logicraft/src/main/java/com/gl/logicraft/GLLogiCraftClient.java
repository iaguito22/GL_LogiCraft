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
import net.minecraft.block.entity.BlockEntity;
import com.gl.logicraft.blockentity.LogicChipBlockEntity;
import net.minecraft.util.math.Direction;
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
                Direction blockedDir = null;
                BlockEntity be = client.world.getBlockEntity(pos);
                if (be instanceof LogicChipBlockEntity chip) {
                    blockedDir = chip.lastSignalOrigin;
                }

                // Draw outline in color #4a9eff (same blue as palette selection highlight)
                MatrixStack matrices = context.matrixStack();
                matrices.push();
                Vec3d cam = context.camera().getPos();
                matrices.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);

                VertexConsumer lineConsumer = context.consumers().getBuffer(RenderLayer.getLines());
                VertexRendering.drawOutline(
                    matrices, lineConsumer, shape,
                    0, 0, 0,
                    0xFF4A9EFF
                );
                
                if (blockedDir != null) {
                    net.minecraft.util.math.Box box = shape.getBoundingBox();
                    int r = 0xCC, g = 0x22, b = 0x00, a = 0xFF;
                    float minX = (float) box.minX; float minY = (float) box.minY; float minZ = (float) box.minZ;
                    float maxX = (float) box.maxX; float maxY = (float) box.maxY; float maxZ = (float) box.maxZ;
                    MatrixStack.Entry entry = matrices.peek();
                    
                    switch (blockedDir) {
                        case NORTH -> {
                            drawLine(entry, lineConsumer, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
                            drawLine(entry, lineConsumer, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
                            drawLine(entry, lineConsumer, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
                            drawLine(entry, lineConsumer, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
                        }
                        case SOUTH -> {
                            drawLine(entry, lineConsumer, minX, minY, maxZ, maxX, minY, maxZ, r, g, b, a);
                            drawLine(entry, lineConsumer, minX, maxY, maxZ, maxX, maxY, maxZ, r, g, b, a);
                            drawLine(entry, lineConsumer, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
                            drawLine(entry, lineConsumer, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
                        }
                        case WEST -> {
                            drawLine(entry, lineConsumer, minX, minY, minZ, minX, minY, maxZ, r, g, b, a);
                            drawLine(entry, lineConsumer, minX, maxY, minZ, minX, maxY, maxZ, r, g, b, a);
                            drawLine(entry, lineConsumer, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
                            drawLine(entry, lineConsumer, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
                        }
                        case EAST -> {
                            drawLine(entry, lineConsumer, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
                            drawLine(entry, lineConsumer, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
                            drawLine(entry, lineConsumer, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
                            drawLine(entry, lineConsumer, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
                        }
                        case DOWN -> {
                            drawLine(entry, lineConsumer, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
                            drawLine(entry, lineConsumer, minX, minY, maxZ, maxX, minY, maxZ, r, g, b, a);
                            drawLine(entry, lineConsumer, minX, minY, minZ, minX, minY, maxZ, r, g, b, a);
                            drawLine(entry, lineConsumer, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
                        }
                        case UP -> {
                            drawLine(entry, lineConsumer, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
                            drawLine(entry, lineConsumer, minX, maxY, maxZ, maxX, maxY, maxZ, r, g, b, a);
                            drawLine(entry, lineConsumer, minX, maxY, minZ, minX, maxY, maxZ, r, g, b, a);
                            drawLine(entry, lineConsumer, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
                        }
                    }
                }
                matrices.pop();
            }
        });
    }

    private static void drawLine(MatrixStack.Entry entry, VertexConsumer vc, float x1, float y1, float z1, float x2, float y2, float z2, int r, int g, int b, int a) {
        float dx = x2 - x1; float dy = y2 - y1; float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= len; dy /= len; dz /= len;
        vc.vertex(entry.getPositionMatrix(), x1, y1, z1).color(r, g, b, a).normal(entry, dx, dy, dz);
        vc.vertex(entry.getPositionMatrix(), x2, y2, z2).color(r, g, b, a).normal(entry, dx, dy, dz);
    }
}
