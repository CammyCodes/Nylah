package com.smartypantsltd.nylah;

import net.fabricmc.api.ClientModInitializer;

/** Fabric entrypoint. Nylah is client-only; everything starts from {@link Nylah}. */
public final class NylahMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Nylah.init();
    }
}
