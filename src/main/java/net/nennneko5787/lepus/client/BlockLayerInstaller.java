package net.nennneko5787.lepus.client;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.level.block.Block;
import net.nennneko5787.lepus.Lepus;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.ir.block.BlockModels;
import net.nennneko5787.lepus.runtime.registry.BlockRenderLayers;
import net.nennneko5787.lepus.runtime.registry.BoundBlocks;

/**
 * Turns the current world's bindings into the layer set a client draws on. SC-150 §5.4.
 *
 * <p><b>Every method here is the {@code SOLID} answer unless a block asked otherwise.</b> That is
 * not a default to avoid work - it is what a block with no {@code render_method} gets, which is
 * nearly every block in every pack, and giving them each an explicit entry would put the entire
 * pool in a map whose only content is "no change".
 *
 * <p><b>Computed, then installed, and never the other way round.</b> The set is derived from
 * {@link BoundBlocks} and the pool on demand rather than kept in step with them, because there is
 * nothing to keep in step: a bind replaces the whole snapshot and the next {@link #install()}
 * reads it. The alternative - updating on every change - is a second thing that can be forgotten.
 *
 * <p><b>Client thread only.</b> Both loaders' mechanisms want to be called while rendering state
 * is not in flight, and one of them walks a vanilla map that the mesher reads.
 */
@SpecImpl("SC-150")
public final class BlockLayerInstaller {

    private BlockLayerInstaller() {
    }

    /**
     * The map a loader installs: our pool blocks only, and only the ones that asked.
     *
     * @see BlockRenderLayers#needed(Map, BlockPool)
     */
    public static Map<Block, ChunkSectionLayer> layers() {
        // Lepus.blockPool() throws before mod init rather than answering null, which is the right
        // shape for a programming error and the wrong shape for a client that simply has no world
        // yet. An empty set is the correct answer there - nothing is bound, so nothing asks.
        Map<Block, ChunkSectionLayer> resolved = new LinkedHashMap<>();
        if (!Lepus.poolRegistered()) {
            return Map.of();
        }
        Map<Block, BlockModels.RenderMethod> wanted =
                BlockRenderLayers.needed(BoundBlocks.all(), Lepus.blockPool());
        wanted.forEach((block, method) -> resolved.put(block, layerFor(method)));
        return Map.copyOf(resolved);
    }

    /**
     * Reads the current bindings and publishes the result.
     *
     * <p>Whole-set replacement, never a merge: a pack that has just been disabled owns slots that
     * are still registered, and leaving their layer behind would draw a block with no content as a
     * cutout one for the rest of the session.
     */
    public static void install() {
        Map<Block, ChunkSectionLayer> wanted = layers();
        BlockLayerLookup.install(wanted);
        // THEN the loader. Filling the map cannot fail; pushing it is the loader's, and a mixin
        // reading the map mid-push would see half of two worlds.
        BlockLayerLookup.push();
        System.out.println("[Lepus] " + wanted.size()
                + " bound block(s) on a chunk layer of their own");
    }

    /** Forgets everything. A client between worlds must not answer with the last world's blocks. */
    public static void forget() {
        BlockLayerLookup.clear();
    }

    /**
     * The one place the five Bedrock methods become three Java layers.
     *
     * <p>{@code alpha_test_single_sided} and {@code double_sided} have no Java counterpart and are
     * folded here rather than at parse time, so the ledger's divergence is one sentence against one
     * switch. {@code SOLID} is returned for both because a caller that asks is asking about a
     * method that has no layer, and vanilla's answer for an unknown block is the right one.
     */
    static ChunkSectionLayer layerFor(BlockModels.RenderMethod method) {
        return switch (method) {
            case ALPHA_TEST, CUTOUT_ONE_SIDED -> ChunkSectionLayer.CUTOUT;
            case BLEND -> ChunkSectionLayer.TRANSLUCENT;
            // DOUBLE_SIDED is folded and OPAQUE is the default. Both are SOLID, and saying so
            // twice rather than falling through is what lets a reader see that the fold is
            // deliberate and not an oversight.
            case OPAQUE, DOUBLE_SIDED -> ChunkSectionLayer.SOLID;
        };
    }
}