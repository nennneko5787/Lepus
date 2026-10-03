package net.nennneko5787.lepus.client;

import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * <b>Nothing to call here, and - for {@code alpha_test} - nothing to fix either.</b>
 *
 * <p>The reason is that 26.2 does not ask the block what layer it wants. It asks the
 * <b>pixels</b>: {@code FaceBakery} hands the quad's own UV rectangle to
 * {@code SpriteContents.computeTransparency(u0, v0, u1, v1)}, which scans the sprite's actual
 * alpha inside that rectangle and ORs the answers together, and
 * {@code ChunkSectionLayer.byTransparency} then maps the result — translucent pixels to
 * {@code TRANSLUCENT}, transparent pixels to {@code CUTOUT}, opaque to {@code SOLID}. No map, no
 * declaration, no registry.
 *
 * <p>So a block that declares {@code render_method: "alpha_test"} and ships a texture with
 * transparent texels <b>draws on CUTOUT on 26.2 whether we do anything or not</b>, and the trophy's
 * halo is the case in point. The pack's declaration is not what puts it there — the RGBA pixels
 * are. Fabric API's removal of {@code BlockRenderLayerMap} in this build is therefore not a
 * missing feature on our side either; there is no longer a producer side for it to call.
 *
 * <p><b>WHAT 26.2 GETS WRONG IS THE OPPOSITE DIRECTION, and it is unobserved.</b> A block that
 * declares {@code opaque} and ships a texture that happens to contain alpha gets CUTOUT anyway,
 * because nothing consults the declaration. That is a real divergence and the ledger names it —
 * but it has never been seen on screen, because every report about this halo came from a 1.21.11
 * client. <b>Everything in this paragraph is read out of bytecode, not off a frame.</b>
 *
 * <p>Registered rather than absent so that the shared client entry point compiles against one
 * name on both versions, the same way {@code FabricAttachableLayer} does.
 */
@SpecImpl("SC-150#minecraft:material_instances")
public final class FabricBlockLayers {

    private FabricBlockLayers() {
    }

    public static void register() {
        // Deliberately empty, and for a reason that is not "not finished yet": on 26.2 the layer
        // comes from the sprite's own pixels, so an alpha_test block draws on CUTOUT without us.
        // See above, and SC-150 §5.4.
    }
}