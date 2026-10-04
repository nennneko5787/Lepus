package net.nennneko5787.lepus.client.render;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * The two render types a Bedrock model needs. The 1.21.11 spelling. SC-180 §8.
 *
 * <p>The same pair as the 26.2 file under different names: here the no-cull variants say so, and
 * plain {@code entityCutout} culls. See that file for why both are no-cull and what the Z offset is
 * for.
 */
@SpecImpl("SC-180")
public final class AttachableRenderTypes {

    private AttachableRenderTypes() {
    }

    /** For cubes with thickness: the ordinary pass. */
    public static RenderType solid(Identifier texture) {
        return RenderTypes.entityCutoutNoCull(texture);
    }

    /**
     * For flat cubes: the same, <b>without</b> the Z-offset layer.
     *
     * <p><b>MEASURED on 1.21.11, and the offset is worse than nothing here.</b> The corpus puts an
     * eye two hundredths of a Bedrock unit in front of the face it decorates, and with this layer
     * the eye flickered between skin tone and eye colour — in third person as well as first.
     * Removing the layer made it <b>better</b>, so it was applied and pulling the wrong way for this
     * geometry: not inert, and not merely too small. The flat cubes still get a pass of their own,
     * which is what keeps the depth ordering between them and the solid cubes sane.
     */
    public static RenderType overlay(Identifier texture) {
        return RenderTypes.entityCutoutNoCull(texture);
    }
}
