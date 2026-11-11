package com.example.watercube;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.fluid.FluidState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WaterCubeMod implements ModInitializer {
    public static final String MOD_ID = "watercube";

    /** How far (in blocks) from the player we look for water. */
    public static final int WATER_RANGE = 6;

    public static final EntityType<WaterCubeEntity> WATER_CUBE = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(MOD_ID, "water_cube"),
            FabricEntityTypeBuilder.<WaterCubeEntity>create(SpawnGroup.MISC, WaterCubeEntity::new)
                    .dimensions(EntityDimensions.fixed(0.8f, 0.8f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(1)
                    .forceTrackedVelocityUpdates(true)
                    .disableSaving()
                    .build());

    /** One active cube per player. */
    private static final Map<UUID, WaterCubeEntity> ACTIVE = new HashMap<>();

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(StartPayload.ID, StartPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(StartPayload.ID,
                (payload, context) -> trySpawnCube(context.player()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ACTIVE.clear());
    }

    private static void trySpawnCube(ServerPlayerEntity player) {
        if (!player.isSneaking() || player.isSpectator()) return;
        if (!(player.getWorld() instanceof ServerWorld world)) return;

        WaterCubeEntity existing = ACTIVE.get(player.getUuid());
        if (existing != null && !existing.isRemoved()) return;

        BlockPos water = findWater(world, player.getBlockPos());
        if (water == null) return;

        // Climb to the surface of the water column.
        for (int i = 0; i < 16; i++) {
            FluidState above = world.getFluidState(water.up());
            if (!above.isIn(FluidTags.WATER)) break;
            water = water.up();
        }

        WaterCubeEntity cube = new WaterCubeEntity(WATER_CUBE, world);
        cube.setOwner(player);
        cube.setPosition(water.getX() + 0.5, water.getY() + 0.9, water.getZ() + 0.5);
        world.spawnEntity(cube);
        ACTIVE.put(player.getUuid(), cube);

        world.spawnParticles(ParticleTypes.SPLASH, cube.getX(), cube.getY() + 0.4, cube.getZ(),
                40, 0.4, 0.2, 0.4, 0.1);
        world.playSound(null, cube.getX(), cube.getY(), cube.getZ(),
                SoundEvents.ENTITY_PLAYER_SPLASH, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    private static BlockPos findWater(ServerWorld world, BlockPos origin) {
        for (BlockPos pos : BlockPos.iterateOutwards(origin, WATER_RANGE, WATER_RANGE, WATER_RANGE)) {
            if (world.getFluidState(pos).isIn(FluidTags.WATER)) {
                return pos.toImmutable();
            }
        }
        return null;
    }
}
