package net.nennneko5787.lepus.client.render;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * The two render types a Bedrock model needs. The 26.2 spelling. SC-180 §8.
 *
 * <p><b>Both are no-cull</b>, because a Bedrock model is full of one-sided decoration — a hair
 * strand, an eye, a skirt panel — that is meant to be visible from either side. Here the plain
 * {@code entityCutout} already is; {@code entityCutoutCull} is the culling one.
 *
 * <p>A per-version file because the names differ, not the meaning. The flat-cube pass uses the same
 * no-cull cutout layer as the solid pass; per-quad model-space offsets separate decals. Applying
 * vanilla's Z-offset layer only on 26.2 made the same model render differently across versions.
 */
@SpecImpl("SC-180")
public final class AttachableRenderTypes {

    private AttachableRenderTypes() {
    }

    /** For cubes with thickness: the ordinary pass. */
    public static RenderType solid(Identifier texture) {
        return RenderTypes.entityCutout(texture);
    }

    /**
     * For flat cubes: the same no-cull cutout layer. Their separation is handled by the geometry
     * submitter so both Minecraft versions use identical depth behavior.
     */
    public static RenderType overlay(Identifier texture) {
        return RenderTypes.entityCutout(texture);
    }
}
