package net.nennneko5787.lepus.core.format.ir.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.ProvesSpec;
import net.nennneko5787.lepus.core.format.diag.Diagnostics;
import net.nennneko5787.lepus.core.format.ir.geometry.GeometryFiles;
import net.nennneko5787.lepus.core.format.ir.geometry.GeometryIr;
import net.nennneko5787.lepus.core.format.json.Json;
import net.nennneko5787.lepus.core.format.json.JsonObject;
import net.nennneko5787.lepus.core.format.json.JsonValue;
import net.nennneko5787.lepus.core.format.value.PackId;
import net.nennneko5787.lepus.core.format.value.Provenance;
import net.nennneko5787.lepus.core.format.value.Vec3f;
import org.junit.jupiter.api.Test;

/** Path A: a Bedrock block geometry becoming a Java block model. SC-150 §5. */
@ProvesSpec("SC-150")
class BlockGeometryTest {

    private static final Provenance WHERE = Provenance.file(PackId.NONE, "models/blocks/x.geo.json");
    private static final Map<String, String> ONE_TEXTURE = Map.of("*", "lepus:block/16_0");

    private static GeometryIr parse(String json) {
        JsonObject root = Json.parse(json).asObject().orElseThrow();
        return GeometryFiles.parse(root, WHERE, new Diagnostics()).get(0);
    }

    /** One bone, one cube, per-face UV, stated as a modern-family file. */
    private static GeometryIr cube(String origin, String size, String extra) {
        return parse("""
                {
                  "format_version": "1.21.0",
                  "minecraft:geometry": [{
                    "description": {
                      "identifier": "geometry.sc_test",
                      "texture_width": 16,
                      "texture_height": 16
                    },
                    "bones": [{
                      "name": "root",
                      "pivot": [0, 0, 0],
                      "cubes": [{
                        "origin": %s,
                        "size": %s,
                        "uv": {
                          "north": {"uv": [0, 0], "uv_size": [16, 16]},
                          "south": {"uv": [0, 0], "uv_size": [16, 16]},
                          "east":  {"uv": [0, 0], "uv_size": [16, 16]},
                          "west":  {"uv": [0, 0], "uv_size": [16, 16]},
                          "up":    {"uv": [0, 0], "uv_size": [16, 16]},
                          "down":  {"uv": [0, 0], "uv_size": [16, 16]}
                        }%s
                      }]
                    }]
                  }]
                }""".formatted(origin, size, extra));
    }

    private static String modelOf(GeometryIr geometry) {
        return BlockGeometry.modelJson(geometry, ONE_TEXTURE).orElseThrow();
    }

    /**
     * The gap that let a mirrored model ship.
     *
     * <p>Every other coordinate assertion in this file used a box symmetric on both horizontal axes,
     * so not one of them would fail if either were mirrored — and one <em>was</em>, for as long as it
     * took somebody to put the same block in both editions side by side. This box is asymmetric on
     * both, so it pins which axis is reversed and which is merely offset.
     */
    @Test
    void anAsymmetricBoxLandsOnTheSideBedrockPutItOn() {
        // Bedrock x -6..-4, z -6..-4 - the same numbers on both axes, so the OUTPUT differing
        // between them is the whole point. X is only offset, so it stays on the low side at 2..4.
        // Z is reversed as well as offset, so it comes out on the high side at 12..14.
        String model = modelOf(cube("[-6, 0, -6]", "[2, 4, 2]", ""));
        assertTrue(model.contains("\"from\": [2,0,12]"), model);
        assertTrue(model.contains("\"to\": [4,4,14]"), model);
    }

    /**
     * Which face is which after the mirror.
     *
     * <p>Nothing anywhere told {@code east} from {@code west} before this: the transpiler wrote
     * Bedrock's face names straight into the Java model, and every test used a cube whose faces were
     * interchangeable. Giving all six faces a distinct UV is what makes the swap visible.
     */
    @Test
    void mirroringSwapsNorthAndSouthAndLeavesTheOthersNamed() {
        JsonObject faces = facesOf(
                BlockGeometry.modelJson(sixDistinctFaces(), ONE_TEXTURE).orElseThrow());

        // Bedrock's north face is at its -Z, which is Java's +Z: it comes out as south.
        assertEquals("[0,0,1,1]", uvOf(faces, "south"));
        assertEquals("[2,0,3,1]", uvOf(faces, "north"));

        // The other four keep their names, and every rectangle keeps its own numbers: the
        // conversion moves faces around and never rewrites one.
        assertEquals("[4,0,5,1]", uvOf(faces, "east"));
        assertEquals("[6,0,7,1]", uvOf(faces, "west"));
        assertEquals("[8,0,9,1]", uvOf(faces, "up"));
        assertEquals("[10,0,11,1]", uvOf(faces, "down"));
    }

