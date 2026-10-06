package com.smartypantsltd.nylah.anim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The animation core, against her real rig and every real clip. */
class AnimTest {

    static Rig rig;
    static Map<String, Clip> clips;
    static JsonObject geo;

    @BeforeAll
    static void load() throws Exception {
        try (var in = AnimTest.class.getResourceAsStream("/assets/nylah/geo/nylah.geo.json")) {
            geo = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        List<String> names = new ArrayList<>();
        List<String> groups = new ArrayList<>();
        for (JsonElement e : geo.getAsJsonArray("bones")) {
            names.add(e.getAsJsonObject().get("name").getAsString());
            groups.add(e.getAsJsonObject().get("group").getAsString());
        }
        rig = new Rig(names, groups);
        clips = ClipLibrary.load(rig);
    }

    @Test
    void everyClipNamesOnlyRealBones() {
        JsonArray index = ClipLibrary.read("/assets/nylah/anim/index.json").getAsJsonArray();
        for (JsonElement e : index) {
            JsonObject c = ClipLibrary.read("/assets/nylah/anim/" + e.getAsString() + ".json").getAsJsonObject();
            for (String bone : c.getAsJsonObject("tracks").keySet()) {
                assertTrue(rig.indexOf(bone) >= 0, e.getAsString() + " names unknown bone " + bone);
            }
            for (String bone : c.getAsJsonObject("vis").keySet()) {
                assertTrue(rig.indexOf(bone) >= 0, e.getAsString() + " shows unknown bone " + bone);
            }
        }
    }

    @Test
    void everyActionsPostureExists() {
        for (Clip c : clips.values()) {
            Clip p = clips.get(c.posture);
            assertNotNull(p, c.name + " sits in missing posture " + c.posture);
            assertEquals(Clip.Kind.POSTURE, p.kind, c.name + "'s posture is not a posture");
        }
    }

    @Test
    void theSignatureAnimationsExist() {
        for (String must : List.of("blep", "look_up", "slow_blink", "leg_rub", "stretch_front", "stretch_back",
                "stretch_long", "roll_wiggle", "belly_reach", "lick_lips", "groom_paw", "yawn", "head_bonk",
                "knead", "chin_scratch", "sit", "loaf", "belly_up", "curl")) {
            assertTrue(clips.containsKey(must), "missing " + must);
        }
    }

    @Test
    void noClipEverProducesNaN() {
        Pose p = new Pose(rig);
        for (Clip c : clips.values()) {
            for (float t = -5; t <= c.length + 5; t += 0.37f) {
                c.sample(t, p);
                for (float v : p.pos) {
                    assertFalse(Float.isNaN(v), c.name);
                }
                for (float v : p.rot) {
                    assertFalse(Float.isNaN(v), c.name);
                }
                for (float v : p.scale) {
                    assertFalse(Float.isNaN(v), c.name);
                }
            }
        }
    }

    @Test
    void curvesNeverOvershootTheirKeys() {
        // Between any two keys, every channel stays within the range of the whole track.
        Clip.Track tr = new Clip.Track(new float[] {0, 10, 20, 30}, new float[] {0, 0, 0, 1, 0, 0, 0.2f, 0, 0, 0.9f, 0, 0});
        Clip c = new Clip("t", Clip.Kind.ACTION, "stand", 30, false, 0, 0, 0,
                new Clip.Track[] {tr}, new Clip.Track[1], new Clip.Track[1], new Clip.VisTrack[1], null);
        for (float t = 0; t <= 30; t += 0.1f) {
            float v = c.value(tr, t, 0);
            assertTrue(v >= -1e-4 && v <= 1.0001, "overshoot at " + t + ": " + v);
        }
        assertEquals(0f, c.value(tr, 0, 0), 1e-5);
        assertEquals(1f, c.value(tr, 10, 0), 1e-5);
        assertEquals(0.9f, c.value(tr, 30, 0), 1e-5);
    }

    @Test
    void loopingClipsJoinUpSmoothly() {
        Pose a = new Pose(rig);
        Pose b = new Pose(rig);
        for (Clip c : clips.values()) {
            if (!c.loop) {
                continue;
            }
            c.sample(c.length - 0.001f, a);
            c.sample(0.001f, b);
            for (int i = 0; i < a.rot.length; i++) {
                assertEquals(a.rot[i], b.rot[i], 0.02f, c.name + " jumps at its loop point");
            }
        }
    }

    @Test
    void everyActionPlaysAndEndsBackInItsPosture() {
        for (Clip c : clips.values()) {
            if (c.kind != Clip.Kind.ACTION) {
                continue;
            }
            Animator a = new Animator(rig, clips);
            assertTrue(a.play(c.name, 0));
            assertTrue(a.busy());
            for (int i = 0; i < c.length + 2; i++) {
                a.tick();
            }
            assertFalse(a.busy(), c.name + " never finished");
            assertEquals(c.posture, a.posture(), c.name + " left her in the wrong posture");
        }
    }

    @Test
    void loopEventsFireOncePerCycle() {
        Animator a = new Animator(rig, clips);
        Clip knead = clips.get("knead");
        a.play("knead", knead.length * 3);
        int purrs = 0;
        for (int i = 0; i < knead.length * 3; i++) {
            a.tick();
            for (String e = a.pollEvent(); e != null; e = a.pollEvent()) {
                if (e.equals("purr")) {
                    purrs++;
                }
            }
        }
        assertEquals(3, purrs, "one purr cue per cycle, three cycles");
    }

    @Test
    void lookAtSettlesWithoutSwingingPast() {
        LookAt l = new LookAt();
        l.aim(1.0f, -0.8f);
        float maxYaw = 0;
        float minPitch = 0;
        for (int i = 0; i < 200; i++) {
            l.tick();
            maxYaw = Math.max(maxYaw, l.yaw(1f));
            minPitch = Math.min(minPitch, l.pitch(1f));
        }
        assertEquals(1.0f, l.yaw(1f), 0.01f);
        assertTrue(maxYaw <= 1.0f + 0.01f, "yaw overshot: " + maxYaw);
        assertTrue(minPitch >= -0.8f - 0.01f, "pitch overshot: " + minPitch);
    }

    @Test
    void lookSplitRespectsLimitsAndYawsTheHead() {
        float[] s = LookAt.split(3f, -3f);
        assertEquals(LookAt.MAX_YAW, s[0], 1e-6);
        assertTrue(s[1] >= -0.6f && s[2] >= -0.75f);
        float[] up = LookAt.split(0f, -1.0f);
        assertTrue(up[1] < 0 && up[2] < 0, "looking up should tip both neck and head back");
    }

    @Test
    void gaitWeightsAlwaysSumToOne() {
        for (float v = 0; v < 0.4f; v += 0.005f) {
            float[] w = Gait.weights(v);
            assertEquals(1f, w[0] + w[1] + w[2], 1e-4, "at speed " + v);
        }
        assertTrue(Gait.weights(0.03f)[0] > 0.99f);
        assertTrue(Gait.weights(0.3f)[2] > 0.99f);
    }

    @Test
    void sampledPosesStayFiniteThroughAWholeDay() {
        Animator a = new Animator(rig, clips);
        Pose p = new Pose(rig);
        String[] script = {"sit", "blep", "loaf", "knead", "belly_up", "roll_wiggle", "stand", "pounce", "curl", "sleep_blep"};
        for (int i = 0; i < 4000; i++) {
            if (i % 200 == 0) {
                a.play(script[(i / 200) % script.length], 0);
            }
            a.setMotion(i % 600 < 200 ? (i % 600) / 1000f : 0f, i % 97 == 0);
            a.lookAt((float) Math.sin(i * 0.01), (float) Math.cos(i * 0.013) - 0.5f);
            a.setBlep(i % 300 < 150);
            a.setHappy(i % 500 < 100);
            a.setPurring(i % 400 < 200);
            a.tick();
            a.sample(0.5f, p);
            for (float v : p.rot) {
                assertFalse(Float.isNaN(v) || Float.isInfinite(v));
            }
        }
    }
}
