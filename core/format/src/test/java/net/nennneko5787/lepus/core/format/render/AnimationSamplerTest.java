package net.nennneko5787.lepus.core.format.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.nennneko5787.lepus.core.api.ProvesSpec;
import net.nennneko5787.lepus.core.format.diag.Diagnostics;
import net.nennneko5787.lepus.core.format.ir.animation.AnimationFiles;
import net.nennneko5787.lepus.core.format.ir.animation.AnimationIr;
import net.nennneko5787.lepus.core.format.json.Json;
import net.nennneko5787.lepus.core.format.json.JsonObject;
import net.nennneko5787.lepus.core.format.value.PackId;
import net.nennneko5787.lepus.core.format.value.Provenance;
import net.nennneko5787.lepus.core.molang.MolangContext;
import org.junit.jupiter.api.Test;

/**
 * An animation at a moment. SC-180 §4.
 *
 * <p>Every assertion is a transformed point rather than a matrix: "the bone is here at 0.5 seconds"
 * is the claim that matters, and a matrix comparison passes for the wrong reason too often.
 */
@ProvesSpec("SC-180")
class AnimationSamplerTest {

    private static final Provenance WHERE =
            Provenance.file(PackId.NONE, "animations/x.animation.json");
    private static final float EPSILON = 0.001f;

    /** No engine state: every query reads zero, which is what a static preview evaluates against. */
    private static final MolangContext NO_WORLD = MolangContext.standalone();

    private static AnimationSampler sampler(String json) {
        JsonObject root = Json.parse(json).asObject().orElseThrow();
        AnimationIr animation = AnimationFiles.parse(root, WHERE, new Diagnostics()).get(0);
        return new AnimationSampler(animation);
    }

    private static void assertPoint(float[] actual, float x, float y, float z) {
        assertEquals(x, actual[0], EPSILON, java.util.Arrays.toString(actual));
        assertEquals(y, actual[1], EPSILON, java.util.Arrays.toString(actual));
        assertEquals(z, actual[2], EPSILON, java.util.Arrays.toString(actual));
    }

    private static final String MOVING = """
            {
              "format_version": "1.8.0",
              "animations": {
                "animation.x": {
                  "loop": true,
                  "animation_length": 2,
                  "bones": {
                    "arm": { "position": { "0.0": [0, 0, 0], "2.0": [0, 10, 0] } }
                  }
                }
              }
            }""";

    @Test
    void aValueBetweenTwoKeyframesIsInterpolated() {
        // Half way between 0 and 10 at half the animation. Without interpolation the bone would
        // teleport between keyframes, which reads as the model juddering rather than as a sampler
        // that only returns keyframes.
        Map<String, Mat4f> pose = sampler(MOVING).at(1.0f, NO_WORLD);
        assertPoint(pose.get("arm").transform(0, 0, 0), 0, 5, 0);
    }

    @Test
    void aLoopingAnimationWrapsRatherThanRunningOff() {
        AnimationSampler sampler = sampler(MOVING);
        // 2.5s into a 2s loop is 0.5s in, which is a quarter of the way.
        assertPoint(sampler.at(2.5f, NO_WORLD).get("arm").transform(0, 0, 0), 0, 2.5f, 0);
        // And the loop point itself is the start again, not the end.
        assertPoint(sampler.at(2.0f, NO_WORLD).get("arm").transform(0, 0, 0), 0, 0, 0);
    }

    @Test
    void anAnimationThatDoesNotLoopHoldsItsLastFrame() {
        AnimationSampler sampler = sampler("""
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "animation.x": {
                      "animation_length": 2,
                      "bones": { "arm": { "position": { "0.0": [0, 0, 0], "2.0": [0, 10, 0] } } }
                    }
                  }
                }""");
        assertPoint(sampler.at(99.0f, NO_WORLD).get("arm").transform(0, 0, 0), 0, 10, 0);
    }

    @Test
    void aTimeOutsideTheKeyframesTakesTheNearestOne() {
        // Held at both ends. A channel whose first keyframe is at 1.0 has a value at 0.0 too, and
        // extrapolating instead would fling the bone away from the model before it starts.
        AnimationSampler sampler = sampler("""
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "animation.x": {
                      "bones": { "arm": { "position": { "1.0": [0, 4, 0], "2.0": [0, 8, 0] } } }
                    }
                  }
                }""");
        assertPoint(sampler.at(0.0f, NO_WORLD).get("arm").transform(0, 0, 0), 0, 4, 0);
        assertPoint(sampler.at(9.0f, NO_WORLD).get("arm").transform(0, 0, 0), 0, 8, 0);
    }

