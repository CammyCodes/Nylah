package com.smartypantsltd.nylah.anim;

import java.util.ArrayList;
import java.util.List;

/**
 * One keyframed animation (a posture or an action), sampled at any time.
 *
 * <p>The maths here has a JavaScript twin in {@code tools/preview/clip.js} so
 * the browser preview shows exactly what the game plays. Between keys the
 * curve is a monotone cubic: smooth, and it never overshoots a key, so a paw
 * lifted to a pose stops at that pose instead of wobbling past it. A
 * non-looping clip eases in and out of its first and last keys.</p>
 */
public final class Clip {

    /** pos / rot / scale keys for one bone. {@code times[k]}, {@code values[k*3 .. k*3+2]}. */
    public static final class Track {
        final float[] times;
        final float[] values;

        public Track(float[] times, float[] values) {
            this.times = times;
            this.values = values;
        }
    }

    /** Step keys for one bone's visibility. */
    public static final class VisTrack {
        final float[] times;
        final boolean[] values;

        public VisTrack(float[] times, boolean[] values) {
            this.times = times;
            this.values = values;
        }
    }

    /** Something to do at a tick: a sound, mostly ("purr", "meow", "trill"...). */
    public record Event(float time, String name) {
    }

    public enum Kind { POSTURE, ACTION }

    public final String name;
    public final Kind kind;
    public final String posture;
    public final float length;
    public final boolean loop;
    public final float blendIn;
    public final float blendOut;
    /** How much the look-at layer may turn her head during this clip (0..1). */
    public final float lookAt;
    final Track[] pos;
    final Track[] rot;
    final Track[] scale;
    final VisTrack[] vis;
    public final List<Event> events;

    public Clip(String name, Kind kind, String posture, float length, boolean loop, float blendIn, float blendOut,
                float lookAt, Track[] pos, Track[] rot, Track[] scale, VisTrack[] vis, List<Event> events) {
        this.name = name;
        this.kind = kind;
        this.posture = posture;
        this.length = Math.max(1f, length);
        this.loop = loop;
        this.blendIn = blendIn;
        this.blendOut = blendOut;
        this.lookAt = lookAt;
        this.pos = pos;
        this.rot = rot;
        this.scale = scale;
        this.vis = vis;
        this.events = events == null ? new ArrayList<>() : events;
    }

    /** Write this clip at time {@code t} (ticks) into {@code out}, starting from rest. */
    public void sample(float t, Pose out) {
        out.reset();
        int n = out.rig.size();
        for (int b = 0; b < n; b++) {
            if (pos[b] != null) {
                for (int c = 0; c < 3; c++) {
                    out.pos[b * 3 + c] = value(pos[b], t, c);
                }
            }
            if (rot[b] != null) {
                for (int c = 0; c < 3; c++) {
                    out.rot[b * 3 + c] = value(rot[b], t, c);
                }
            }
            if (scale[b] != null) {
                for (int c = 0; c < 3; c++) {
                    out.scale[b * 3 + c] = value(scale[b], t, c);
                }
            }
            if (vis[b] != null) {
                out.visible[b] = visible(vis[b], t, out.visible[b]);
            }
        }
    }

    float wrap(float t) {
        return loop ? (((t % length) + length) % length) : t;
    }

    float value(Track tr, float t, int c) {
        int n = tr.times.length;
        if (n == 0) {
            return 0f;
        }
        if (n == 1) {
            return tr.values[c];
        }
        t = wrap(t);
        if (!loop && t <= tr.times[0]) {
            return tr.values[c];
        }
        if (!loop && t >= tr.times[n - 1]) {
            return tr.values[(n - 1) * 3 + c];
        }
        int i = -1;
        for (int k = 0; k < n; k++) {
            if (tr.times[k] <= t) {
                i = k;
            }
        }
        if (i == -1 || i == n - 1) {
            // Looping wrap: last key -> first key, across the end of the clip.
            float span = (tr.times[0] + length) - tr.times[n - 1];
            float tt = i == -1 ? t + length : t;
            return segment(tr, n - 1, 0, span > 0 ? (tt - tr.times[n - 1]) / span : 1f, c);
        }
        float span = tr.times[i + 1] - tr.times[i];
        return segment(tr, i, i + 1, span > 0 ? (t - tr.times[i]) / span : 1f, c);
    }

    private float slope(Track tr, int i, int c) {
        int n = tr.times.length;
        float pt;
        float pv;
        float nt;
        float nv;
        if (i > 0) {
            pt = tr.times[i - 1];
            pv = tr.values[(i - 1) * 3 + c];
        } else if (loop) {
            pt = tr.times[n - 1] - length;
            pv = tr.values[(n - 1) * 3 + c];
        } else {
            return 0f; // the clip eases in
        }
        if (i < n - 1) {
            nt = tr.times[i + 1];
            nv = tr.values[(i + 1) * 3 + c];
        } else if (loop) {
            nt = tr.times[0] + length;
            nv = tr.values[c];
        } else {
            return 0f; // and eases out
        }
        float v = tr.values[i * 3 + c];
        if ((v - pv) * (nv - v) <= 0f) {
            return 0f; // a peak or a valley: flat there, so nothing overshoots
        }
        float d = nt - pt;
        return d > 0 ? (nv - pv) / d : 0f;
    }

    private float segment(Track tr, int i0, int i1, float u, int c) {
        float span = tr.times[i1] - tr.times[i0];
        if (span <= 0) {
            span += length;
        }
        float m0 = slope(tr, i0, c) * span;
        float m1 = slope(tr, i1, c) * span;
        float p0 = tr.values[i0 * 3 + c];
        float p1 = tr.values[i1 * 3 + c];
        float u2 = u * u;
        float u3 = u2 * u;
        return (2 * u3 - 3 * u2 + 1) * p0 + (u3 - 2 * u2 + u) * m0 + (-2 * u3 + 3 * u2) * p1 + (u3 - u2) * m1;
    }

    private boolean visible(VisTrack v, float t, boolean dflt) {
        if (v.times.length == 0) {
            return dflt;
        }
        t = wrap(t);
        boolean out = dflt;
        for (int k = 0; k < v.times.length; k++) {
            if (v.times[k] <= t) {
                out = v.values[k];
            }
        }
        return out;
    }
}