    /**
     * The invariant that decides the question the screenshots could not.
     *
     * <p>Both engines draw a face whose UV rectangle has positive extent un-mirrored, so a
     * conversion between them can only ever <b>rotate</b> a face. If it flipped one, that face would
     * render as its own mirror image — something neither engine does to anybody's model.
     *
     * <p>This is the test that rejects a wrong rule on the reasoning alone. The first attempt at
     * this conversion flipped four of the six faces, and nothing here could see it; a screenshot
     * eventually could, but this would have been quicker and did not need one.
     */
    @Test
    void noFaceComesOutMirrored() {
        JsonObject faces = facesOf(BlockGeometry.modelJson(sixDistinctFaces(), ONE_TEXTURE)
                .orElseThrow());
        for (String face : List.of("north", "south", "east", "west", "up", "down")) {
            List<Float> uv = faces.members().get(face).asObject().orElseThrow()
                    .members().get("uv").asArray().orElseThrow().floats();
            assertTrue(uv.get(0) < uv.get(2), face + " came out mirrored: " + uv);
        }
    }

    /**
     * The conversion is a pure relabelling: it spins nothing.
     *
     * <p>A small piece of evidence for the axis being the right one. A mirror on the other
     * horizontal axis would differ from this one by a half turn about Y, and that half turn has to
     * go somewhere — it would land as 180° of {@code rotation} on the top and bottom faces. Coming
     * out with no spin at all is the tidier of the two answers, and the one the block that settled
     * this actually looked like.
     *
     * <p><b>Still asserted rather than observed for the top face specifically.</b> A texture whose
     * top is obviously asymmetric settles it, and the correction is one number in
     * {@code toJavaSpin}.
     */
    @Test
    void theConversionSpinsNoFace() {
        JsonObject faces = facesOf(BlockGeometry.modelJson(sixDistinctFaces(), ONE_TEXTURE)
                .orElseThrow());
        for (String face : List.of("north", "south", "east", "west", "up", "down")) {
            assertEquals("none", spinOf(faces, face), face + " should not spin");
        }
    }

    /** A cube whose six faces carry six different rectangles, so none can stand in for another. */
    private static GeometryIr sixDistinctFaces() {
        return parse("""
                {
                  "format_version": "1.21.0",
                  "minecraft:geometry": [{
                    "description": {
                      "identifier": "geometry.sc_faces",
                      "texture_width": 16,
                      "texture_height": 16
                    },
                    "bones": [{
                      "name": "root",
                      "cubes": [{
                        "origin": [-8, 0, -8], "size": [16, 16, 16],
                        "uv": {
                          "north": {"uv": [0, 0], "uv_size": [1, 1]},
                          "south": {"uv": [2, 0], "uv_size": [1, 1]},
                          "east":  {"uv": [4, 0], "uv_size": [1, 1]},
                          "west":  {"uv": [6, 0], "uv_size": [1, 1]},
                          "up":    {"uv": [8, 0], "uv_size": [1, 1]},
                          "down":  {"uv": [10, 0], "uv_size": [1, 1]}
                        }
                      }]
                    }]
                  }]
                }""");
    }

    private static String spinOf(JsonObject faces, String face) {
        JsonValue rotation = faces.members().get(face).asObject().orElseThrow()
                .members().get("rotation");
        return rotation == null ? "none" : rotation.toCanonicalString();
    }

    @Test
    void aFullCubeLandsWhereTheBlockIs() {
        // The centre-to-corner offset, and the reason it is the same +8 collision_box uses: get the
        // sign wrong and every model in every pack sits half a block off, which no golden over
        // component names would ever notice.
        String model = modelOf(cube("[-8, 0, -8]", "[16, 16, 16]", ""));
        assertTrue(model.contains("\"from\": [0,0,0]"), model);
        assertTrue(model.contains("\"to\": [16,16,16]"), model);
    }

