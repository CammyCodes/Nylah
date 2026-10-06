# CLAUDE.md: working on Nylah

Nylah is a client-only Fabric mod (Minecraft 26.1.x, loader-only, Java 25) that adds a
companion cat: a tortie-point Siamese with a custom model, a painted coat and a library of
keyframed and procedural animations. Version **1.0.0**.

Nylah is a separate mod from Nethermine (`../prisonPlayer`) and must stay that way: no
references to it, no reading its files or state, no shared code.

## Build, test, deliver

`JAVA_HOME` must be Temurin 25 (`C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot`).

```bash
./gradlew build          # jar in build/libs/nylah-<version>.jar; runs the tests
./gradlew test           # just the tests
```

`deliver.ps1` installs the jar into both CurseForge instances (it waits while an instance is
running, never leaves two Nylah jars, never downgrades, verifies by hash).
Bump `mod_version` in `gradle.properties` for every delivered change.

## Where things live

| Concern | Files |
|---|---|
| Her shape (bones, boxes, UVs) | `tools/model/build_geo.py` writes `assets/nylah/geo/nylah.geo.json`; `model/Geo.java` bakes it |
| Her coat | `tools/texture/paint_nylah.py` writes `textures/entity/nylah_{2,4,8}x.png`; colours from `tools/palette/palette.json` (measured from photos by `sample_palette.py`) |
| Her animations | `tools/anim/build_clips.py` writes `assets/nylah/anim/*.json`; `anim/Clip.java` samples, `anim/ClipLibrary.java` loads |
| Her pose each frame | `anim/Animator.java` (the ONE owner), `anim/Gait.java`, `anim/LookAt.java` |
| Drawing her | `render/NylahRenderer.java`, `render/NylahModel.java`, mixins `EntityRenderDispatcherMixin`, `EntityRenderersMixin` |
| The entity | `entity/NylahCat.java` |
| What she decides to do | `Brain.java` (moods, idle repertoire, leg rubs, follow, wait, sleep...) |
| Walking | `move/PathFinder.java` (pure A*), `move/LevelGrid.java`, `move/Mover.java` |
| Being there (spawn, arrive out of view, catch up, nap) | `Nylah.java`, `SpawnSpots.java` |
| Strokes | `Gesture.java`, `MinecraftMixin.startUseItem` |
| `/nylah debug` | `DebugReel.java`, `ClientPacketListenerMixin` |
| Menu, keys, config | `ui/NylahScreen.java`, `NylahKeybinds.java`, `NylahConfig.java` (`config/nylah.json`) |
| Logo, icon, banner | `tools/logo/make_logo.py` |
| Browser preview of model + clips | `tools/preview/index.html` (+ `clip.js`, the JS twin of `Clip.java`) |

The Python tools need Pillow and NumPy. The reference photos and videos live in `reference/`,
which is git-ignored and must never be committed.

## Rules that keep her safe

1. **`NylahCat.isPickable()` is `return false`, unconditionally, forever.** If the crosshair
   could pick her, the next click would send the server an interact/attack packet about an
   entity that does not exist. Clicks on her are found by `Gesture`'s own ray test.
   `SafetyTest.sheIsNeverPickable` checks the bytecode.
2. **Nothing may talk to the server.** No packets, no chat, no commands, no container
   clicks, no `containerMenu` writes. `/nylah` is intercepted in `sendCommand` and
   cancelled. `SafetyTest.nothingInTheModCanTalkToTheServer` audits every compiled class.
   Messages to the player are client-side chat lines (`addClientSystemMessage`).
3. **Only a right-click with an EMPTY hand, aimed at her with nothing in between, is ever
   consumed.** Holding anything passes the click on. Left-click is never touched.
4. **She never appears in view.** Arrivals spawn behind the camera (`SpawnSpots`) and walk in.
5. **Fake entity ids count down from -1,900,000,000** and are checked against the level
   before use; if a real entity ever takes her id she is quietly re-created.
6. **Verify every Minecraft API against the 26.1 jar before using it** (`javap -cp
   ~/.gradle/caches/unimined/net/minecraft/minecraft/26.1/minecraft-26.1-fabric-merged+fixed-official.jar <class>`).
   Training data does not cover 26.1. Mixin targets are especially unforgiving:
   `defaultRequire` is 1, so a wrong descriptor crashes the game at launch.

## 26.1 facts found while building her (all javap-verified)

- Vanilla's cat model has only head, body, legs and two tail parts: no neck, jaw, tongue,
  ears or eyelids. Hence the custom model.
- `LayerDefinition.create(mesh, w, h).bakeRoot()` bakes a model with no model-layer
  registration, so resource packs and model mods cannot replace her body.
- `EntityRenderDispatcher.getRenderer` has two overloads, one by entity and one by render
  state; both are intercepted. `EntityRenderers.createEntityRenderers(Context)` is called on
  every resource reload: that is where her renderer is built.
- `Model.setupAnim` resets the pose first, then `NylahModel` adds the animator's offsets.
- `ModelPart` rotations apply Z, then Y, then X, about the PARENT's axes. A yaw on a part
  whose parent is pitched swings through a tilted plane: look-at yaw therefore goes on the
  head (its parent, the neck, stays near upright), and grooming poses turn the head too.
- Box UV layout and corner order (from `ModelPart$Cube` / `$Polygon` bytecode) are replicated
  exactly in `paint_nylah.py` and the preview: change one, change all three.
- Loader-only mods cannot rely on their `assets/` being visible to the resource manager
  (that is Fabric API's job). Her coats are loaded from the jar into `DynamicTexture`s
  registered as `nylah:coat_2x/4x/8x`.
- She is an `Ocelot` underneath, not a `Cat`: `Cat` reads its variant from a server-synced
  registry on construction. Cat sounds come from `SoundEvents.CAT_SOUNDS` (CLASSIC set).
- Mojang's 26.1 names: `Identifier` (not ResourceLocation), `GuiGraphicsExtractor` +
  `extractRenderState`/`extractBackground`/`extractContents` for GUI, `Entity.snapTo`.

## Look and feel

She is deliberately **adorable**: baby-animal proportions (big head and eyes, small round
body, stubby legs), soft colour fields, huge glossy eyes with two sparkles. Her MARKINGS are
what make her Nylah: the soft seal mask and blaze, the ginger flash on her right forehead,
ginger on her right ear, her **dark right front paw and pale left**, the ginger tail band
and dark tail tip, blue eyes. Model -X is HER RIGHT. Keep both the cuteness and the
markings when changing anything.

Check any change to her model, coat or clips in the preview (`tools/preview/index.html`,
`?sheet=1` gives a contact sheet of every clip) before building.
