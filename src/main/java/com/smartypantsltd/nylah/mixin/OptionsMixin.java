package com.smartypantsltd.nylah.mixin;

import com.smartypantsltd.nylah.NylahKeybinds;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lists her keys in Options &gt; Controls and saves them to options.txt.
 * {@code require = 0}: if this ever misses its target the keys still work
 * (KeyMapping registers itself), they just are not listed. Never a boot failure.
 */
@Mixin(Options.class)
public abstract class OptionsMixin {

    @Shadow @Final @Mutable
    public KeyMapping[] keyMappings;

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;load()V"), require = 0)
    private void nylah$registerKeys(CallbackInfo ci) {
        try {
            NylahKeybinds.init();
            KeyMapping[] ours = NylahKeybinds.all();
            KeyMapping[] merged = new KeyMapping[keyMappings.length + ours.length];
            System.arraycopy(keyMappings, 0, merged, 0, keyMappings.length);
            System.arraycopy(ours, 0, merged, keyMappings.length, ours.length);
            keyMappings = merged;
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