    @Test
    void anOffCentreBoxKeepsItsOffset() {
        String model = modelOf(cube("[-2, 0, -2]", "[4, 16, 4]", ""));
        assertTrue(model.contains("\"from\": [6,0,6]"), model);
        assertTrue(model.contains("\"to\": [10,16,10]"), model);
    }

    @Test
    void inflateGrowsTheBoxOnEverySide() {
        // Java has no inflate, so it is baked. Half a unit each way from a 4-wide box is 5 wide.
        String model = modelOf(cube("[-2, 0, -2]", "[4, 4, 4]", ", \"inflate\": 0.5"));
        assertTrue(model.contains("\"from\": [5.5,-0.5,5.5]"), model);
        assertTrue(model.contains("\"to\": [10.5,4.5,10.5]"), model);
    }

    /**
     * <b>A cube with no thickness on an axis emits BOTH of its faces, and this is measured.</b>
     *
     * <p>§5.3.1 originally said to drop one, on the reasoning that two coplanar quads fight over the
     * depth buffer. That reasoning is the attachable path's, where the quads are built with culling
     * off, and it was carried here by analogy. It is wrong here: dropping {@code up} from a flat
     * cube made the trophy's halo vanish from the world.
     *
     * <p>So Minecraft's block model baker already resolves the degeneracy, and it resolves it by
     * drawing one of the pair. The halo was visible before and gone after, which is what says
     * {@code up} was the one being drawn — {@code down} was never visible in the first place. There
     * were never two quads fighting here.
     *
     * <p>This asserts the observation rather than the theory, because the theory is what was wrong.
     * The real trophy's plate is {@code size: [24, 0, 15]}: a halo written as a zero-height box, and
     * 420 such cubes across the surveyed packs.
     */
    @Test
    void aCubeWithNoThicknessStillEmitsBothOfTheFlatAxisFaces() {
        String model = modelOf(cube("[-12, 19, -4]", "[24, 0, 15]", ""));
        assertTrue(model.contains("\"up\""),
                "up is the face Minecraft's baker draws, so dropping it loses the cube: \n" + model);
        assertTrue(model.contains("\"down\""), "and down is emitted for it to drop: \n" + model);
        // The box itself is unchanged: a zero-thickness axis is still a zero-thickness axis, and
        // 19 on both corners is what makes it flat. The Z numbers differ from the real trophy's
        // because this fixture has no 180-degree root turn for §5.3's mirror to move Z with.
        assertTrue(model.contains("\"from\": [-4,19,-3]"), model);
        assertTrue(model.contains("\"to\": [20,19,12]"), model);
    }

    /** And the same on the other two axes, since the flat one is not special. */
    @Test
    void aFlatAxisIsEmittedWholeOnEveryAxis() {
        for (String size : new String[] {"[16, 16, 0]", "[0, 16, 16]", "[16, 0, 16]"}) {
            String model = modelOf(cube("[-8, 0, -8]", size, ""));
            for (String face : new String[] {"north", "south", "east", "west", "up", "down"}) {
                assertTrue(model.contains("\"" + face + "\""),
                        "size " + size + " must still carry " + face + ":\n" + model);
            }
        }
    }

