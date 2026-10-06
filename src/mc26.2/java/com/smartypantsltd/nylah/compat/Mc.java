package com.smartypantsltd.nylah.compat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.feline.Ocelot;

/**
 * The few Minecraft calls that differ between the versions Nylah is built for.
 * This is the 26.2 copy; {@code src/mc26.1} holds the 26.1 one. Gradle compiles
 * exactly one of them ({@code -Pmc=...}), and both must keep the same methods.
 *
 * <p>26.2 (javap-verified): screens moved from {@code Minecraft} to {@code Gui},
 * the chat to {@code Gui.hud}, entity-type constants to {@code EntityTypes}, and
 * {@code GameRenderer.getMainCamera()} became {@code mainCamera()}.</p>
 */
public final class Mc {

    private Mc() {}

    public static Screen screen(Minecraft mc) {
        return mc.gui.screen();
    }

    public static void setScreen(Minecraft mc, Screen screen) {
        mc.gui.setScreen(screen);
    }

    public static Camera camera(Minecraft mc) {
        return mc.gameRenderer.mainCamera();
    }

    public static ChatComponent chat(Minecraft mc) {
        return mc.gui.hud.getChat();
    }

    public static EntityType<Ocelot> ocelot() {
        return EntityTypes.OCELOT;
    }
}
