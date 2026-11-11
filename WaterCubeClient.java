package com.example.watercube;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class WaterCubeClient implements ClientModInitializer {
    /** Set to true to only trigger with an empty main hand (avoids clashing with block placing). */
    private static final boolean REQUIRE_EMPTY_HAND = false;

    private boolean wasUsePressed = false;

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(WaterCubeMod.WATER_CUBE, WaterCubeRenderer::new);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean pressed = client.options.useKey.isPressed();
            if (pressed && !wasUsePressed
                    && client.player != null
                    && client.currentScreen == null
                    && client.player.isSneaking()
                    && (!REQUIRE_EMPTY_HAND || client.player.getMainHandStack().isEmpty())) {
                ClientPlayNetworking.send(new StartPayload());
            }
            wasUsePressed = pressed;
        });
    }
}