    /**
     * <b>A box UV that leaves the texture is out of range for a Java element.</b> SC-150 §5.3.2.
     *
     * <p>A Java block model reads {@code uv} as 0–16 <em>across its own sprite</em>, so a value
     * outside that samples off the sprite. Bedrock has no such range: a {@code uv} origin is stated in
     * texels and may be negative, with the texture treated as periodic. This trophy states its top
     * plate at exactly {@code uv: [-15, 11]} on a 64-wide texture, and the transpiler emitted
     * {@code u = -3.75} — 25 faces of 1230 across the surveyed packs.
     *
     * <p><b>NOT the trophy's missing halo, which is what this javadoc used to say.</b> SC-180 §3.3's
     * expansion puts the plate's two visible faces at {@code u} 0→6 and 6→12, both in range; the four
     * faces carrying {@code u < 0} have height 0 and draw nothing to be wrong about. That symptom is
     * §5.3.1's dropped face, and saying otherwise here sent a reader after UV arithmetic that cannot
     * produce it.
     *
     * <p><b>This asserts the DEFECT, and that is the point.</b> SC-150 §5.3.2 records that what
     * replaces it is not observed — whether Bedrock splits the rectangle at the seam or the box is
     * simply refused is a measurement nobody has taken. A test that pinned a wrap would be
     * asserting a guess, and would quietly become wrong the day the real answer arrives.
     */
    @Test
    void aBoxUvOutsideTheTextureIsCountedRatherThanSilentlyAccepted() {
        GeometryIr trophy = parse("""
                {
                  "format_version": "1.21.20",
                  "minecraft:geometry": [{
                    "description": {
                      "identifier": "geometry.sc_trophy_plate",
                      "texture_width": 64,
                      "texture_height": 64
                    },
                    "bones": [{
                      "name": "root",
                      "pivot": [0, 0, 0],
                      "cubes": [{
                        "origin": [-12, 19, -4],
                        "size": [24, 0, 15],
                        "uv": [-15, 11]
                      }]
                    }]
                  }]
                }""");
        assertTrue(BlockGeometry.transpilable(trophy), "the model itself transpiles fine");

        String model = modelOf(trophy);
        // Read every uv rectangle out of the emitted model and look at the numbers, rather than
        // string-matching a layout this file would then have to restate whenever it changes.
        List<Float> every = uvValues(model);
        assertFalse(every.isEmpty(), "the model emitted faces at all:\n" + model);
        // -15 texels on a 64-wide texture is -15/64*16 = -3.75 in Java's space, and Java's space
        // stops at 0. Asserting the number rather than the symptom, because "looks wrong" is not a
        // test anybody can fail on purpose.
        assertTrue(every.contains(-3.75f),
                "the negative box UV reaches the Java model as written: " + every);
        assertTrue(every.stream().anyMatch(v -> v < 0f || v > 16f),
                "at least one uv coordinate is outside the 0..16 a Java sprite is read in: " + every);
    }

    /** Every number appearing in any {@code uv} rectangle of a transpiled model, in order. */
    private static List<Float> uvValues(String model) {
        List<Float> out = new ArrayList<>();
        Json.parse(model).asObject().orElseThrow().getArray("elements").orElseThrow().values()
                .forEach(element -> element.asObject().orElseThrow()
                        .getObject("faces").orElseThrow().members().values()
                        .forEach(face -> face.asObject().orElseThrow()
                                .getArray("uv").orElseThrow()
                                .floats().forEach(out::add)));
        return out;
    }

    @Test
    void theUvDivisorIsAppliedHereAndOnlyHere() {
        // The IR keeps Bedrock's texel units undivided (SC-180 §3.4). A 32-wide texture means a
        // 16-texel face covers half of it, which Java spells as 0..8 in its 0..16 space.
        GeometryIr wide = parse("""
                {
                  "format_version": "1.21.0",
                  "minecraft:geometry": [{
                    "description": {
                      "identifier": "geometry.sc_wide",
                      "texture_width": 32,
                      "texture_height": 32
                    },
                    "bones": [{
                      "name": "root",
                      "cubes": [{
                        "origin": [-8, 0, -8], "size": [16, 16, 16],
                        "uv": { "north": {"uv": [0, 0], "uv_size": [16, 16]} }
                      }]
                    }]
                  }]
                }""");
        // 8 rather than 16, and in the original order: the conversion never flips a face.
        assertTrue(modelOf(wide).contains("\"uv\": [0,0,8,8]"), modelOf(wide));
    }

    @Test
    void mirrorBecomesASwappedPairOfUCoordinates() {
        // Java has no mirror flag and reads u1 > u2 as a flipped face, which is the same thing.
        //
        // Face by face, because the axis conversion ALSO flips U on four of the six: a `contains`
        // over the whole model passes on any face and would have gone on passing when the pack's
        // own mirror stopped being applied at all.
        String model = modelOf(cube("[-8, 0, -8]", "[16, 16, 16]", ", \"mirror\": true"));
        JsonObject faces = facesOf(model);

        // The pack's mirror is the ONLY thing that flips a texture, so every face carries it.
        assertEquals("[16,0,0,16]", uvOf(faces, "north"));
        assertEquals("[16,0,0,16]", uvOf(faces, "east"));
    }

    private static JsonObject facesOf(String model) {
        return Json.parse(model).asObject().orElseThrow()
                .members().get("elements").asArray().orElseThrow()
                .get(0).orElseThrow().asObject().orElseThrow()
                .members().get("faces").asObject().orElseThrow();
    }

