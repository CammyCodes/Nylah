package com.smartypantsltd.nylah.mixin;

import com.smartypantsltd.nylah.Nylah;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code /nylah ...} is handled here on your client and cancelled, so it is
 * NEVER sent to the server. It is hidden: not added to the command list or
 * suggestions. Every other command passes through untouched.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void nylah$command(String command, CallbackInfo ci) {
        try {
            if (command != null && Nylah.get().onCommand(command)) {
                ci.cancel();
            }
        } catch (Throwable t) {
            Nylah.LOG.error("Nylah command failed", t);
        }
    }
}
