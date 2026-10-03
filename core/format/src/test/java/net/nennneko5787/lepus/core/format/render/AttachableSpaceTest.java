package net.nennneko5787.lepus.core.format.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import net.nennneko5787.lepus.core.api.ProvesSpec;
import org.junit.jupiter.api.Test;

/**
 * The three spaces, and where each puts its origin. SC-180 §3.4, §3.4.2.
 *
 * <p><b>These were fields on the client-side emitter until they moved here</b>, which made them
 * correct and uncheckable at the same time. This class is the test that moving them bought, and it
 * is worth being explicit about which claims are arithmetic and which are measurements, because the
 * one number no frame supports is the one most likely to be changed by someone who assumes the rest
 * are equally soft.
 */
@ProvesSpec("SC-180")
class AttachableSpaceTest {

    private static final float EPSILON = 1.0e-6f;

    /**
     * <b>Every space is 1/16 of a block per Bedrock unit.</b> ARITHMETIC, from the unit the packs are
     * authored in, and the reason a model drawn without it is sixteen times the size of its block.
     */
    @Test
    void everySpaceIsSixteenthsOfABlock() {
        for (float[] space : new float[][] {AttachableSpace.AS_ITEM, AttachableSpace.ON_PLAYER,
                AttachableSpace.IN_FIRST_PERSON}) {
            for (float component : space) {
                assertEquals(AttachableSpace.SCALE, Math.abs(component), EPSILON,
                        java.util.Arrays.toString(space));
            }
        }
        assertEquals(1.0f / 16.0f, AttachableSpace.SCALE, EPSILON);
    }

    /**
     * <b>Java draws an entity model with +Y pointing down, so every space inverts Y.</b> MEASURED —
     * Bedrock authors a character standing on y 0 and Java draws the same character from a pivot
     * 1.5 blocks up with Y descending, and the mismatch is a model floating over the player's head.
     */
    @Test
    void everySpaceInvertsY() {
        assertEquals(-AttachableSpace.SCALE, AttachableSpace.ON_PLAYER[1], EPSILON);
        assertEquals(AttachableSpace.SCALE, AttachableSpace.IN_FIRST_PERSON[1], EPSILON);
        assertEquals(-AttachableSpace.SCALE, AttachableSpace.AS_ITEM[1], EPSILON);
    }

    /**
     * <b>Third person and first person mirror the same NUMBER of axes and a different one.</b>
     * MEASURED, and asserted here because it is the fact the derivation rests on and the easiest to
     * lose to a well-meaning "they should be the same up to a translation".
     *
     * <p>Both are odd — one flipped axis each — so <b>neither is a pure rotation of the other</b>,
     * and that is the load-bearing part. Third person's flip is the engine's own Y flip; first
     * person's is on X, because the world-to-camera step is a proper rotation and cannot absorb the
     * mirror Bedrock's model space already carries, so the composition has to keep it somewhere
     * else. A change that made the two agree on the axis would be a change that has thrown away a
     * measurement, and one that made the parity even would make one of the two models inside out.
     */
    @Test
    void bothSpacesMirrorOneAxisAndItIsNotTheSameOne() {
        assertEquals(1, negatives(AttachableSpace.ON_PLAYER),
                "third person mirrors one axis, Y: " + java.util.Arrays.toString(
                        AttachableSpace.ON_PLAYER));
        assertEquals(1, negatives(AttachableSpace.IN_FIRST_PERSON),
                "first person mirrors one axis, X: " + java.util.Arrays.toString(
                        AttachableSpace.IN_FIRST_PERSON));
        assertEquals(2, negatives(AttachableSpace.AS_ITEM),
                "the item space mirrors two, Y and Z, which is why it is not a rotation of either "
                        + "player space: " + java.util.Arrays.toString(AttachableSpace.AS_ITEM));
        assertNotEquals(java.util.Arrays.toString(AttachableSpace.ON_PLAYER),
                java.util.Arrays.toString(AttachableSpace.IN_FIRST_PERSON));
        // The two are not related by a rotation: three axes of sign between them on two of three.
        int differing = 0;
        for (int axis = 0; axis < 3; axis++) {
            if (AttachableSpace.ON_PLAYER[axis] != AttachableSpace.IN_FIRST_PERSON[axis]) {
                differing++;
            }
        }
        assertEquals(2, differing, "X and Y differ; Z is the one axis the two agree on");
    }