    /**
     * A curved channel, written the way the corpus writes them: {@code lerp_mode} on every keyframe.
     *
     * <p>Values that make the curve and the line disagree by a readable amount — a peak and a
     * trough, so the spline overshoots where a line cannot.
     */
    private static final String CURVED = """
            {
              "format_version": "1.8.0",
              "animations": {
                "animation.x": {
                  "bones": { "arm": { "position": {
                    "0.0": { "post": [0,   0, 0], "lerp_mode": "catmullrom" },
                    "1.0": { "post": [0,  10, 0], "lerp_mode": "catmullrom" },
                    "2.0": { "post": [0,   0, 0], "lerp_mode": "catmullrom" },
                    "3.0": { "post": [0, -10, 0], "lerp_mode": "catmullrom" }
                  } } }
                }
              }
            }""";

    /**
     * {@code catmullrom} is a spline through the neighbouring keyframes, not a line. SC-180 §4.1.2.
     *
     * <p>Half way from 10 down to 0, with 0 behind and −10 ahead, the curve reads <b>6.25</b> where
     * a line reads 5 — it leaves the peak flat and steepens into the fall, which is the whole point
     * of the mode. The number is the uniform tension-half form evaluated by hand, so a change to the
     * arithmetic has to disagree with a written-out expectation rather than with itself.
     *
     * <p>233 keyframes of the surveyed corpus ask for this and were sampled as straight lines.
     */
    @Test
    void aCatmullromSegmentCurvesThroughItsNeighbours() {
        assertPoint(sampler(CURVED).at(1.5f, NO_WORLD).get("arm").transform(0, 0, 0),
                0, 6.25f, 0);
    }

    /**
     * At the ends of a channel the curve clamps: the end keyframe stands in for its own neighbour.
     *
     * <p>There is no keyframe before the first one, and inventing a tangent by extrapolating would
     * let the bone start outside every value the pack wrote. Half way through the first segment this
     * reads 5.625 — curved, because there IS a keyframe on the far side, and not the 6.25 of a
     * segment with neighbours on both.
     */
    @Test
    void theFirstAndLastSegmentsClampTheMissingNeighbour() {
        assertPoint(sampler(CURVED).at(0.5f, NO_WORLD).get("arm").transform(0, 0, 0),
                0, 5.625f, 0);
    }

    /**
     * <b>Either</b> end of a segment asking for a curve is enough. SC-180 §4.1.2.
     *
     * <p>Bedrock's editor takes the linear path only when the keyframe before is linear AND the one
     * after is too, so a single {@code catmullrom} keyframe curves the segment on both sides of it.
     * A reader that asked only the earlier keyframe would sample the run-up to every eased landing
     * as a straight line — and would agree with this build's other tests, because the corpus marks
     * every keyframe of a curved channel.
     */
    @Test
    void aSegmentCurvesWhenOnlyItsLaterKeyframeAsksFor() {
        AnimationSampler sampler = sampler("""
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "animation.x": {
                      "bones": { "arm": { "position": {
                        "0.0": [0,   0, 0],
                        "1.0": [0,  10, 0],
                        "2.0": { "post": [0, 0, 0], "lerp_mode": "catmullrom" },
                        "3.0": [0, -10, 0]
                      } } }
                    }
                  }
                }""");
        assertPoint(sampler.at(1.5f, NO_WORLD).get("arm").transform(0, 0, 0), 0, 6.25f, 0);
    }

    /**
     * A keyframe with two values steps: the channel arrives at one and leaves with the other.
     *
     * <p>The incoming segment ends at {@code pre} and the outgoing one starts at {@code post}, so
     * the bone is at 10 an instant before the keyframe and at 0 an instant after. Reading `post` for
     * both — which this sampler did — makes the approach a ramp to the wrong value and loses the
     * step entirely.
     */
    @Test
    void aKeyframeWithTwoValuesStepsBetweenThem() {
        AnimationSampler sampler = sampler("""
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "animation.x": {
                      "bones": { "arm": { "position": {
                        "0.0": [0, 0, 0],
                        "1.0": { "pre": [0, 10, 0], "post": [0, 0, 0] },
                        "2.0": [0, 10, 0]
                      } } }
                    }
                  }
                }""");
        assertPoint(sampler.at(0.5f, NO_WORLD).get("arm").transform(0, 0, 0), 0, 5, 0);
        assertPoint(sampler.at(1.0f, NO_WORLD).get("arm").transform(0, 0, 0), 0, 0, 0);
        assertPoint(sampler.at(1.5f, NO_WORLD).get("arm").transform(0, 0, 0), 0, 5, 0);
    }

