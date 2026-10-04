package net.nennneko5787.lepus.core.format.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.ProvesSpec;
import net.nennneko5787.lepus.core.format.diag.Diagnostics;
import net.nennneko5787.lepus.core.format.ir.geometry.GeometryFiles;
import net.nennneko5787.lepus.core.format.ir.geometry.GeometryIr;
import net.nennneko5787.lepus.core.format.json.Json;
import net.nennneko5787.lepus.core.format.json.JsonObject;
import net.nennneko5787.lepus.core.format.value.PackId;
import net.nennneko5787.lepus.core.format.value.Provenance;
import org.junit.jupiter.api.Test;

/**
 * Where each bone ends up. SC-180 §3.
 *
 * <p>Every assertion here is a point, not a matrix. A matrix comparison passes for the wrong reason
 * often enough to be worth avoiding, and "the elbow ends up here" is the claim that matters.
 */
@ProvesSpec("SC-180")
class BoneMatricesTest {

    private static final Provenance WHERE = Provenance.file(PackId.NONE, "models/x.geo.json");
    private static final float EPSILON = 0.001f;

    private static GeometryIr parse(String json) {
        JsonObject root = Json.parse(json).asObject().orElseThrow();
        return GeometryFiles.parse(root, WHERE, new Diagnostics()).get(0);
    }

    private static void assertPoint(float[] actual, float x, float y, float z) {
        assertEquals(x, actual[0], EPSILON, "x of " + java.util.Arrays.toString(actual));
        assertEquals(y, actual[1], EPSILON, "y of " + java.util.Arrays.toString(actual));
        assertEquals(z, actual[2], EPSILON, "z of " + java.util.Arrays.toString(actual));
    }

