package net.nennneko5787.lepus.core.format.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.ir.geometry.GeometryIr;
import net.nennneko5787.lepus.core.molang.MolangContext;
import net.nennneko5787.lepus.core.molang.MolangExpr;

/**
 * What an attachable looks like right now: which animations play, and the pose they make.
 * SC-170 §5, SC-180 §4, SC-130 §4.
 *
 * <p><b>{@code scripts.animate} is a list of amounts, not a list of animations.</b> An entry may be
 * a bare name, or an object whose one entry carries a Molang expression — and that expression is
 * <em>how much</em> of the animation to apply, not whether to apply it. Mojang: "the query in the
 * scripts section is only a blend value for the animation. It defines 'how much' the animation
 * plays, not when it plays and when it doesn't." Reading it as a switch is right for a condition
 * that only ever answers zero or one, which is what the corpus writes, and wrong for every vanilla
 * entry that blends a walk by {@code query.modified_move_speed}. SC-180 §4.1.1.
 *
 * <p><b>{@code pre_animation} runs first, every frame.</b> Its statements assign to {@code v.} —
 * {@code v.main_hand = c.item_slot == 'main_hand';} — and the conditions then read those variables.
 * Evaluating the conditions without running it first leaves every such variable at zero, which
 * silently answers "false" to every question a pack asks about itself.
 *
 * <p><b>Two animations that name the same bone both get it.</b> Their channel components add, in
 * order, and a transform is built once at the end — each scaled by its own blend. SC-180 §4.1,
 * where the three rules that were tried instead are recorded along with what each looked like on
 * screen.
 */
@SpecImpl({"SC-170#attachable/scripts", "SC-180#animation/bones"})
public final class AttachablePoser {


    /**
     * The wearer's bones as first person has them, for a player standing still. SC-180 §4.2.1.
     *
     * <p><b>Lives in the half of the build with no Minecraft in it so the renderer and the measuring
     * tool cannot disagree about it.</b> They did, three times in one day: the survey quietly posed
     * these at identity while the renderer turned them, and every extent it printed for a
     * `body`-parented attachable was a frame nobody was drawing. Whatever is here, both read it.
     *
     * <p><b>Half a turn on `body`, and it is a measurement.</b> Probe v10
     * ({@code spec/features/0005-attachables/probe/}) put an animation-less `body`-parented rig on
     * the Bedrock client in first person: it is VISIBLE, its +X marker on the screen's right, its
     * −X marker on the left, its forward marker straddling the camera — the whole rig turned half a
     * turn about the player's centre. The `waist`-parented character never sees this bone, which is
     * why she was correct all along and could never testify about it.
     *
     * <p>`head`'s half turn is vanilla's own — `base_pose` writes
     * `[q.target_x_rotation, q.target_y_rotation + 180, 0]`. `body` was identity here for as long
     * as this map existed, on the reading that those queries answer zero in first person; the probe
     * says the engine turns `body` regardless, which base_pose expresses if `q.target_y_rotation`
     * answers 180 there rather than 0. The mechanism is a guess; the half turn is not.
     */
    public static final Map<String, Mat4f> FIRST_PERSON_WEARER = Map.of(
            "body", Mat4f.rotationY(180.0f),
            "head", Mat4f.rotationY(180.0f));

    /** One entry of {@code scripts.animate}: something to play, and how much of it. */
    private record Track(Playable animation, Optional<MolangExpr> when) {
    }

    private final GeometryIr geometry;
    private final List<Track> tracks;
    private final List<MolangExpr> preAnimation;

    /**
     * @param animations   in {@code scripts.animate} order, each with its blend expression's source
     *                     or empty. An entry may be an animation or an animation CONTROLLER — a pack
     *                     writes both in the same list and looks both up in the same map
     * @param preAnimation the {@code scripts.pre_animation} statements, in order
     */
    public AttachablePoser(GeometryIr geometry,
            List<Map.Entry<Playable, Optional<String>>> animations,
            List<String> preAnimation) {
        this.geometry = geometry;
        this.tracks = new ArrayList<>();
        for (Map.Entry<Playable, Optional<String>> entry : animations) {
            tracks.add(new Track(entry.getKey(), entry.getValue().map(AttachablePoser::compile)));
        }
        this.preAnimation = preAnimation.stream().map(AttachablePoser::compile).toList();
    }

    /**
     * Every bone's transform at a moment, for the contexts this frame is in.
     *
     * <p><b>Every animation that names a bone contributes to it, by its blend.</b> SC-180 §4.1.
     */
    public Map<String, Mat4f> at(Playback playback, MolangContext context) {
        return at(playback, context, Map.of());
    }