    @Test
    void aBoneTheAnimationDoesNotMentionIsAbsentRatherThanIdentity() {
        // The caller composes this OVER the model's bind pose. An identity written for an
        // unmentioned bone would erase whatever that bone was declared with - which is every
        // rotation in the geometry file.
        Map<String, Mat4f> pose = sampler(MOVING).at(0.0f, NO_WORLD);
        assertTrue(pose.containsKey("arm"));
        assertFalse(pose.containsKey("head"), pose.keySet().toString());
    }

    @Test
    void aMolangComponentIsEvaluatedRatherThanDropped() {
        // Real packs write these constantly, mixed with plain numbers in the same vector. Against a
        // context with no world every query reads zero, so this is -10 - which is the static pose a
        // preview shows and exactly what the corpus's head bones come to.
        AnimationSampler sampler = sampler("""
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "animation.x": {
                      "bones": {
                        "head": {
                          "rotation": ["query.target_x_rotation - 10 - this", 0, 0]
                        }
                      }
                    }
                  }
                }""");
        // A Bedrock rotation of -10 about X tips a point above the origin towards +Z, because
        // Bedrock's angles turn the opposite way to a right-handed turn (BoneMatrices.rotate).
        // This assertion used to expect the right-handed answer and agreed with code that was
        // wrong the same way; a character's legs swinging backwards is what exposed both.
        float[] at = sampler.at(0.0f, NO_WORLD).get("head").transform(0, 1, 0);
        assertEquals(0.0f, at[0], EPSILON);
        assertEquals(Math.cos(Math.toRadians(10)), at[1], EPSILON);
        assertEquals(Math.sin(Math.toRadians(10)), at[2], EPSILON);
    }

    @Test
    void anExpressionTheEngineCannotParseDoesNotEndTheFrame() {
        // Constitution rule 5, at its sharpest: this runs inside a render pass sixty times a
        // second, so one unparsed expression is not a diagnostic - it is a client that stops
        // drawing. The bone loses that component and keeps the rest.
        AnimationSampler sampler = sampler("""
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "animation.x": {
                      "bones": { "arm": { "position": ["!!! not molang !!!", 7, 0] } }
                    }
                  }
                }""");
        assertPoint(sampler.at(0.0f, NO_WORLD).get("arm").transform(0, 0, 0), 0, 7, 0);
        assertEquals(1, sampler.unreadableExpressions().size());
    }

    @Test
    void scaleZeroHidesABone() {
        // How a pack turns a bone off - `"scale": 0`, a bare number meaning all three axes. Every
        // point collapses to the bone's own origin, which draws nothing.
        AnimationSampler sampler = sampler("""
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "animation.x": { "bones": { "bag": { "scale": 0 } } }
                  }
                }""");
        assertPoint(sampler.at(0.0f, NO_WORLD).get("bag").transform(5, 5, 5), 0, 0, 0);
    }

    /**
     * <b>Past its length, the three kinds of {@code loop} do three different things.</b> SC-180 §4.1.
     *
     * <p>The middle row is the one this build got wrong for most of this feature's life, and the
     * corpus could not see it: {@code AnimationIr} carried a boolean, {@code "hold_on_last_frame"} was
     * read as a boolean it is not, and so a plain non-looping animation and a holding one were the
     * same animation. **A non-looping animation therefore held its last keyframe forever** — which no
     * frame ever showed, because the ledger's own note says why: no animation in the surveyed corpus
     * is plainly non-looping, so no pose was ever sampled past an end.
     *
     * <p>Asserted through {@link AnimationSampler#accumulate} and not through
     * {@link AnimationSampler#at}, because {@code at} reads the channels directly and so never asks
     * whether the animation still has anything to say. A frame asks, and that is the path this has to
     * hold on.
     */
    @Test
    void pastItsLengthALoopWrapsAHoldingAnimationHoldsAndAPlainOneHasEnded() {
        String frames = """
                 "bones": { "head": { "position": {
                   "0.0": [0, 0, 0], "1.0": [0, 10, 0], "2.0": [0, 20, 0] } } }""";

        // 2.5s into a 2s loop is 0.5s in, which is halfway up the first segment: 0 at t=0 and 10 at
        // t=1, so 5. A loop that answered 10 or 20 would be clamping, not wrapping.
        assertEquals(5.0f, headY(looping(frames)), 0.01f, "a loop wraps");

        // The same instant with hold_on_last_frame, which keeps the last keyframe.
        assertEquals(20.0f, headY(holding(frames)), 0.01f,
                "hold_on_last_frame keeps contributing the last keyframe past the end");

        // And the same instant with neither, which is the row that was never implemented: it has ENDED,
        // and ending removes the pose rather than freezing it.
        assertTrue(finished(once(frames)),
                "a plain non-looping animation past its length contributes nothing at all");
    }