    private static String uvOf(JsonObject faces, String face) {
        return faces.members().get(face).asObject().orElseThrow()
                .members().get("uv").toCanonicalString();
    }

    @Test
    void aFaceNamingAMaterialInstanceGetsThatInstancesTexture() {
        GeometryIr twoSided = parse("""
                {
                  "format_version": "1.21.0",
                  "minecraft:geometry": [{
                    "description": { "identifier": "geometry.sc_two" },
                    "bones": [{
                      "name": "root",
                      "cubes": [{
                        "origin": [-8, 0, -8], "size": [16, 16, 16],
                        "uv": {
                          "up":    {"uv": [0, 0], "uv_size": [16, 16], "material_instance": "top"},
                          "north": {"uv": [0, 0], "uv_size": [16, 16]}
                        }
                      }]
                    }]
                  }]
                }""");
        String model = BlockGeometry.modelJson(twoSided,
                Map.of("*", "lepus:block/side", "top", "lepus:block/top")).orElseThrow();
        assertTrue(model.contains("lepus:block/top"), model);
        assertTrue(model.contains("lepus:block/side"), model);
        assertEquals(List.of("*", "top"),
                BlockGeometry.instancesUsed(twoSided).stream().sorted().toList());
    }

    @Test
    void aFaceNamingAnInstanceThePackDoesNotDeclareFallsBackToTheDefault() {
        // Constitution rule 5: a typo in one face name costs that face its own texture, not the
        // block. The face still draws, with the block's `*` material.
        GeometryIr typo = parse("""
                {
                  "format_version": "1.21.0",
                  "minecraft:geometry": [{
                    "description": { "identifier": "geometry.sc_typo" },
                    "bones": [{
                      "name": "root",
                      "cubes": [{
                        "origin": [-8, 0, -8], "size": [16, 16, 16],
                        "uv": {
                          "up": {"uv": [0, 0], "uv_size": [16, 16], "material_instance": "tpo"}
                        }
                      }]
                    }]
                  }]
                }""");
        String model = BlockGeometry.modelJson(typo, ONE_TEXTURE).orElseThrow();
        assertTrue(model.contains("lepus:block/16_0"), model);
        assertTrue(model.contains("\"up\""), model);
    }

    @Test
    void aNeverRenderBoneDrawsNothing() {
        GeometryIr hidden = parse("""
                {
                  "format_version": "1.21.0",
                  "minecraft:geometry": [{
                    "description": { "identifier": "geometry.sc_hidden" },
                    "bones": [{
                      "name": "root",
                      "never_render": true,
                      "cubes": [{
                        "origin": [-8, 0, -8], "size": [16, 16, 16],
                        "uv": { "north": {"uv": [0, 0], "uv_size": [16, 16]} }
                      }]
                    }]
                  }]
                }""");
        assertEquals(Optional.empty(), BlockGeometry.modelJson(hidden, ONE_TEXTURE));
    }

    @Test
    void aCubeRotationJavaCanExpressBecomesAnElementRotation() {
        // The pivot is asymmetric on BOTH horizontal axes, which is the only way to see what happens
        // to it: every pivot in this file used to be [0, y, 0], and a point on the block's own axis
        // is fixed by any mirror, so the conversion could be - and was - simply wrong there.
        String model = modelOf(cube("[-8, 0, -8]", "[16, 16, 16]",
                ", \"pivot\": [4, 8, -4], \"rotation\": [0, 45, 0]"));
        assertTrue(BlockGeometry.transpilable(
                cube("[-8, 0, -8]", "[16, 16, 16]", ", \"rotation\": [0, 45, 0]")));
        assertTrue(model.contains("\"axis\": \"y\""), model);
        // Two corrections that happen to cancel on x and y: the pack's angle turns the other way to
        // this file's right-handed turns, and the mirror on Z reverses a turn about x or y again.
        // So +45 stays +45 here, and a turn about z would come out negated.
        assertTrue(model.contains("\"angle\": 45"), model);
        // Exactly what the box conversion does to a point: x is offset, z is reversed and offset.
        assertTrue(model.contains("\"origin\": [12,8,12]"), model);
    }

