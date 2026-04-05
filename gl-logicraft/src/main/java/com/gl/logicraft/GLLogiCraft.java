package com.gl.logicraft;

import com.gl.logicraft.network.SaveCircuitPayload;
import com.gl.logicraft.registry.ModBlockEntities;
import com.gl.logicraft.registry.ModBlocks;
import com.gl.logicraft.registry.ModItems;
import com.gl.logicraft.gui.LogicScreenHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entrypoint for GL_LogiCraft mod.
 * Calls all registry classes in the correct order.
 */
public class GLLogiCraft implements ModInitializer {
    public static final String MOD_ID = "gllogicraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing GL_LogiCraft - Programmable Logic Blocks!");

        // Order matters: blocks first, then block entities (which reference blocks)
        ModBlocks.register();
        ModBlockEntities.register();

        ModItems.register();
        // ModScreenHandlers.register(); - Screen handlers are usually registered in ModScreenHandlers itself, but we can call it here if needed.
        // Wait, looking at the previous implementation, ModScreenHandlers.register() was not called here. I should call it.
        com.gl.logicraft.registry.ModScreenHandlers.register();

        // Register Networking
        PayloadTypeRegistry.playC2S().register(SaveCircuitPayload.ID, SaveCircuitPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SaveCircuitPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                LogicScreenHandler.handleSaveCircuit(payload, context.player());
            });
        });
    }
}