    /** {@code loop: true} with a length, so it is a loop rather than a zero-length one. */
    private static AnimationSampler looping(String frames) {
        return sampler("""
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "animation.x": { "loop": true, "animation_length": 2, %s }
                  }
                }""".formatted(frames));
    }

    private static AnimationSampler holding(String frames) {
        return sampler("""
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "animation.x": { "loop": "hold_on_last_frame", "animation_length": 2, %s }
                  }
                }""".formatted(frames));
    }

    private static AnimationSampler once(String frames) {
        return sampler("""
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "animation.x": { "animation_length": 2, %s }
                  }
                }""".formatted(frames));
    }

    /** The composed Y of {@code head}, through the path a frame takes. */
    private static float headY(AnimationSampler sampler) {
        return contributed(sampler, 2.5f).get("head").transform(0, 0, 0)[1];
    }

    /** True when the animation added nothing for {@code head} at that moment. */
    private static boolean finished(AnimationSampler sampler) {
        return !contributed(sampler, 2.5f).containsKey("head");
    }

    /**
     * The bones an animation actually contributed at a moment.
     *
     * <p>Two {@code accumulate} calls because {@link Playback#timeOf} starts a clock on the first
     * frame an animation is asked for and answers zero for it — so one call would sample every
     * animation at t=0 whatever time was asked for, which is precisely the sort of test that passes
     * for the wrong reason.
     */
    private static Map<String, Mat4f> contributed(AnimationSampler sampler, float seconds) {
        Playback playback = new Playback();
        sampler.accumulate(playback, NO_WORLD, 1.0f, new java.util.LinkedHashMap<>());
        playback.advanceTo(seconds);
        Map<String, AnimationSampler.Channels> channels = new java.util.LinkedHashMap<>();
        sampler.accumulate(playback, NO_WORLD, 1.0f, channels);
        Map<String, Mat4f> out = new java.util.LinkedHashMap<>();
        channels.forEach((bone, accumulated) -> out.put(bone, accumulated.transform()));
        return out;
    }

    /**
     * <b>Whether an animation can draw at all is a different fact from what blend it was
     * given.</b> SC-180 §4.1.
     *
     * <p>Both fixtures are constants only, so neither has an {@code animation_length} and both have a
     * length of zero — a pack that writes no keyframes has a last keyframe at t=0, which is the whole
     * of the corpus's first-person animations. A zero-length <b>loop</b> restarts on the frame it
     * starts and therefore ends on every frame, contributing nothing at any blend. That much is
     * settled, and it is measured: the corpus's two characters are conditioned identically and only the
     * one carrying {@code loop: true} goes unposed.
     *
     * <p><b>The zero-length {@code hold_on_last_frame} below is NOT settled, and this test asserts the
     * behaviour rather than the truth.</b> "Holds the last keyframe" and "finished at t=0" are the same
     * instant; Mojang's own resource pack never writes the field, so there is nothing to read it off;
     * and the corpus's single occurrence is the animation two readings of a reported bug both depend
     * on. {@code holdsWithoutLength} is the decision, and it is a one-line change once a frame exists.
     * **If that frame shows the marker moving, this assertion is what has to flip.**
     */
    @Test
    void aZeroLengthLoopDrawsNothingAndTheHeldFrameCurrentlyDoes() {
        assertFalse(sampler("""
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "animation.x": { "loop": true,
                                      "bones": { "head": { "rotation": [0, 180, 0] } } }
                  }
                }""").contributes(),
                "a zero-length loop ends on every frame and draws nothing at any blend");

        assertTrue(sampler("""
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "animation.x": { "loop": "hold_on_last_frame",
                                      "bones": { "head": { "rotation": [0, 180, 0] } } }
                  }
                }""").contributes(),
                "UNMEASURED: a zero-length hold_on_last_frame currently holds. Flip this if the "
                        + "Bedrock frame shows the marker standing still.");
    }

    /**
     * <b>A length is what makes a loop a loop.</b> The same {@code loop: true} with a length draws,
     * so the rule above is about the pair and not about {@code loop} on its own — which is the
     * reading that would silently disable every looping animation in every pack.
     */
    @Test
    void aLoopWithALengthIsNotFinishedOnEveryFrame() {
        assertTrue(sampler("""
                {
                  "format_version": "1.8.0",
                  "animations": {
                    "animation.x": { "loop": true, "animation_length": 2,
                                      "bones": { "head": { "rotation": [0, 90, 0] } } }
                  }
                }""").contributes());
    }
}