    /**
     * As above, with bones the WEARER drives rather than the pack. SC-180 §4.2.
     *
     * <p>A Bedrock attachable names bones after the player's own — a halo's geometry is a cube-less
     * {@code head} at the player's head pivot with the ring hanging off it — and Bedrock drives
     * those from the player's skeleton. Without that a halo is a ring that stays where the model
     * declared it while the head turns underneath, which is how it was reported.
     *
     * <p><b>The wearer's transform goes OUTSIDE the pack's, not instead of it, and it is applied
     * AFTER the pack's own channels are summed.</b> A pack may pose a wearer-named bone and
     * Bedrock honours it: one character's first-person animation moves the cube-less {@code body}
     * bone she hangs off by {@code [0, -1, -6]}, and that third of a block is part of where she
     * lands. Replacing the pack's pose instead of composing with it discards that, and with it
     * the head — which is what was tried and what left her with no head at all.
     *
     * <p><b>Which side of the multiplication, measured by probe v13 and not by argument.</b> The
     * wearer's contribution is <em>written</em> as an animation (vanilla's first-person
     * {@code base_pose} is one) and that is where the old reading came from — but it is applied as
     * a separate outer pass, so the pack's position is composed <em>inside</em> the wearer's
     * rotation. The corpus proves the difference is real: its {@code .hand} writes
     * {@code position [0,-1,-6]} onto {@code body}, first person turns {@code body} by a half
     * turn, and the two orders put that −6 on opposite sides. Third person cannot tell them apart
     * — the wearer's transform is ~identity there — so the whole of this was invisible until a
     * probe put the bone where a wearer drives it and read the sign.
     *
     * <p>It reached the head and nothing else because the other character in the same pack hangs off
     * {@code waist}, which no wearer drives — the one asymmetry in the corpus that lets a single
     * change affect one of them and not the other.
     *
     * @param skeleton bone name → the wearer's transform for it, in Bedrock's space
     */
    public Map<String, Mat4f> at(Playback playback, MolangContext context,
            Map<String, Mat4f> skeleton) {
        // `pre_animation` FIRST, as documented — its assignments are what the conditions read.
        //
        // Evaluating the conditions ahead of it was tried, to explain why the corpus's first-person
        // animation does not show on the Bedrock client. It explains too much: neither character's
        // would play, and one of them demonstrably IS posed by hers there. What separates them is
        // the `loop` field, in AnimationSampler.
        for (MolangExpr statement : preAnimation) {
            statement.evaluate(context);
        }
        // A CONDITION IS AN AMOUNT, NOT A SWITCH. SC-180 §4.1.1. Mojang: "the query in the scripts
        // section is only a blend value for the animation. It defines 'how much' the animation
        // plays, not when it plays and when it doesn't." A bare entry is a blend of one; an entry
        // with an expression is a blend of whatever that expression answers, which for the
        // corpus's `v.main_hand && c.is_first_person` is still zero or one, and for vanilla's
        // `query.modified_move_speed` is a walk that grows with the walking.
        float[] blend = new float[tracks.size()];
        for (int track = 0; track < tracks.size(); track++) {
            Optional<MolangExpr> when = tracks.get(track).when();
            blend[track] = when.isEmpty() ? 1.0f : when.get().evaluate(context);
        }
        // PER CHANNEL, ADDITIVELY, IN ORDER — and the transform built once at the end. SC-180 §4.1.
        // Bedrock's own documentation: "the skeleton is reset to its default pose ... then
        // animations are applied per-channel-additively in order", and "the channels (x, y, and z)
        // are added separately across animations first, then converted to a transform once all
        // animations have been cumulatively applied". A matrix per animation cannot express that,
        // which is what this used to build before picking one of them.
        Map<String, AnimationSampler.Channels> channels = new LinkedHashMap<>();
        for (int track = 0; track < tracks.size(); track++) {
            tracks.get(track).animation().accumulate(playback, context, blend[track], channels);
        }
        Map<String, Mat4f> extra = new LinkedHashMap<>();
        channels.forEach((bone, accumulated) -> extra.put(bone, accumulated.transform()));
        // THE WEARER'S TRANSFORM GOES OUTSIDE THE PACK'S. MEASURED, probe v13, and it is the other
        // way round from what this line used to do.
        //
        // The reasoning that pinned the old order was "the wearer's contribution is one more
        // animation in the stack", which is true of how it is WRITTEN — vanilla's first-person
        // `base_pose` really is an animation — and false of when it is APPLIED. It is not another
        // entry in `scripts.animate`: it has no blend, no clock and no channels, and it is
        // composed after the pack's channels have been summed into one transform. So it is a
        // separate outer pass, not a member of the inner stack.
        //
        // What that costs when they are not told apart: the corpus's `.hand` writes `position
        // [0,-1,-6]` onto the cube-less `body` she hangs off by, and first person turns `body` by a
        // half turn. Put the translation outside (the old reading, §4.1's "translation outermost"
        // read across from a single bone) the −6 is added AFTER that turn and lands on the wrong
        // side; put it inside, the −6 is added first and the turn carries it. The sign flips, and
        // with it the whole character's placement in the only view where a wearer drives `body`.
        //
        // Probe v13 is the frame that decides it, and it is one bar: a bone named `body`, so a
        // wearer drives it, with `position [0,0,-24]` and no animation of its own, beside a control
        // bone no wearer drives. Third person put it at z −24 in both readings — the wearer's
        // transform is ~identity there, which is why the corpus never caught this. First person put
        // it at **+24, out of sight behind the camera**, which is the reading below and only the
        // reading below.
        skeleton.forEach((bone, driven) ->
                extra.merge(bone, driven, (animated, driver) -> driver.times(animated)));
        if (extra.isEmpty()) {
            return BoneMatrices.bindPose(geometry);
        }
        return BoneMatrices.posed(geometry, bone -> Optional.ofNullable(extra.get(bone.name())));
    }

    /**
     * A pack's expression, compiled, or a zero that never throws.
     *
     * <p>Constitution rule 5 in the place it matters most: this is evaluated inside a render pass,
     * and an expression that refuses to compile there is not a diagnostic but a client that stops
     * drawing. A condition that will not compile answers false, which costs that one animation.
     */
    private static MolangExpr compile(String source) {
        try {
            return MolangExpr.compile(source);
        } catch (RuntimeException unparsed) {
            return MolangExpr.zero();
        }
    }
}
