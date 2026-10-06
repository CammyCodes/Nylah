// The JavaScript twin of anim/Clip.java: samples a keyframed clip the same way
// the mod does, so the preview shows exactly what the game will play.
//
// A clip: { name, length (ticks), loop, tracks: { bone: { pos|rot|scale: [[t, [x,y,z]], ...] } },
//           vis: { bone: [[t, bool], ...] }, events: [[t, "name"], ...] }
// pos and rot are OFFSETS added to the bone's rest pose; scale multiplies (default 1).
// Between keys the curve is a monotone cubic: smooth, and never overshoots a key.
(function () {
  const HIDDEN = new Set(['lid', 'happy', 'tongue']);

  function hermite(keys, t, loop, length, comp) {
    const n = keys.length;
    if (n === 0) return 0;
    if (n === 1) return keys[0][1][comp];
    if (loop) t = ((t % length) + length) % length;
    if (!loop && t <= keys[0][0]) return keys[0][1][comp];
    if (!loop && t >= keys[n - 1][0]) return keys[n - 1][1][comp];
    let i = -1;
    for (let k = 0; k < n; k++) if (keys[k][0] <= t) i = k;
    let k0, k1, span;
    if (i === -1 || i === n - 1) {         // looping wrap segment: last key -> first key
      k0 = keys[n - 1]; k1 = keys[0];
      span = (k1[0] + length) - k0[0];
      const tt = i === -1 ? t + length : t;
      return seg(keys, n - 1, 0, (tt - k0[0]) / span, comp, loop, length);
    }
    k0 = keys[i]; k1 = keys[i + 1];
    span = k1[0] - k0[0];
    return seg(keys, i, i + 1, span > 0 ? (t - k0[0]) / span : 1, comp, loop, length);
  }

  function slope(keys, i, comp, loop, length) {
    const n = keys.length;
    const prev = i > 0 ? keys[i - 1] : (loop ? [keys[n - 1][0] - length, keys[n - 1][1]] : null);
    const next = i < n - 1 ? keys[i + 1] : (loop ? [keys[0][0] + length, keys[0][1]] : null);
    if (!prev || !next) return 0;            // clip ends ease in and out
    const v = keys[i][1][comp], a = prev[1][comp], b = next[1][comp];
    if ((v - a) * (b - v) <= 0) return 0;    // a local peak or valley: flat, so no overshoot
    const d = next[0] - prev[0];
    return d > 0 ? (b - a) / d : 0;
  }

  function seg(keys, i0, i1, u, comp, loop, length) {
    const k0 = keys[i0], k1 = keys[i1];
    let span = k1[0] - k0[0];
    if (span <= 0) span += length;
    const m0 = slope(keys, i0, comp, loop, length) * span;
    const m1 = slope(keys, i1, comp, loop, length) * span;
    const p0 = k0[1][comp], p1 = k1[1][comp];
    const u2 = u * u, u3 = u2 * u;
    return (2 * u3 - 3 * u2 + 1) * p0 + (u3 - 2 * u2 + u) * m0 + (-2 * u3 + 3 * u2) * p1 + (u3 - u2) * m1;
  }

  function vec(keys, t, loop, length, dflt) {
    if (!keys) return dflt.slice();
    return [0, 1, 2].map(c => hermite(keys, t, loop, length, c));
  }

  function visAt(keys, t, loop, length, dflt) {
    if (!keys || keys.length === 0) return dflt;
    if (loop) t = ((t % length) + length) % length;
    let v = keys[0][0] <= t ? keys[0][1] : dflt;
    for (const [kt, kv] of keys) if (kt <= t) v = kv;
    return v;
  }

  function sample(clip, t, geo) {
    const out = { bones: {}, root: { pos: [0, 0, 0], rot: [0, 0, 0] } };
    for (const b of geo.bones) {
      const tr = clip.tracks[b.name] || {};
      out.bones[b.name] = {
        pos: vec(tr.pos, t, clip.loop, clip.length, [0, 0, 0]),
        rot: vec(tr.rot, t, clip.loop, clip.length, [0, 0, 0]),
        scale: vec(tr.scale, t, clip.loop, clip.length, [1, 1, 1]),
        visible: visAt((clip.vis || {})[b.name], t, clip.loop, clip.length, !HIDDEN.has(b.group)),
      };
    }
    return out;
  }

  window.NylahClip = { sample };
})();
