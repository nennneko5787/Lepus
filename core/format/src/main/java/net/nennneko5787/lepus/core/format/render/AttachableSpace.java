package net.nennneko5787.lepus.core.format.render;

/**
 * The three spaces a Bedrock model is drawn in, and where each one puts its origin.
 * SC-180 §3.4, §3.4.2, §4.2.
 *
 * <p><b>They live here rather than beside the renderer so that they can be tested without
 * Minecraft.</b> They were fields on the client-side emitter, which made them correct and
 * uncheckable at the same time: a survey or a test that wanted to say where a model lands in
 * first person had to either launch a client or copy the numbers, and a survey with its own copy
 * of a constant drifts towards agreeing with itself. A coordinate space is arithmetic, and this is
 * the class that says so.
 *
 * <p><b>The caller picks the space, because they are not the same conversion.</b> One constant inside
 * the emitter would have meant the item path and the player path sharing a decision they do not
 * share — which is exactly the mistake {@link #AS_ITEM} records.
 *
 * <p>None of this is a fitting. Every number below is either read off a Bedrock frame or derived
 * from another number that was, and the two derivations are written out in full at the constants
 * they belong to. The exception is {@link #PLAYER_MODEL_SCALE}, which is not measured and says so.
 */
public final class AttachableSpace {

    /** Bedrock authors at 16 units per block. Every space below shares that much. */
    public static final float SCALE = 1.0f / 16.0f;

    /**
     * Bedrock model units as an <b>item</b> is drawn.
     *
     * <p>Y and Z inverted, which is vanilla's own conversion for an entity model shown as an item —
     * its trident renderer carries {@code scale(1, -1, -1)} for exactly this.
     *
     * <p><b>Nothing calls this.</b> It is kept because it is the one of the three that is not a
     * player's body, and deleting it would leave the question "what about an item that is not on a
     * player" with no answer recorded anywhere. That it has no caller is itself the finding: no
     * Bedrock attachable in the surveyed corpus is drawn anywhere but on a player.
     */
    public static final float[] AS_ITEM = {SCALE, -SCALE, -SCALE};

    /**
     * Bedrock model units as part of a <b>player</b>.
     *
     * <p>Y inverted, X and Z left alone. Java draws an entity model with +Y down, and both engines
     * agree on the other two — observed, after the alternatives were tried on screen.
     *
     * <p><b>X was flipped here for a while, and that was a wrong fix for a real bug.</b> A character
     * posed at −73° had its legs swing backwards, and flipping X made the space's handedness match
     * vanilla's — a plausible-sounding parity argument. It did not help, because the cause was
     * elsewhere: Bedrock's rotation angles turn the opposite way to a right-handed turn, and the
     * bone and animation paths were not applying the correction the block transpile already had.
     * Once that was fixed at the source, the flip remained as a pure mirror, and the model that had
     * been placed on the left shoulder appeared on the right.
     *
     * <p>Worth stating plainly: <b>a change made to fix a misdiagnosed symptom outlives the
     * diagnosis.</b> This one had to be undone by hand after the real cause was found.
     */
    public static final float[] ON_PLAYER = {SCALE, -SCALE, SCALE};

    /**
     * Bedrock model units as seen from the <b>first-person camera</b>.
     *
     * <p>In first person there is no player model to hang anything on, so the player space Bedrock
     * poses into has to be rebuilt against the camera. This is that space, and it was <b>solved from
     * {@link #ON_PLAYER} rather than guessed</b> — the third try at a coordinate system in this
     * feature, and the first two were guesses that cost a day each.
     *
     * <p>With the player facing south (yaw 0), so that the world axes can be named:
     *
     * <pre>
     * Bedrock -> world    the layer's chain, R(180) . scale(-1,-1,1) . ON_PLAYER   = diag( 1, 1,-1)/16
     * world   -> camera   camera looks along +Z, so its right is west and its -Z is south
     *                                                                              = diag(-1, 1,-1)
     * Bedrock -> camera                                                            = diag(-1, 1, 1)/16
     * </pre>
     *
     * <p>Reading it back: Bedrock −Z is the direction the player faces and stays the camera's −Z, so
     * the hand animation's {@code position: [0, 0, -11]} puts the model 0.69 blocks in FRONT of the
     * eye, which is where measurement said it lands. Bedrock +X is the model's left and becomes the
     * camera's −X. Bedrock +Y is up in both.
     *
     * <p>Odd parity — one axis flips — and that is correct here rather than the bug it was on
     * {@link #ON_PLAYER}: Bedrock's model space is already mirrored against the world, and the
     * world-to-camera step is a proper rotation, so the composition has to keep the mirror.
     */
    public static final float[] IN_FIRST_PERSON = {-SCALE, SCALE, SCALE};

    /**
     * How far down Bedrock's floor is from where a third-person player model's origin sits.
     *
     * <p>Java draws an entity model with <b>+Y pointing DOWN</b> and the origin a block and a half
     * up: a player's legs run from y 12 to y 24, and 24 is the ground. Bedrock authors the same
     * character with Y up from its feet. So Bedrock's y 0 belongs at Java's y 24.
     *
     * <p><b>The sign is the whole of it, and it was wrong the first time.</b> +Y being down means
     * this has to be POSITIVE to move the model down; it was negative, which lifted the model by a
     * block and a half instead of lowering it — and since the correction was also missing, the
     * character floated three blocks over the player's head. Exactly what the first screenshot
     * showed.
     */
    public static final float THIRD_PERSON_FLOOR = 24.0f / 16.0f;

    /**
     * A standing player's eye height, which is what first person drops the model by to put Bedrock's
     * y 0 on the ground. SC-180 §3.4.2.
     *
     * <p><b>A number, not a constant in the renderer.</b> It is read from the player per frame
     * because a crouching player's eye drops, and a model nailed to 1.62 would sink into the ground
     * with them. This field is the value for a player standing still, which is what an offline
     * measurement wants and what a frame of one is.
     */
    public static final float STANDING_EYE_HEIGHT = 1.62f;

    /**
     * What vanilla draws a player model at, and therefore what a third-person layer draws inside.
     *
     * <p><b>NOT MEASURED, and the reason recorded beside it is a premise this project has since
     * refuted.</b> {@code AvatarRenderer.scale} multiplies by this before any layer runs, so a
     * third-person attachable is 0.9375 of the size its numbers say — that half is read out of the
     * class rather than remembered, and it is why this number exists at all.
     *
     * <p>First person has no {@code AvatarRenderer} and nothing in its pass supplies a scale, so the
     * renderer applies this by hand. The reason it gives is that the model was "6.7% larger than the
     * same model on the same player seen from outside" — which assumes first person should be
     * third person in size. SC-180 §3.4.2 records that first person is <em>not</em> the player's
     * space, so that assumption is not available, and paired captures of the corpus's body-parented
     * character put its head visibly larger on the Bedrock client than at this scale. **Treat it as
     * unsubstantiated: it is the one number in this class no frame supports, and a constant that
     * outlived the diagnosis of the thing it was correcting has cost this project a mirror once
     * already.**
     */
    public static final float PLAYER_MODEL_SCALE = 0.9375f;

    private AttachableSpace() {
    }

    /** The space a view is drawn in. SC-180 §3.4.2: first person is not player space. */
    public static float[] of(boolean firstPerson) {
        return firstPerson ? IN_FIRST_PERSON : ON_PLAYER;
    }
}
