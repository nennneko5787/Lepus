package net.nennneko5787.lepus.client;

import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.level.block.Block;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * Puts a bound block on the chunk layer its {@code render_method} asked for, on Fabric. SC-150 §5.4.
 *
 * <p><b>Three lines, because Fabric has an API for exactly this.</b> 1.21.11 ships
 * {@code BlockRenderLayerMap.putBlock(Block, ChunkSectionLayer)}, which writes the same
 * {@code TYPE_BY_BLOCK} that the NeoForge mixin redirects around. There is nothing to design here:
 * the decision is made in {@code BlockModels}, the lookup is filled by
 * {@link BlockLayerInstaller}, and this only carries it across. The NeoForge side of this feature
 * is a redirect into vanilla's map and this is a documented call into it - two routes to one
 * answer, and the disagreement between them would show up as one loader's transparent blocks
 * drawing opaque.
 *
 * <p><b>In the shared package on purpose.</b> It reads {@link BlockLayerLookup#layers()}, which is
 * package-private so that nothing outside this package can put blocks on a layer by accident. The
 * alternative - making it public - would export the setter and the getter together, and the
 * setter is the one that can be wrong. Loader-specific classes already live in the shared package
 * across source directories; {@code AttachableRenderTypes} is per-version and does the same.
 *
 * <p><b>Nothing is removed, and that is a fact about the map rather than a choice.</b> The API
 * offers {@code putBlock} and nothing that takes a block off, so an entry outlives both the pack
 * and the world that put it there — the opposite of NeoForge's mixin, which reads a map this mod
 * owns and replaces whole on every install. Nothing draws wrong from the staleness: an unbound
 * slot draws the empty model, which has no faces to cut out, and between worlds there is no world
 * to render. Removing it would mean a second route into vanilla's map, and two routes into one map
 * is the thing the NeoForge side was written to avoid. Recorded in SC-150 §5.4.
 *
 * <p><b>1.21.11 only, and the reason is not ours to choose.</b> Fabric API removed
 * {@code BlockRenderLayerMap} in the build that targets 26.2, because 26.2 takes a block's layer
 * from its sprite's transparency rather than from a per-block map. There is nothing to call on that
 * version. §5.4 records what 26.2 needs instead.
 */
@SpecImpl("SC-150#minecraft:material_instances")
public final class FabricBlockLayers {

    private FabricBlockLayers() {
    }

    /** Hands the loader its push. Called once, from the Fabric client entry point. */
    public static void register() {
        BlockLayerLookup.register(FabricBlockLayers::push);
        // Once at startup as well, so a client that goes straight into a world has the layers
        // before the first mesh is built rather than after the first bind. Empty until then, and
        // empty is the right answer: a client with no world has no bound blocks.
        push();
    }

    private static void push() {
        for (Map.Entry<Block, ChunkSectionLayer> entry : BlockLayerLookup.layers().entrySet()) {
            BlockRenderLayerMap.putBlock(entry.getKey(), entry.getValue());
        }
    }
}