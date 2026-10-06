package com.smartypantsltd.nylah.mixin;

import com.smartypantsltd.nylah.Nylah;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Her heartbeat (after each client tick, once the level has ticked, so the
 * renderer interpolates from last tick's position to this one) and her
 * strokes (a right-click on her with an empty hand). A fault in her code must
 * never break the game, so both are wrapped.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Inject(method = "tick", at = @At("RETURN"))
    private void nylah$tick(CallbackInfo ci) {
        try {
            Nylah.get().tick((Minecraft) (Object) this);
        } catch (Throwable t) {
            Nylah.LOG.error("Nylah tick failed", t);
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void nylah$stroke(CallbackInfo ci) {
        try {
            if (Nylah.get().onUse((Minecraft) (Object) this)) {
                ci.cancel();
            }
        } catch (Throwable t) {
            Nylah.LOG.error("Nylah stroke failed", t);
        }
    }
}
