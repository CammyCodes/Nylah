package com.smartypantsltd.nylah.anim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads every clip in {@code assets/nylah/anim/} (written by
 * {@code tools/anim/build_clips.py}) and binds it to her rig.
 * Read from our own jar via the classloader, never the resource manager.
 */
public final class ClipLibrary {

    private ClipLibrary() {
    }

    /** All clips, in the index's order (which is also the debug reel's order). */
    public static Map<String, Clip> load(Rig rig) {
        Map<String, Clip> out = new LinkedHashMap<>();
        JsonArray index = read("/assets/nylah/anim/index.json").getAsJsonArray();
        for (JsonElement e : index) {
            String name = e.getAsString();
            out.put(name, parse(read("/assets/nylah/anim/" + name + ".json").getAsJsonObject(), rig));
        }
        return out;
    }

    static JsonElement read(String path) {
        try (InputStream in = ClipLibrary.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException(path + " missing from the jar");
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not read " + path, ex);
        }
    }

    static Clip parse(JsonObject o, Rig rig) {
        int n = rig.size();
        Clip.Track[] pos = new Clip.Track[n];
        Clip.Track[] rot = new Clip.Track[n];
        Clip.Track[] scale = new Clip.Track[n];
        Clip.VisTrack[] vis = new Clip.VisTrack[n];
        JsonObject tracks = o.getAsJsonObject("tracks");
        for (String bone : tracks.keySet()) {
            int i = rig.indexOf(bone);
            if (i < 0) {
                continue;
            }
            JsonObject t = tracks.getAsJsonObject(bone);
            pos[i] = track(t.getAsJsonArray("pos"));
            rot[i] = track(t.getAsJsonArray("rot"));
            scale[i] = track(t.getAsJsonArray("scale"));
        }
        JsonObject v = o.getAsJsonObject("vis");
        if (v != null) {
            for (String bone : v.keySet()) {
                int i = rig.indexOf(bone);
                if (i < 0) {
                    continue;
                }
                JsonArray keys = v.getAsJsonArray(bone);
                float[] times = new float[keys.size()];
                boolean[] vals = new boolean[keys.size()];
                for (int k = 0; k < keys.size(); k++) {
                    times[k] = keys.get(k).getAsJsonArray().get(0).getAsFloat();
                    vals[k] = keys.get(k).getAsJsonArray().get(1).getAsBoolean();
                }
                vis[i] = new Clip.VisTrack(times, vals);
            }
        }
        List<Clip.Event> events = new ArrayList<>();
        JsonArray ev = o.getAsJsonArray("events");
        if (ev != null) {
            for (JsonElement e : ev) {
                events.add(new Clip.Event(e.getAsJsonArray().get(0).getAsFloat(), e.getAsJsonArray().get(1).getAsString()));
            }
        }
        Clip.Kind kind = "posture".equals(o.get("kind").getAsString()) ? Clip.Kind.POSTURE : Clip.Kind.ACTION;
        return new Clip(o.get("name").getAsString(), kind, o.get("posture").getAsString(),
                o.get("length").getAsFloat(), o.get("loop").getAsBoolean(),
                o.get("blendIn").getAsFloat(), o.get("blendOut").getAsFloat(), o.get("lookAt").getAsFloat(),
                pos, rot, scale, vis, events);
    }

    private static Clip.Track track(JsonArray keys) {
        if (keys == null) {
            return null;
        }
        float[] times = new float[keys.size()];
        float[] vals = new float[keys.size() * 3];
        for (int k = 0; k < keys.size(); k++) {
            JsonArray key = keys.get(k).getAsJsonArray();
            times[k] = key.get(0).getAsFloat();
            JsonArray v = key.get(1).getAsJsonArray();
            for (int c = 0; c < 3; c++) {
                vals[k * 3 + c] = v.get(c).getAsFloat();
            }
        }
        return new Clip.Track(times, vals);
    }
}
