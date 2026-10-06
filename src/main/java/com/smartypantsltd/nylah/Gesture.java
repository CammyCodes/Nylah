package com.smartypantsltd.nylah;

import com.smartypantsltd.nylah.entity.NylahCat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Stroking her: a right-click with an empty hand, aimed at her.
 *
 * <p>She is never pickable, so the game's own crosshair never sees her; this
 * does its own ray test against her box. The decision is a pure function so
 * every case can be tested. Only right-click is ever considered, and the click
 * is only consumed for a stroke: anything in your hand, anything between you
 * and her, any open screen, and the click passes straight on as normal.</p>
 */
public final class Gesture {

    public static final double REACH = 4.0;

    public enum Outcome { PASS, STROKE }

    public enum Region { HEAD, BODY }

    private Gesture() {
    }

    /** The whole decision, from plain values. */
    public static Outcome decide(boolean present, boolean screenOpen, boolean rayHitsHer, double herDistance,
                                 double blockDistance, boolean handEmpty) {
        if (!present || screenOpen || !rayHitsHer) {
            return Outcome.PASS;
        }
        if (blockDistance < herDistance - 0.05) {
            return Outcome.PASS;           // a block is in the way: that block gets the click
        }
        if (!handEmpty) {
            return Outcome.PASS;           // holding something: use it as normal
        }
        return Outcome.STROKE;
    }

    /** Where on her a stroke lands, from the hit point (her head is the front-top). */
    public static Region region(double hitY, double minY, double maxY, double alongFacing) {
        double h = (hitY - minY) / Math.max(1e-3, maxY - minY);
        return (h > 0.5 && alongFacing > 0.0) ? Region.HEAD : Region.BODY;
    }

    /** Gather the facts and decide; returns the region when it is a stroke, else null. */
    static Region evaluate(Minecraft mc, NylahCat cat) {
        if (mc.player == null || cat == null) {
            return null;
        }
        Vec3 eye = mc.player.getEyePosition();
        Vec3 end = eye.add(mc.player.getLookAngle().scale(REACH));
        AABB box = cat.getBoundingBox().inflate(0.15);
        Optional<Vec3> hit = box.clip(eye, end);
        double herDist = hit.map(eye::distanceTo).orElse(Double.MAX_VALUE);
        double blockDist = mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK
                ? eye.distanceTo(mc.hitResult.getLocation()) : Double.MAX_VALUE;
        Outcome o = decide(true, mc.screen != null, hit.isPresent(), herDist, blockDist,
                mc.player.getMainHandItem().isEmpty());
        if (o != Outcome.STROKE) {
            return null;
        }
        Vec3 p = hit.get();
        double yaw = Math.toRadians(cat.getYRot());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double along = (p.x - cat.getX()) * fx + (p.z - cat.getZ()) * fz;
        return region(p.y, box.minY, box.maxY, along);
    }
}
