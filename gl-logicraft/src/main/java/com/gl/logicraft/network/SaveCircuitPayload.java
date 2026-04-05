package com.gl.logicraft.network;

import com.gl.logicraft.GLLogiCraft;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * C2S packet: sent when the player closes the circuit editor GUI.
 * Carries the BlockPos of the chip and the full serialized circuit (GuiComponents + Wires).
 */
public record SaveCircuitPayload(BlockPos pos, NbtCompound circuit) implements CustomPayload {

    public static final Id<SaveCircuitPayload> ID =
            new Id<>(Identifier.of(GLLogiCraft.MOD_ID, "save_circuit"));

    public static final PacketCodec<RegistryByteBuf, SaveCircuitPayload> CODEC =
            PacketCodec.tuple(
                    BlockPos.PACKET_CODEC,       SaveCircuitPayload::pos,
                    PacketCodecs.NBT_COMPOUND,   SaveCircuitPayload::circuit,
                    SaveCircuitPayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
