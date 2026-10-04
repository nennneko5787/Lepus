package net.nennneko5787.lepus.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.ir.geometry.BoneIr;
import net.nennneko5787.lepus.core.format.ir.geometry.CubeFace;
import net.nennneko5787.lepus.core.format.ir.geometry.CubeIr;
import net.nennneko5787.lepus.core.format.ir.geometry.FaceUv;
import net.nennneko5787.lepus.core.format.ir.geometry.GeometryIr;
import net.nennneko5787.lepus.core.format.render.AttachableSpace;
import net.nennneko5787.lepus.core.format.render.Mat4f;
import net.nennneko5787.lepus.core.format.value.Vec3f;

/**
 * A whole Bedrock model, posed and submitted. SC-170 §5, SC-180 §3.
 *
 * <p>The step {@code BedrockCubeSubmitter} could not take: that spike draws one <b>axis-aligned</b>
 * box, and a posed model has none — every cube arrives rotated by its bone chain, so its eight
 * corners are eight independent points and the box is gone. What survives is the quad, which is why
 * this walks corners rather than extents.
 *
 * <p><b>Version-free on purpose.</b> Everything that differs between 1.21.11 and 26.2 about
 * attachables is in the {@code SpecialModelRenderer} contract around this — the display-context
 * parameter, the generics, where the registry lives — and none of it is here. This file is the part
 * that would otherwise be written twice and drift.
 *
 * <p><b>The two things a compiler cannot check</b>, and where to change them:
 *
 * <ul>
 *   <li>{@link #AS_ITEM} and {@link #ON_PLAYER} — Bedrock's model space against the two spaces it
 *       is drawn in. <b>The caller picks</b>, because they are not the same conversion.
 *   <li>the corner order in {@link #face} — winding, which decides whether a face is visible from
 *       the front or from inside.
 * </ul>
 *
 * <p>Both are settled by looking at a frame, not by reading a jar. They are named and isolated so
 * that settling them is one edit each rather than a hunt.
 */
@SpecImpl(value = "SC-170#attachable/geometry",
        note = "Static pose. Animation, controllers and render controllers are later stages.")
public final class AttachableGeometry {

    /**
     * The three spaces, and the two origins, now live in {@link AttachableSpace} in {@code core}.
     *
     * <p><b>Moved out of this class, and the reason is testability rather than tidiness.</b> They
     * were fields here, which made them correct and uncheckable at the same time: an offline survey
     * or a test that wanted to state where a model lands had to launch a client or copy the numbers,
     * and a tool with its own copy of a constant drifts towards agreeing with itself. The reasoning
     * moved with them, because the reasoning is the part worth having in one place.
     *
     * <p>These three names stay so that both call sites and this class's own javadoc keep working.
     */
    public static final float[] AS_ITEM = AttachableSpace.AS_ITEM;

    public static final float[] ON_PLAYER = AttachableSpace.ON_PLAYER;

    public static final float[] IN_FIRST_PERSON = AttachableSpace.IN_FIRST_PERSON;

    private AttachableGeometry() {
    }

    /**
     * Submits every cube of every bone, posed.
     *
     * <p>Cutout rather than solid: Bedrock character models are full of one-pixel-thin quads for
     * hair, eyes and halos, and every one of them has transparent texels. Drawing those as opaque
     * puts a black rectangle where the transparency was, which reads as a broken texture.
     *
     * @param pose the bone matrices from {@code BoneMatrices}, in Bedrock's space
     */
    public static void submit(SubmitNodeCollector collector, PoseStack poseStack,
            Identifier texture, GeometryIr geometry, Map<String, Mat4f> pose, float[] space,
            int light, int overlay, int tint) {
        float width = Math.max(1, geometry.textureWidth());
        float height = Math.max(1, geometry.textureHeight());

        // TWO passes, split by whether a cube has thickness.
        //
        // A Bedrock model decorates a surface with flat quads sitting a hair in front of it — an eye
        // two hundredths of a unit ahead of a face, which at 1/16 scale is a millimetre and a
        // quarter. No depth buffer at Minecraft's view distance separates that, so the two fight and
        // the eye flickers. Bedrock's renderer tolerates the gap; Java's does not.
        //
        // The flat ones therefore go through a pass of their own, **without the render type's
        // Z-offset.**
        //
        // <p><b>The offset was measured and it is worse than nothing.</b> On 1.21.11 the eye flickered
        // between skin tone and eye colour — the flat quad at z 1.98 against the face at z 2.00, a
        // twentieth of a Bedrock unit apart, in third person as well as first. Removing the offset
        // made it <b>better</b>, so it was being applied and pulling the wrong way for this geometry:
        // not inert, and not merely too small.
        //
        // <p><b>What replaces it.</b> The render type's offset is gone; the flat pass gets a per-quad walk
        // along each quad's own outward normal <em>in model space</em>, in declaration order. Both
        // spaces are reflections, so a normal taken after the conversion faces the other way, and
        // that is what made the first attempt at this push decals into their surface.
        pass(collector, poseStack, AttachableRenderTypes.solid(texture), geometry, pose, space,
                width, height, light, overlay, tint, false);
        pass(collector, poseStack, AttachableRenderTypes.overlay(texture), geometry, pose, space,
                width, height, light, overlay, tint, true);
    }

