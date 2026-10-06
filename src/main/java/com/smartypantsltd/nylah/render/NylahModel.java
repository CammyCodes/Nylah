package com.smartypantsltd.nylah.render;

import com.smartypantsltd.nylah.anim.Pose;
import com.smartypantsltd.nylah.anim.Rig;
import com.smartypantsltd.nylah.model.Geo;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Her baked model. Each frame {@link #setupAnim} resets every part to rest
 * (Model.setupAnim does that, javap-verified) and then applies the animator's
 * pose on top: positions and rotations add, scale multiplies, visibility replaces.
 */
public final class NylahModel extends EntityModel<NylahRenderState> {

    private final ModelPart[] parts;

    public NylahModel(ModelPart root, Geo geo, Rig rig) {
        super(root);
        parts = new ModelPart[rig.size()];
        for (Geo.Bone b : geo.bones) {
            ModelPart parent = b.parent() == null ? root : parts[rig.indexOf(b.parent())];
            parts[rig.indexOf(b.name())] = parent.getChild(b.name());
        }
    }

    @Override
    public void setupAnim(NylahRenderState state) {
        super.setupAnim(state);
        Pose p = state.pose;
        if (p == null) {
            return;
        }
        for (int i = 0; i < parts.length; i++) {
            ModelPart m = parts[i];
            int k = i * 3;
            m.x += p.pos[k];
            m.y += p.pos[k + 1];
            m.z += p.pos[k + 2];
            m.xRot += p.rot[k];
            m.yRot += p.rot[k + 1];
            m.zRot += p.rot[k + 2];
            m.xScale *= p.scale[k];
            m.yScale *= p.scale[k + 1];
            m.zScale *= p.scale[k + 2];
            m.visible = p.visible[i];
        }
    }
}
