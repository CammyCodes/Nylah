package com.smartypantsltd.nylah.mixin;

import com.smartypantsltd.nylah.Nylah;
import com.smartypantsltd.nylah.entity.NylahCat;
import com.smartypantsltd.nylah.render.NylahRenderState;
import com.smartypantsltd.nylah.render.NylahRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hands out Nylah's renderer for her entity and her render state.
 *
 * <p>She has no entity type of her own, so vanilla's renderer table cannot
 * hold her renderer. Both lookups are intercepted (26.1 looks a renderer up by
 * entity when extracting and by render state when drawing, javap-verified).
 * If her renderer failed to build, both fall through to vanilla's.</p>
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;",
            at = @At("HEAD"), cancellable = true)
    private void nylah$rendererForHer(Entity entity, CallbackInfoReturnable cir) {
        if (entity instanceof NylahCat) {
            NylahRenderer r = Nylah.get().renderer();
            if (r != null) {
                cir.setReturnValue(r);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "getRenderer(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)Lnet/minecraft/client/renderer/entity/EntityRenderer;",
            at = @At("HEAD"), cancellable = true)
    private void nylah$rendererForHerState(EntityRenderState state, CallbackInfoReturnable cir) {
        if (state instanceof NylahRenderState) {
            NylahRenderer r = Nylah.get().renderer();
            if (r != null) {
                cir.setReturnValue(r);
            }
        }
    }
}