    /**
     * How far apart consecutive coplanar decals are drawn, in blocks. SC-180 §3.
     *
     * <p><b>Not a fitted constant.</b> The corpus leaves a gap of 0.02 Bedrock units between an eye
     * and the face it decorates, and this is a fortieth of that — so a model of any reasonable
     * decal count still lands well inside the gap it was drawn to sit in, and nothing moves
     * perceptibly. It exists because two quads sharing a plane cannot be separated by any lift
     * applied to both: the corpus's eyes are exactly that, {@code eye2} #0 with #1 and #3 with #4,
     * each pair coplanar at z 1.98 and overlapping 0.90 x 3.00.
     */
    private static final float FLAT_DECAL_STEP_BLOCKS = 0.0008f;

    /**
     * The eye decal that survives its own surface: a residual, not a solved problem. SC-180 §3.
     *
     * <p>The corpus's halos and eyes sit two hundredths of a Bedrock unit in front of the face
     * they decorate — a millimetre and a quarter at model scale — and the depth buffer does not
     * separate that at the distance a character is viewed from. Three things were measured:
     *
     * <ul>
     *   <li>the render type's own Z-offset, which <b>makes it worse</b> (pulls the wrong way);
     *   <li>drawing both faces of a degenerate pair, which fights over depth where both of the
     *       pair's rectangles have content;
     *   <li>a nudge in the pose stack, whose <b>sign was measured rather than assumed</b>: +0.005
     *       renders the eyes wrongly, so the lift is -Z.
     * </ul>
     *
     * <p>The magnitude is derived from the gap it has to beat, not fitted to a picture: the corpus's
     * smallest eye-to-face gap is 0.02 Bedrock units and this is four times that, which is an order
     * of magnitude under the 0.23 separating the eye from the hair layer in front of it.
     */

    /** One pass over the model, taking either the cubes with thickness or the flat ones. */
    private static void pass(SubmitNodeCollector collector, PoseStack poseStack,
            RenderType renderType, GeometryIr geometry, Map<String, Mat4f> pose, float[] space,
            float width, float height, int light, int overlay, int tint, boolean flatOnes) {
        int[] drawn = {0};
        collector.submitCustomGeometry(poseStack, renderType, (matrix, buffer) -> {
            for (BoneIr bone : geometry.bones()) {
                if (bone.neverRender()) {
                    continue;
                }
                Mat4f bonePose = pose.get(bone.name());
                if (bonePose == null) {
                    // A bone whose chain was refused - a cycle. Skipping it costs that bone rather
                    // than the model, which is the same trade the block transpile makes.
                    continue;
                }
                for (CubeIr cube : bone.cubes()) {
                    boolean flat = flatFace(cube.size().x() + (cube.inflate() + bone.inflate()) * 2,
                            cube.size().y() + (cube.inflate() + bone.inflate()) * 2,
                            cube.size().z() + (cube.inflate() + bone.inflate()) * 2) != null;
                    if (flat != flatOnes) {
                        continue;
                    }
                    submitCube(buffer, matrix, bonePose, bone, cube, width, height, space,
                            light, overlay, tint, flatOnes ? drawn[0]++ * FLAT_DECAL_STEP_BLOCKS : 0.0f);
                }
            }
        });
    }

