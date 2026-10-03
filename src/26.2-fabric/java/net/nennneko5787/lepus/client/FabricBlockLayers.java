package net.nennneko5787.lepus.client;

import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * <b>Nothing to do on 26.2, and the reason is that the question has a different shape there.</b>
 *
 * <p>The 1.21.11 half writes {@code TYPE_BY_BLOCK}, because on that version a block's chunk layer
 * comes from a per-block map. On 26.2 it does not: {@code BakedQuad.MaterialInfo.of} derives the
 * layer from the <b>sprite's</b> transparency via {@code ChunkSectionLayer.byTransparency}, and
 * Fabric API removed {@code BlockRenderLayerMap} in the build that targets it because the producer
 * side it offered no longer exists to serve. Verified in the 25.3.2 Fabric API jar, which has no
 * such class, and in 26.2's own bytecode.
 *
 * <p><b>So this is not a stub that could not be finished; it is a method with no question to ask
 * here.</b> A block that declares {@code render_method: "alpha_test"} on 26.2 needs the sprite
 * itself to carry the right transparency, which is a different fix in a different place - most
 * likely {@code render_type} in the {@code .png.mcmeta} this project already writes for flipbooks.
 * SC-150 §5.4 records it as open, and it wants measuring rather than assuming.
 *
 * <p>Registered rather than absent so that the shared client entry point compiles against one
 * name on both versions, the same way {@code FabricAttachableLayer} does.
 */
@SpecImpl("SC-150#minecraft:material_instances")
public final class FabricBlockLayers {

    private FabricBlockLayers() {
    }

    public static void register() {
        // Deliberately empty. See above, and SC-150 §5.4.
    }
}