package com.smartypantsltd.nylah.render;

import com.smartypantsltd.nylah.anim.Pose;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/** One frame of Nylah, extracted for drawing: her pose and which texture density. */
public final class NylahRenderState extends LivingEntityRenderState {

    /** Filled by the renderer from her animator each frame (allocated once per state). */
    public Pose pose;
    public Identifier texture;
}