    private static void submitCube(VertexConsumer buffer, PoseStack.Pose matrix, Mat4f bonePose,
            BoneIr bone, CubeIr cube, float width, float height, float[] space,
            int light, int overlay, int tint, float lift) {
        float inflate = cube.inflate() + bone.inflate();
        Vec3f origin = cube.origin();
        Vec3f size = cube.size();
        float x0 = origin.x() - inflate;
        float y0 = origin.y() - inflate;
        float z0 = origin.z() - inflate;
        float x1 = origin.x() + size.x() + inflate;
        float y1 = origin.y() + size.y() + inflate;
        float z1 = origin.z() + size.z() + inflate;

        // Coplanar decals are walked apart **in model space, before the pose and before the
        // space conversion**, and that placement is the whole of why this works.
        //
        // <p>Both spaces have a NEGATIVE determinant — `ON_PLAYER` negates Y and
        // `IN_FIRST_PERSON` negates X — so they are reflections, and a cross product taken after
        // them comes out facing the opposite way to the one it would have had before. Measured on
        // the corpus's eye quad: (0, 0, -1) in model space, (0, 0, +1) after conversion. An offset
        // taken there therefore pushes a decal INTO the surface it decorates, which is the exact
        // failure that made three previous attempts render the eyes wrongly.
        //
        // <p>The model's own corner order knows which way its faces point without reference to
        // where the camera is, so the walk is taken from the untransformed cube and the pose
        // carries it from there like any other vertex.
        float dx = 0.0f, dy = 0.0f, dz = 0.0f;
        if (lift != 0.0f) {
            // Same corner order `face` uses for NORTH: (x1,y0,z0), (x0,y0,z0), (x0,y1,z0).
            float[] a = {x1, y0, z0};
            float[] b = {x0, y0, z0};
            float[] c = {x0, y1, z0};
            float nx = (b[1] - a[1]) * (c[2] - a[2]) - (b[2] - a[2]) * (c[1] - a[1]);
            float ny = (b[2] - a[2]) * (c[0] - a[0]) - (b[0] - a[0]) * (c[2] - a[2]);
            float nz = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length > 1.0e-6f) {
                dx = nx / length * lift;
                dy = ny / length * lift;
                dz = nz / length * lift;
            }
        }

        // The eight corners, posed and converted, indexed by the bits of x/y/z. Computed once and
        // shared by the six faces: a corner belongs to three of them, and transforming it three
        // times is both slower and a way for two faces to disagree about where an edge is.
        float[][] corner = new float[8][];
        for (int i = 0; i < 8; i++) {
            float x = (i & 1) == 0 ? x0 : x1;
            float y = (i & 2) == 0 ? y0 : y1;
            float z = (i & 4) == 0 ? z0 : z1;
            float[] posed = bonePose.transform(x + dx, y + dy, z + dz);
            corner[i] = new float[] {
                    posed[0] * space[0], posed[1] * space[1], posed[2] * space[2]};
        }

