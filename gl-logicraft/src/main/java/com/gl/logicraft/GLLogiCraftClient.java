package com.gl.logicraft;

import com.gl.logicraft.gui.LogicScreen;
import com.gl.logicraft.registry.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

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
    }
}
