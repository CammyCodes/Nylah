package com.smartypantsltd.nylah.compat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.feline.Ocelot;

/**
 * The few Minecraft calls that differ between the versions Nylah is built for.
 * This is the 26.1 copy; {@code src/mc26.2} holds the 26.2 one. Gradle compiles
 * exactly one of them ({@code -Pmc=...}), and both must keep the same methods.
 */
public final class Mc {

    private Mc() {}

    public static Screen screen(Minecraft mc) {
        return mc.screen;
    }

    public static void setScreen(Minecraft mc, Screen screen) {
        mc.setScreen(screen);
    }

    public static Camera camera(Minecraft mc) {
        return mc.gameRenderer.getMainCamera();
    }

    public static ChatComponent chat(Minecraft mc) {
        return mc.gui.getChat();
    }

    public static EntityType<Ocelot> ocelot() {
        return EntityType.OCELOT;
    }
}