    @Test
    void aRotationJavaCannotExpressIsRejectedWholesale() {
        // Java validates elements[].rotation.angle against exactly {-45, -22.5, 0, 22.5, 45} and
        // refuses the whole model otherwise — so the block would not draw AT ALL. Rejecting here
        // means it draws as a cube instead, which is SC-150 §5.2's fallback.
        assertFalse(BlockGeometry.transpilable(
                cube("[-8, 0, -8]", "[16, 16, 16]", ", \"rotation\": [0, 30, 0]")));
        assertFalse(BlockGeometry.transpilable(
                cube("[-8, 0, -8]", "[16, 16, 16]", ", \"rotation\": [45, 45, 0]")));
        assertEquals(Optional.empty(), BlockGeometry.modelJson(
                cube("[-8, 0, -8]", "[16, 16, 16]", ", \"rotation\": [0, 30, 0]"), ONE_TEXTURE));
    }

    /** A bone chain: {@code root} turns and carries {@code stand}'s cubes with it. */
    private static GeometryIr chained(String rootRotation, String origin, String size) {
        return parse("""
                {
                  "format_version": "1.21.0",
                  "minecraft:geometry": [{
                    "description": {
                      "identifier": "geometry.sc_chain",
                      "texture_width": 16,
                      "texture_height": 16
                    },
                    "bones": [
                      { "name": "root", "pivot": [0, 0, 0], "rotation": %s },
                      {
                        "name": "stand",
                        "parent": "root",
                        "pivot": [0, 0, 0],
                        "cubes": [{
                          "origin": %s,
                          "size": %s,
                          "uv": {
                            "north": {"uv": [0, 0], "uv_size": [4, 4]},
                            "up":    {"uv": [0, 0], "uv_size": [4, 4]}
                          }
                        }]
                      }
                    ]
                  }]
                }""".formatted(rootRotation, origin, size));
    }

    @Test
    void aChildBoneInheritsItsParentsTurn() {
        // THE regression. The first real add-on to need any of this put `rotation` on a CHILDLESS
        // root bone and the cubes in its child, which is the ordinary way to orient a Bedrock model.
        // Reading each bone's own rotation alone transpiles it into a model that never turns - and
        // that reads as a rotation bug rather than as a missing feature.
        GeometryIr turned = chained("[0, 180, 0]", "[0, 0, 0]", "[4, 4, 4]");
        assertTrue(BlockGeometry.transpilable(turned));

        // In Bedrock's space the box is 0..4 on X and Z; a half turn about the centre sends it to
        // -4..0 on both. Converting then mirrors X and offsets Z, which lands it at 8..12 on X and
        // 4..8 on Z. A model that ignored the parent would leave it at 4..8 and 8..12 - the two
        // axes the other way round, which is what makes this case worth having twice over.
        String model = modelOf(turned);
        assertTrue(model.contains("\"from\": [4,0,8]"), model);
        assertTrue(model.contains("\"to\": [8,4,12]"), model);
    }

    @Test
    void aHalfTurnSendsTheFrontFaceToTheBack() {
        // Both halves of the claim, because either alone is satisfied by a model that never turns.
        // Untouched, the cube's Bedrock north face comes out as Java south - the axis conversion
        // trades those two names. Turned half way round, it comes out as north.
        String still = modelOf(chained("[0, 0, 0]", "[0, 0, 0]", "[4, 4, 4]"));
        assertTrue(still.contains("\"south\""), still);
        assertFalse(still.contains("\"north\""), still);

        String model = modelOf(chained("[0, 180, 0]", "[0, 0, 0]", "[4, 4, 4]"));
        assertTrue(model.contains("\"north\""), model);
        assertFalse(model.contains("\"south\""), model);
        // The face on the turn's axis spins in place rather than moving, and Java spells that as a
        // rotation on the face itself.
        assertEquals("180", spinOf(facesOf(model), "up"));
    }

    @Test
    void aQuarterTurnIsTranspilableToo() {
        assertTrue(BlockGeometry.transpilable(chained("[0, 90, 0]", "[0, 0, 0]", "[4, 4, 4]")));
        assertTrue(BlockGeometry.transpilable(chained("[0, -90, 0]", "[0, 0, 0]", "[4, 4, 4]")));
        assertTrue(BlockGeometry.transpilable(chained("[90, 0, 0]", "[0, 0, 0]", "[4, 4, 4]")));
    }

