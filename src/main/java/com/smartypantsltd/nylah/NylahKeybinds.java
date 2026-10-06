package com.smartypantsltd.nylah;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * Her keys: real vanilla keybinds, listed and rebindable under
 * Options &gt; Controls &gt; Nylah. Defaults avoid vanilla's keys.
 */
public final class NylahKeybinds {

    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("nylah", "controls"));

    /** N: her menu (every action, and her settings). */
    public static final KeyMapping MENU = new KeyMapping("key.nylah.menu", 78, CATEGORY);
    /** H: "come here!" She runs over, chirps, and looks up at you. */
    public static final KeyMapping COME = new KeyMapping("key.nylah.come", 72, CATEGORY);
    /** Unbound: do something cute right now. */
    public static final KeyMapping CUTE = new KeyMapping("key.nylah.cute", -1, CATEGORY);
    /** Unbound: send her for a nap / call her back. */
    public static final KeyMapping NAP = new KeyMapping("key.nylah.nap", -1, CATEGORY);

    private NylahKeybinds() {
    }

    public static KeyMapping[] all() {
        return new KeyMapping[] {MENU, COME, CUTE, NAP};
    }

    /** Touching the class registers the mappings; the Options mixin calls this. */
    public static void init() {
        // Intentionally empty.
    }
}
