package com.smartypantsltd.nylah.anim;

/**
 * One frame of her body: per-bone offsets from the rest pose.
 *
 * <p>pos and rot ADD to the bone's rest pivot and rotation (model units and
 * radians), scale MULTIPLIES (1 = rest), visible replaces. Flat arrays, reused
 * every frame, so animating her allocates nothing.</p>
 */
public final class Pose {

    public final Rig rig;
    public final float[] pos;
    public final float[] rot;
    public final float[] scale;
    public final boolean[] visible;

    public Pose(Rig rig) {
        this.rig = rig;
        int n = rig.size();
        pos = new float[n * 3];
        rot = new float[n * 3];
        scale = new float[n * 3];
        visible = new boolean[n];
        reset();
    }

    /** Back to her rest pose: no offsets, unit scale, expression planes hidden. */
    public void reset() {
        java.util.Arrays.fill(pos, 0f);
        java.util.Arrays.fill(rot, 0f);
        java.util.Arrays.fill(scale, 1f);
        for (int i = 0; i < visible.length; i++) {
            visible[i] = rig.visibleAtRest(i);
        }
    }

    public void copyFrom(Pose o) {
        System.arraycopy(o.pos, 0, pos, 0, pos.length);
        System.arraycopy(o.rot, 0, rot, 0, rot.length);
        System.arraycopy(o.scale, 0, scale, 0, scale.length);
        System.arraycopy(o.visible, 0, visible, 0, visible.length);
    }

    /**
     * Blend toward {@code o} by {@code w} (0 = stay, 1 = become o). Visibility
     * switches over at the halfway point, so a blink plane never half-exists.
     */
    public void blendToward(Pose o, float w) {
        if (w <= 0f) {
            return;
        }
        if (w >= 1f) {
            copyFrom(o);
            return;
        }
        for (int i = 0; i < pos.length; i++) {
            pos[i] += (o.pos[i] - pos[i]) * w;
            rot[i] += (o.rot[i] - rot[i]) * w;
            scale[i] += (o.scale[i] - scale[i]) * w;
        }
        if (w >= 0.5f) {
            System.arraycopy(o.visible, 0, visible, 0, visible.length);
        }
    }

    public void addRot(int bone, float x, float y, float z) {
        if (bone < 0) {
            return;
        }
        rot[bone * 3] += x;
        rot[bone * 3 + 1] += y;
        rot[bone * 3 + 2] += z;
    }

    public void addPos(int bone, float x, float y, float z) {
        if (bone < 0) {
            return;
        }
        pos[bone * 3] += x;
        pos[bone * 3 + 1] += y;
        pos[bone * 3 + 2] += z;
    }

    public void mulScale(int bone, float x, float y, float z) {
        if (bone < 0) {
            return;
        }
        scale[bone * 3] *= x;
        scale[bone * 3 + 1] *= y;
        scale[bone * 3 + 2] *= z;
    }

    public void setVisible(int bone, boolean v) {
        if (bone >= 0) {
            visible[bone] = v;
        }
    }
}