    /**
     * Which way round a quarter turn goes — the other hole the half turns hid.
     *
     * <p>Nothing in this file told +90° from −90° before it: every turn asserted anywhere was 180°,
     * and a half turn is the same whichever way it is taken. So the two directions a real pack
     * reaches for most — the east and west permutations of a block that faces the player — were the
     * only ones with no test at all, and they were the two that shipped swapped.
     *
     * <p>The box is in Bedrock's south-west corner and stays out of the middle, so where it lands
     * says which way the model went round rather than merely that it moved.
     */
    @Test
    void aQuarterTurnGoesTheWayBedrocksAngleMeans() {
        // Bedrock x -6..-4 and z -6..-4: Java's west side, and its south side once Z is reversed.
        // A pack's +90 turns it towards the east, so it comes out in the south-east corner. Round
        // the other way it would land in the north-west, which is what it used to do.
        String model = modelOf(chained("[0, 90, 0]", "[-6, 0, -6]", "[2, 4, 2]"));
        assertTrue(model.contains("\"from\": [12,0,12]"), model);
        assertTrue(model.contains("\"to\": [14,4,14]"), model);

        // And -90 is the mirror of it, in the north-west. Stated because a sense error that also
        // negated would pass the line above and fail here.
        String back = modelOf(chained("[0, -90, 0]", "[-6, 0, -6]", "[2, 4, 2]"));
        assertTrue(back.contains("\"from\": [2,0,2]"), back);
        assertTrue(back.contains("\"to\": [4,4,4]"), back);
    }

    /**
     * A quarter turn carries a face round with it, and the two faces on the axis spin in place.
     *
     * <p>Both halves matter: the box landing in the right corner with its textures on the wrong
     * sides is a distinct bug from the box landing in the wrong corner, and one that no coordinate
     * assertion can see.
     */
    @Test
    void aQuarterTurnCarriesTheFacesRoundWithTheBox() {
        String model = modelOf(chained("[0, 90, 0]", "[0, 0, 0]", "[4, 4, 4]"));
        JsonObject faces = facesOf(model);

        // Bedrock's north face, taken three right-handed quarters round, points east - and east is
        // one of the four names the conversion leaves alone.
        assertTrue(faces.members().containsKey("east"), model);
        assertFalse(faces.members().containsKey("north"), model);
        assertFalse(faces.members().containsKey("south"), model);
        assertFalse(faces.members().containsKey("west"), model);
        // The face on the turn's own axis does not move; it spins, and Java spells that on the face.
        assertEquals("270", spinOf(faces, "up"));
    }

    /**
     * The turn a placed block actually gets, and that it agrees with the one a bone gets.
     *
     * <p>{@code minecraft:transformation} is how a Bedrock block faces a direction: one permutation
     * per direction, each setting a rotation. It reaches the same machinery as a bone's rotation and
     * had no test of its own, so the two could have disagreed about which way round they go and only
     * the one with a test would have been right.
     */
    @Test
    void aTransformationTurnsTheModelTheWayABoneWould() {
        String turned = BlockGeometry.modelJson(cube("[-6, 0, -6]", "[2, 4, 2]", ""), ONE_TEXTURE,
                new BlockTransform(new Vec3f(0, 90, 0), Vec3f.ZERO, Vec3f.ONE)).orElseThrow();
        assertTrue(turned.contains("\"from\": [12,0,12]"), turned);
        assertTrue(turned.contains("\"to\": [14,4,14]"), turned);
    }

    @Test
    void aTurnOnTwoAxesAtOnceIsRejected() {
        // Composing them needs Bedrock's order, which is not written down anywhere this project can
        // check. Wrong order is a model that is right in outline and wrong in orientation.
        assertFalse(BlockGeometry.transpilable(chained("[90, 90, 0]", "[0, 0, 0]", "[4, 4, 4]")));
    }

    @Test
    void aRotationThatIsNotAQuarterTurnIsStillRejected() {
        assertFalse(BlockGeometry.transpilable(chained("[0, 45, 0]", "[0, 0, 0]", "[4, 4, 4]")));
        assertFalse(BlockGeometry.transpilable(chained("[0, 158.77, 0]", "[0, 0, 0]", "[4, 4, 4]")));
    }

