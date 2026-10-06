package com.smartypantsltd.nylah.anim;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Her bones by index: the shared vocabulary of the model, the clips and the animator.
 *
 * <p>Built from the geometry file's bone list, so adding a bone to
 * {@code tools/model/build_geo.py} is all it takes for clips to address it.
 * Pure Java on purpose: the animation maths is tested without Minecraft.</p>
 */
public final class Rig {

    /** Groups whose parts are hidden at rest (expression planes and the tongue). */
    private static final List<String> HIDDEN_GROUPS = List.of("lid", "happy", "tongue");

    private final String[] names;
    private final String[] groups;
    private final Map<String, Integer> index = new HashMap<>();

    public Rig(List<String> names, List<String> groups) {
        this.names = names.toArray(new String[0]);
        this.groups = groups.toArray(new String[0]);
        for (int i = 0; i < this.names.length; i++) {
            index.put(this.names[i], i);
        }
    }

    public int size() {
        return names.length;
    }

    public String name(int i) {
        return names[i];
    }

    public String group(int i) {
        return groups[i];
    }

    /** -1 when the rig has no such bone (a clip naming a removed bone is ignored, not fatal). */
    public int indexOf(String name) {
        Integer i = index.get(name);
        return i == null ? -1 : i;
    }

    public boolean visibleAtRest(int i) {
        return !HIDDEN_GROUPS.contains(groups[i]);
    }
}