    /**
     * <b>Bedrock −X is the character's own right, and first person keeps it there.</b> MEASURED on a
     * Bedrock frame: a rifle carried on the character's right, at {@code x -4.43 .. -1.96}, appears
     * on the screen's right. The X of first person is negated, so the model's own −X must survive
     * into the camera's +X — which is what "only Y flips" buys.
     */
    @Test
    void firstPersonNegatesXAndLeavesZAlone() {
        assertEquals(-AttachableSpace.SCALE, AttachableSpace.IN_FIRST_PERSON[0], EPSILON);
        assertEquals(AttachableSpace.SCALE, AttachableSpace.IN_FIRST_PERSON[2], EPSILON);
    }

    /**
     * <b>The item space is unused, and that is recorded rather than deleted.</b>
     *
     * <p>It is the only one of the three that is not a player's body, and no attachable in the
     * surveyed corpus is drawn anywhere else — so the honest state of the question "and an item that
     * is not on a player?" is a constant nothing calls, not a constant that was quietly dropped and
     * will be reinvented wrongly. A test that fails when someone deletes it is the point.
     */
    @Test
    void theItemSpaceExistsAndIsTheOneJavaUsesForATrident() {
        assertArrayEquals(new float[] {AttachableSpace.SCALE, -AttachableSpace.SCALE,
                -AttachableSpace.SCALE}, AttachableSpace.AS_ITEM, EPSILON);
    }

    /**
     * <b>Which space a view uses is the caller's choice, and the two are not interchangeable.</b>
     * SC-180 §3.4.2: first person is not player space with a different origin, which is the belief
     * that put a pitch and a yaw compensation in this pass and had to take them out again.
     */
    @Test
    void ofPicksTheSpaceTheViewIsDrawnIn() {
        assertArrayEquals(AttachableSpace.IN_FIRST_PERSON, AttachableSpace.of(true), EPSILON);
        assertArrayEquals(AttachableSpace.ON_PLAYER, AttachableSpace.of(false), EPSILON);
    }

    /**
     * <b>The two origins, asserted as the numbers the renderers pass.</b>
     *
     * <p>Third person's is 24/16 = 1.5, because a player model's legs run from y 12 to y 24 in Java
     * and 24 is the ground — MEASURED, and the sign was wrong the first time, which lifted the model
     * a block and a half instead of lowering it.
     *
     * <p>First person's is a standing player's eye height, read per frame rather than fixed, because
     * a crouching player's eye drops. This constant is the standing value: the one an offline
     * measurement wants, and the one a frame of a standing player is.
     */
    @Test
    void theOriginsAreTheNumbersTheRenderersUse() {
        assertEquals(1.5f, AttachableSpace.THIRD_PERSON_FLOOR, EPSILON);
        assertEquals(24.0f, AttachableSpace.THIRD_PERSON_FLOOR * 16.0f, EPSILON);
        assertEquals(1.62f, AttachableSpace.STANDING_EYE_HEIGHT, EPSILON);
    }

    /**
     * <b>The one number here no frame supports, pinned so that changing it is a decision.</b>
     *
     * <p>Not a test of the value — there is no value to test, which is the point. This asserts only
     * that it is the number the code applies, and the comment says in one place that it is
     * unsubstantiated: the reason recorded for it assumes first person should be third person in
     * size, and SC-180 §3.4.2 refutes that premise. A constant that outlived the diagnosis of the
     * thing it was correcting has cost this project a mirror once already, so the next reader meets
     * the caveat in the class, in the ledger and in a failing test rather than in a git blame.
     */
    @Test
    void thePlayerModelScaleIsAppliedAndFlaggedAsUnmeasured() {
        assertEquals(0.9375f, AttachableSpace.PLAYER_MODEL_SCALE, EPSILON);
        // Read out of vanilla's own AvatarRenderer for the THIRD-person half, which is why the
        // number exists. What is not established is that a first-person pass should apply it.
        assertEquals(15.0f / 16.0f, AttachableSpace.PLAYER_MODEL_SCALE, EPSILON);
    }

    private static int negatives(float[] space) {
        int count = 0;
        for (float component : space) {
            if (component < 0) {
                count++;
            }
        }
        return count;
    }
}