    @Test
    void aRotatedBoneIsRejected() {
        // A turned bone turns its cubes about a pivot, and a turned box is the one thing a Java
        // element cannot be. §5.1 rule 3.
        GeometryIr turned = parse("""
                {
                  "format_version": "1.21.0",
                  "minecraft:geometry": [{
                    "description": { "identifier": "geometry.sc_turned" },
                    "bones": [{
                      "name": "root",
                      "pivot": [0, 8, 0],
                      "rotation": [0, 45, 0],
                      "cubes": [{
                        "origin": [-8, 0, -8], "size": [16, 16, 16],
                        "uv": { "north": {"uv": [0, 0], "uv_size": [16, 16]} }
                      }]
                    }]
                  }]
                }""");
        assertFalse(BlockGeometry.transpilable(turned));
    }

    @Test
    void aModelWithNoTextureIsNotSomethingToDraw() {
        assertEquals(Optional.empty(),
                BlockGeometry.modelJson(cube("[-8, 0, -8]", "[16, 16, 16]", ""), Map.of()));
    }

    @Test
    void theModelInheritsBlockDisplayTransforms() {
        // Right in the world and wrong in every hand holding it, without this.
        assertTrue(modelOf(cube("[-8, 0, -8]", "[16, 16, 16]", ""))
                .contains("minecraft:block/block"));
        // And nothing is written where the pack said nothing, so the parent keeps answering.
        assertFalse(modelOf(cube("[-8, 0, -8]", "[16, 16, 16]", "")).contains("\"display\""), "display");
    }

    /** One model with its own {@code item_display_transforms}, stated as a pack states them. */
    private static GeometryIr displayed(String transforms) {
        return parse("""
                {
                  "format_version": "1.21.0",
                  "minecraft:geometry": [{
                    "description": {
                      "identifier": "geometry.sc_display",
                      "texture_width": 16,
                      "texture_height": 16
                    },
                    "bones": [{
                      "name": "root",
                      "pivot": [0, 0, 0],
                      "cubes": [{
                        "origin": [-8, 0, -8], "size": [16, 16, 16],
                        "uv": { "north": {"uv": [0, 0], "uv_size": [16, 16]} }
                      }]
                    }],
                    "item_display_transforms": %s
                  }]
                }""".formatted(transforms));
    }

    /**
     * The transforms a pack wrote, in Java's space.
     *
     * <p>The whole visible symptom of not having this: the block is right in the world, and the
     * hotbar icon is at Java's default angle — which is about a quarter turn from the one the pack
     * chose, and reads as a mirrored icon rather than as a dropped component.
     */
    @Test
    void thePacksItemDisplayTransformsReachTheModel() {
        JsonObject display = displayOf(BlockGeometry.modelJson(displayed("""
                {
                  "gui": {
                    "rotation": [30, -45, 2],
                    "translation": [-1, -1.25, 3],
                    "scale": [0.5, 0.5, 0.5]
                  }
                }"""), ONE_TEXTURE).orElseThrow());
        JsonObject gui = display.members().get("gui").asObject().orElseThrow();

        // The angles take the same correction the model's own turns take, which on x and y is two
        // reversals that cancel and on z is one that does not.
        assertEquals("[30,-45,-2]", gui.members().get("rotation").toCanonicalString());
        // A translation is a displacement, not a position: it takes the mirror's sign on Z and NOT
        // the centre-to-corner offset on X. Adding 8 here would shove every icon half a block over.
        assertEquals("[-1,-1.25,-3]", gui.members().get("translation").toCanonicalString());
        // A mirror commutes with a scale along the axes, so this one passes through untouched.
        assertEquals("[0.5,0.5,0.5]", gui.members().get("scale").toCanonicalString());
    }

    @Test
    void aContextThePackDoesNotStateIsLeftToTheParent() {
        // Java resolves `display` one context at a time up the parent chain, so writing only what
        // the pack wrote keeps block/block's answer for the seven the pack did not.
        JsonObject display = displayOf(BlockGeometry.modelJson(
                displayed("{ \"gui\": { \"scale\": [0.5, 0.5, 0.5] } }"), ONE_TEXTURE).orElseThrow());
        assertEquals(List.of("gui"), List.copyOf(display.members().keySet()));
    }

    private static JsonObject displayOf(String model) {
        return Json.parse(model).asObject().orElseThrow()
                .members().get("display").asObject().orElseThrow();
    }
}
