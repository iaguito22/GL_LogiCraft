package com.gl.logicraft.gui;

import com.gl.logicraft.blockentity.LogicChipBlockEntity;
import com.gl.logicraft.circuit.GuiComponent;
import com.gl.logicraft.circuit.Wire;
import com.gl.logicraft.network.SaveCircuitPayload;
import com.gl.logicraft.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen handler for the circuit editor GUI.
 *
 * Server constructor: uses BlockPos to locate the BlockEntity directly.
 * Client constructor: receives NbtCompound (from ExtendedScreenHandlerType) with pos + circuit.
 *
 * On GUI close (client): sends SaveCircuitPayload C2S to persist the circuit.
 * On server receive: deserializes into LogicChipBlockEntity and re-evaluates.
 */
public class LogicScreenHandler extends ScreenHandler {

    private final BlockPos chipPos;

    // Circuit data held client-side for rendering in LogicScreen
    private final List<GuiComponent> guiComponents = new ArrayList<>();
    private final List<Wire>         wires         = new ArrayList<>();
    public final boolean[]           startingInputs = new boolean[5];

    // -----------------------------------------------------------------------
    // Server-side constructor (called by WrenchItem.useOnBlock createMenu)
    // -----------------------------------------------------------------------
    public LogicScreenHandler(int syncId, PlayerInventory inv, BlockPos pos) {
        super(ModScreenHandlers.LOGIC_SCREEN, syncId);
        this.chipPos = pos;
    }

    // -----------------------------------------------------------------------
    // Client-side constructor (called by Fabric with ExtendedScreenHandlerType data)
    // -----------------------------------------------------------------------
    public LogicScreenHandler(int syncId, PlayerInventory inv, NbtCompound data) {
        super(ModScreenHandlers.LOGIC_SCREEN, syncId);
        this.chipPos = new BlockPos(
                data.getInt("posX"),
                data.getInt("posY"),
                data.getInt("posZ")
        );
        if (data.contains("GuiData")) {
            NbtCompound gui = data.getCompound("GuiData");

            NbtList gcList = gui.getList("GuiComponents", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < gcList.size(); i++) {
                guiComponents.add(GuiComponent.fromNbt(gcList.getCompound(i)));
            }

            NbtList wList = gui.getList("Wires", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < wList.size(); i++) {
                wires.add(Wire.fromNbt(wList.getCompound(i)));
            }
            if (gui.contains("CircuitInputs")) {
                byte[] ins = gui.getByteArray("CircuitInputs");
                for (int i = 0; i < ins.length && i < startingInputs.length; i++) {
                    startingInputs[i] = (ins[i] != 0);
                }
            }
        }

    }

    // -----------------------------------------------------------------------
    // Accessors used by LogicScreen renderer
    // -----------------------------------------------------------------------

    public BlockPos getChipPos()             { return chipPos; }
    public List<GuiComponent> getGuiComponents() { return guiComponents; }
    public List<Wire>         getWires()         { return wires; }

    // -----------------------------------------------------------------------
    // Called from LogicScreen when player closes — sends data to server
    // -----------------------------------------------------------------------

    public void saveToServer() {
        NbtCompound guiData = new NbtCompound();

        NbtList gcList = new NbtList();
        for (GuiComponent gc : guiComponents) gcList.add(gc.toNbt());
        guiData.put("GuiComponents", gcList);

        NbtList wList = new NbtList();
        for (Wire w : wires) wList.add(w.toNbt());
        guiData.put("Wires", wList);

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
                .send(new SaveCircuitPayload(chipPos, guiData));
    }

    // -----------------------------------------------------------------------
    // Server-side: apply received circuit data to the block entity
    // -----------------------------------------------------------------------

    public static void handleSaveCircuit(SaveCircuitPayload payload, net.minecraft.server.network.ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        if (!(sw.getBlockEntity(payload.pos()) instanceof LogicChipBlockEntity chip)) return;

        chip.deserializeGuiData(payload.circuit());
        chip.evaluate();
    }

    // -----------------------------------------------------------------------
    // ScreenHandler boilerplate
    // -----------------------------------------------------------------------

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }
}