    /**
     * A bone with no rotation moves nothing at all.
     *
     * <p>The pivot is NOT a translation, and this is the test that says so. Treating it as one is
     * the most common way to get a bone hierarchy wrong: every cube collapses onto its bone's pivot,
     * which looks like a model that exploded rather than like a transform bug.
     */
    @Test
    void anUnrotatedBoneLeavesItsCubesWhereThePackPutThem() {
        GeometryIr model = parse("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": { "identifier": "geometry.sc" },
                    "bones": [{ "name": "body", "pivot": [0, 24, 0] }]
                  }]
                }""");
        Map<String, Mat4f> pose = BoneMatrices.bindPose(model);
        assertPoint(pose.get("body").transform(3, 20, 1), 3, 20, 1);
    }

    /**
     * A rotation happens about the bone's own pivot.
     *
     * <p>A quarter turn about Y at pivot {@code [0, 24, 0]} sends a point one unit in front of the
     * pivot to one unit to its side, and leaves the pivot itself alone. About the ORIGIN instead,
     * the same point would land 24 units away — the difference between an arm swinging at the
     * shoulder and an arm orbiting the model's feet.
     */
    @Test
    void aBoneTurnsAboutItsPivotAndNotTheOrigin() {
        GeometryIr model = parse("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": { "identifier": "geometry.sc" },
                    "bones": [{ "name": "arm", "pivot": [0, 24, 0], "rotation": [0, 90, 0] }]
                  }]
                }""");
        Mat4f arm = BoneMatrices.bindPose(model).get("arm");
        assertPoint(arm.transform(0, 24, 0), 0, 24, 0);
        // Bedrock's +90 about Y sends -Z to -X: a right-handed turn, UNLIKE its X and Z.
        //
        // This assertion has been both signs, and both times it agreed with the code rather than
        // checking it. It said -1 while the bone path ignored Bedrock's angle sense entirely; it was
        // changed to +1 alongside the fix for legs that swung backwards, which was an X-axis
        // failure, on the assumption that a sign convention is uniform across axes. It is not, and
        // SC-180 section 3.4.1 says why: the flip that makes X and Z turn backwards is about Y, and
        // leaves rotations about Y alone.
        //
        // What caught it was a pair of legs at +-24 degrees about Y crossing instead of spreading.
        assertPoint(arm.transform(0, 24, -1), -1, 24, 0);
    }

    /**
     * A child inherits its parent's turn, composed in the right order.
     *
     * <p>THE regression to guard. Multiplying the chain the other way round gives a model whose
     * limbs orbit the world instead of the body, and a bind pose — where most bones do not rotate
     * at all — hides it completely. The corpus this was written against orients whole characters
     * through a rotated root with the cubes several bones below it.
     */
    @Test
    void aChildInheritsItsParentsTurn() {
        GeometryIr model = parse("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": { "identifier": "geometry.sc" },
                    "bones": [
                      { "name": "root", "pivot": [0, 0, 0], "rotation": [0, 180, 0] },
                      { "name": "head", "parent": "root", "pivot": [0, 24, 0] }
                    ]
                  }]
                }""");
        Mat4f head = BoneMatrices.bindPose(model).get("head");
        // The root turns everything half way about the model's centre line, so a point in front of
        // the head comes out behind it. Unrotated, this would still be at z = -4.
        assertPoint(head.transform(0, 24, -4), 0, 24, 4);
        // And a point that is off-centre moves on BOTH axes, which a single-axis test cannot see.
        assertPoint(head.transform(2, 24, -4), -2, 24, 4);
    }

    /**
     * Two turns in a chain compose rather than replacing each other.
     *
     * <p>A parent turned a quarter and a child turned a quarter the same way is a half turn on the
     * child's cubes. If the child's own rotation replaced the chain instead of composing with it,
     * this reads as a quarter — which looks like "the parent is being ignored", the exact symptom
     * the block transpile had before it learned to walk parent chains.
     */
    @Test
    void turnsComposeAlongTheChain() {
        GeometryIr model = parse("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": { "identifier": "geometry.sc" },
                    "bones": [
                      { "name": "root", "pivot": [0, 0, 0], "rotation": [0, 90, 0] },
                      { "name": "arm", "parent": "root", "pivot": [0, 0, 0], "rotation": [0, 90, 0] }
                    ]
                  }]
                }""");
        assertPoint(BoneMatrices.bindPose(model).get("arm").transform(0, 0, -4), 0, 0, 4);
    }

    @Test
    void anExtraTransformAppliesInsideTheBonesOwnPivot() {
        // What animation hangs off. A quarter turn handed in for `arm` must swing it about the
        // elbow, exactly as its own rotation would - not about the model's origin.
        GeometryIr model = parse("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": { "identifier": "geometry.sc" },
                    "bones": [{ "name": "arm", "pivot": [0, 24, 0] }]
                  }]
                }""");
        Map<String, Mat4f> pose = BoneMatrices.posed(model,
                bone -> Optional.of(Mat4f.rotationY(90)));
        assertPoint(pose.get("arm").transform(0, 24, 0), 0, 24, 0);
        assertPoint(pose.get("arm").transform(0, 24, -1), -1, 24, 0);
    }

    /**
     * <b>A bone's own declared rotation does NOT carry that bone's own animated position.</b>
     * SC-180 §4.1.3, MEASURED by probe v20 on the Bedrock client.
     *
     * <p>This build had it the other way round — the declared rotation sat outside the animated
     * offset and carried it — and <b>every other test in this file passed while it was wrong</b>,
     * because a bind pose has no animation at all and a single bone is unrotated in most fixtures.
     * That is the whole reason the case is here rather than left to the probe: the probe found it,
     * nothing in this module could.
     *
     * <p>The rig is probe v20's: a bone declaring a quarter turn about Z, animated with an offset
     * straight up, and a cube-less control bone with no declared rotation carrying the same offset.
     * The control lands at the offset; the turned one must land there TOO, rotated in place.
     */
    @Test
    void aDeclaredRotationDoesNotCarryTheSameBonesAnimatedPosition() {
        GeometryIr model = parse("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": { "identifier": "geometry.sc" },
                    "bones": [
                      { "name": "turned", "pivot": [0, 0, 0], "rotation": [0, 0, 90] },
                      { "name": "plain",  "pivot": [0, 0, 0] }
                    ]
                  }]
                }""");
        // The animation both bones get: 24 units straight up, no rotation of its own.
        Mat4f offset = Mat4f.translation(0, 24, 0);
        Map<String, Mat4f> pose = BoneMatrices.posed(model,
                bone -> Optional.of(offset));

        // UP, not sideways. The declared rotation turned the bone in place about the origin and
        // the offset was added afterwards - which is the frame the Bedrock client drew.
        assertPoint(pose.get("turned").transform(0, 0, 0), 0, 24, 0);
        // It DID rotate: a point off the origin moves to where the quarter turn puts it. SC-180
        // 3.4.1 negates the Z sense, so a declared [0,0,90] sends +X to -Y - which is why the
        // expected y is 24-4 and not 24+4. Written out so a future reader does not "fix" it.
        assertPoint(pose.get("turned").transform(4, 0, 0), 0, 20, 0);
        // The control, which declares nothing, lands exactly where it always did.
        assertPoint(pose.get("plain").transform(0, 0, 0), 0, 24, 0);
    }

    /**
     * <b>And an ANCESTOR's transform still does carry it.</b> The other half of the same rule, and
     * the one that must not move when the case above is fixed.
     *
     * <p>Probe v16 measured it for an animated parent rotation and probe v14 for a declared one.
     * Both are about a transform on the LEFT, so a change to how one bone composes its own cannot
     * reach them — and this asserts that rather than trusting it, because the two readings differ
     * only in which bone the rotation is written on, which is exactly the kind of change that looks
     * like a no-op.
     */
    @Test
    void anAncestorsTransformStillCarriesTheChildsAnimatedPosition() {
        GeometryIr model = parse("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": { "identifier": "geometry.sc" },
                    "bones": [
                      { "name": "arm", "pivot": [0, 0, 0], "rotation": [0, 0, 90] },
                      { "name": "child", "parent": "arm", "pivot": [0, 0, 0] }
                    ]
                  }]
                }""");
        // The OFFSET goes to the child only. The parent's extra is identity: a rig that handed the
        // same offset to every bone would be measuring two rules at once.
        Map<String, Mat4f> pose = BoneMatrices.posed(model,
                bone -> Optional.of(bone.name().equals("child")
                        ? Mat4f.translation(0, 24, 0)
                        : Mat4f.IDENTITY));

        // The parent's quarter turn swings the child's offset SIDEWAYS, not up.
        assertPoint(pose.get("child").transform(0, 0, 0), 24, 0, 0);
    }

    @Test
    void aParentChainThatCyclesIsRefusedRatherThanFollowed() {
        // Following one inside a resource reload hangs the client. Refusing the model costs the
        // model. Constitution rule 5 puts a wrong shape above a hung game.
        GeometryIr model = parse("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": { "identifier": "geometry.sc" },
                    "bones": [
                      { "name": "a", "parent": "b", "pivot": [0, 0, 0] },
                      { "name": "b", "parent": "a", "pivot": [0, 0, 0] }
                    ]
                  }]
                }""");
        assertTrue(BoneMatrices.bindPose(model).isEmpty());
    }

    @Test
    void aParentNobodyDeclaresEndsTheChainRatherThanTheModel() {
        // Bedrock's own files do this, and SC-180 §3.2 already says the hierarchy may be
        // incomplete. The bone keeps its own transform and simply inherits nothing.
        GeometryIr model = parse("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "description": { "identifier": "geometry.sc" },
                    "bones": [{ "name": "hat", "parent": "nobody", "pivot": [0, 24, 0] }]
                  }]
                }""");
        assertPoint(BoneMatrices.bindPose(model).get("hat").transform(1, 2, 3), 1, 2, 3);
    }
}
