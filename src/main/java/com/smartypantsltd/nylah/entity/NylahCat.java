package com.smartypantsltd.nylah.entity;

import com.smartypantsltd.nylah.anim.Animator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.feline.Ocelot;

/**
 * Nylah in the world: a client-only entity the server never hears about.
 *
 * <p>She is an {@link Ocelot} underneath only because an entity needs a type;
 * her body is drawn entirely by {@code NylahRenderer} from her own model. Ocelot
 * rather than Cat on purpose: a Cat reads its coat from a server-synced
 * registry when it is constructed, which a client-only entity must not depend
 * on. She plays real cat sounds anyway, from the static cat sound sets.</p>
 *
 * <p>She is moved by hand ({@code Mover} sets her position each tick): no
 * physics, no gravity, no goals, no collisions.</p>
 */
public final class NylahCat extends Ocelot {

    private final Animator animator;

    public NylahCat(ClientLevel level, Animator animator) {
        super(EntityType.OCELOT, level);
        this.animator = animator;
        setSilent(true);
        setInvulnerable(true);
        setNoGravity(true);
        noPhysics = true;
    }

    public Animator animator() {
        return animator;
    }

    /**
     * NEVER pickable, unconditionally. If the crosshair could pick her, the next
     * click would send the server an interact/attack packet about an entity id
     * that does not exist (the hard-won rule from the pet fox next door). Clicks
     * on her are found by our own ray test in {@code Gesture} instead. Do not
     * make this conditional, ever.
     */
    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** Her voice is ours (see {@code Voice}); the vanilla ambient sound is muted. */
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }
}
