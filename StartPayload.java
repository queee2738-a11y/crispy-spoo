package com.example.watercube;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Sent client -> server when the player sneak + right-clicks. Carries no data. */
public record StartPayload() implements CustomPayload {
    public static final CustomPayload.Id<StartPayload> ID =
            new CustomPayload.Id<>(Identifier.of(WaterCubeMod.MOD_ID, "start"));
    public static final PacketCodec<RegistryByteBuf, StartPayload> CODEC =
            PacketCodec.unit(new StartPayload());

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
