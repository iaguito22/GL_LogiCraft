package com.gl.logicraft.registry;

import com.gl.logicraft.GLLogiCraft;
import com.gl.logicraft.gui.LogicScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

/**
 * Registry class for GUI screen handlers.
 */
public class ModScreenHandlers {

    /**
     * Screen handler type for the circuit editor.
     * Uses ExtendedScreenHandlerType so the server can pass circuit data to the client.
     * NBT_COMPOUND codec is used to transmit the pos + circuit layout.
     */
    public static final ScreenHandlerType<LogicScreenHandler> LOGIC_SCREEN =
            Registry.register(
                    Registries.SCREEN_HANDLER,
                    Identifier.of(GLLogiCraft.MOD_ID, "logic_screen"),
                    new ExtendedScreenHandlerType<>(
                            (syncId, inv, data) -> new LogicScreenHandler(syncId, inv, data),
                            PacketCodecs.NBT_COMPOUND
                    )
            );

    public static void register() {
        GLLogiCraft.LOGGER.info("Registering GL_LogiCraft Screen Handlers...");
    }
}