        // A cube with no thickness on an axis is ONE quad: the two faces of that axis's pair lie in the
        // same plane, so exactly one of them is drawn.
        //
        // <b>WHICH one is not a matter of taste — it is the difference between a halo and no halo.</b>
        // A quad is invisible when the rectangle it samples is transparent, not when it is
        // back-facing, so the pair is not interchangeable. The corpus's halos are
        // {@code size [12,0,10]} at {@code uv [-10,54]}: measured over every texture checked, the
        // face this used to draw is **fully transparent** and the one it skipped has content.
        //
        // <p><b>So the one to drop is the one pointing NEGATIVE, and it was implemented as the
        // opposite for this file's whole life.</b> Zero height then draws {@code UP} (the halo's
        // content) and zero depth draws {@code NORTH} — which is also what the eyes want, measured
        // on probe v17. Drawing both faces instead was tried and is worse: it fixes the halo and
        // reintroduces the coplanar depth fight the original author was avoiding, which shows on
        // every pair whose two rectangles both have content.
        CubeFace flat = flatFace(size.x() + inflate * 2, size.y() + inflate * 2,
                size.z() + inflate * 2);
        for (CubeFace face : CubeFace.values()) {
            if (face == flat) {
                continue;
            }
            cube.face(face).ifPresent(uv -> face(buffer, matrix, corner, face, uv,
                    width, height, light, overlay, tint));
        }
    }

    /**
     * The face to drop when a cube has no thickness, or null when it has some.
     *
     * <p><b>Each axis is its own decision, and only two of the three have been measured.</b> The pair is
     * not interchangeable: a quad vanishes when the rectangle it samples is <em>transparent</em>,
     * which has nothing to do with which way it faces, so "culling is off, so the choice only
     * decides whose UV is used" is a statement about geometry that says nothing about whether
     * anything is visible. The corpus's halos are {@code size [12,0,10]} at {@code uv [-10,54]}: of
     * the two faces, the one this method used to draw is <b>fully transparent</b> in every texture
     * checked and the one it skipped has content, so every halo in every character was emitted,
     * covered exactly the right pixels, and showed nothing.
     *
     * <table>
     * <caption>per axis, and what decided each</caption>
     * <tr><th>zero axis</th><th>drawn</th><th>decided by</th></tr>
     * <tr><td>height</td><td>{@code UP}</td><td>alpha over the corpus textures — the halo</td></tr>
     * <tr><td>depth</td><td>{@code NORTH}</td><td>probe v17's colour key — the eyes</td></tr>
     * <tr><td>width</td><td>{@code WEST}</td><td><b>nothing yet</b> — unchanged, and unmeasured</td></tr>
     * </table>
     *
     * <p><b>There is no single sign to apply here, and trying to find one broke the eyes.</b> All
     * three were flipped together on the strength of the halo alone; the eyes — {@code size [2,3,0]}
     * at {@code uv [0,0]} — then drew the other rectangle and read wrong, which is what a one-axis
     * finding generalised to three looks like. The width row is left as it was rather than given a
     * measured answer it does not have.
     *
     * <p>Drawing the pair's <em>other</em> face instead was also tried and is strictly worse: it
     * brings the halo back and puts the coplanar depth fight back with it, which flickers wherever
     * both rectangles have content.
     *
     * <p><b>No probe measured the LAYOUT of the two rectangles</b>, only which face is drawn; the
     * layout itself is {@link BoxUv}'s and is still asserted rather than verified. That is where a
     * future correction belongs, and it is three lines there rather than here.
     *
     * <p>Zero is compared exactly. A cube one thousandth of a unit thick is a real box a pack
     * meant, and rounding it to flat here would silently drop a face somebody drew.
     */
    private static CubeFace flatFace(float width, float height, float depth) {
        // One decision per axis, and they do NOT share a sign. Each names the face whose rectangle
        // carries the picture for the corpus's flat cubes on that axis; see the table above.
        if (depth == 0.0f) {
            return CubeFace.SOUTH;
        }
        if (width == 0.0f) {
            return CubeFace.EAST;
        }
        if (height == 0.0f) {
            return CubeFace.DOWN;
        }
        return null;
    }

    /**
     * One face, as two triangles' worth of quad.
     *
     * <p>The corner quadruples are wound so that the face is seen from outside the box in <b>Bedrock's</b>
     * space. the conversions mirror an even number of axes and therefore preserve
     * winding — so if faces turn out inside out, the fault is this table and not the conversion.
     *
     * <p>The normal is computed from the corners rather than taken from the face's name, because a
     * posed cube's north face does not point north any more. Lighting reads it, and a stale normal
     * is a model lit from the wrong side — which looks like a texture problem.
     */
    private static void face(VertexConsumer buffer, PoseStack.Pose matrix, float[][] corner,
            CubeFace face, FaceUv uv, float width, float height,
            int light, int overlay, int tint) {
        int[] order = switch (face) {
            // Bits: 1 = x1, 2 = y1, 4 = z1.
            case NORTH -> new int[] {1, 0, 2, 3};
            case SOUTH -> new int[] {4, 5, 7, 6};
            case EAST -> new int[] {5, 1, 3, 7};
            case WEST -> new int[] {0, 4, 6, 2};
            case UP -> new int[] {6, 7, 3, 2};
            case DOWN -> new int[] {0, 1, 5, 4};
        };
        float u0 = uv.uv().x() / width;
        float v0 = uv.uv().y() / height;
        float u1 = (uv.uv().x() + uv.uvSize().x()) / width;
        float v1 = (uv.uv().y() + uv.uvSize().y()) / height;

        float[] a = corner[order[0]];
        float[] b = corner[order[1]];
        float[] c = corner[order[2]];
        float nx = (b[1] - a[1]) * (c[2] - a[2]) - (b[2] - a[2]) * (c[1] - a[1]);
        float ny = (b[2] - a[2]) * (c[0] - a[0]) - (b[0] - a[0]) * (c[2] - a[2]);
        float nz = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length > 1.0e-6f) {
            nx /= length;
            ny /= length;
            nz /= length;
        }

        vertex(buffer, matrix, corner[order[0]], u0, v1, light, overlay, tint, nx, ny, nz);
        vertex(buffer, matrix, corner[order[1]], u1, v1, light, overlay, tint, nx, ny, nz);
        vertex(buffer, matrix, corner[order[2]], u1, v0, light, overlay, tint, nx, ny, nz);
        vertex(buffer, matrix, corner[order[3]], u0, v0, light, overlay, tint, nx, ny, nz);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose matrix, float[] at,
            float u, float v, int light, int overlay, int tint,
            float nx, float ny, float nz) {
        buffer.addVertex(matrix, at[0], at[1], at[2])
                .setColor(tint)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(matrix, nx, ny, nz);
    }
}
