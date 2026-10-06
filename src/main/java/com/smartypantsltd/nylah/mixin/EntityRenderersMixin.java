package com.smartypantsltd.nylah.mixin;

import com.smartypantsltd.nylah.Nylah;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla builds every entity renderer here on each resource reload
 * (EntityRenderDispatcher.onResourceManagerReload calls it, javap-verified).
 * We take the same context and build Nylah's renderer at the same moment.
 */
@Mixin(EntityRenderers.class)
public abstract class EntityRenderersMixin {

    @SuppressWarnings("rawtypes")
    @Inject(method = "createEntityRenderers", at = @At("HEAD"))
    private static void nylah$buildHers(EntityRendererProvider.Context ctx, CallbackInfoReturnable cir) {
        try {
            Nylah.get().onRendererContext(ctx);
        } catch (Throwable t) {
            Nylah.LOG.error("Nylah's renderer could not be built", t);
        }
    }
}
