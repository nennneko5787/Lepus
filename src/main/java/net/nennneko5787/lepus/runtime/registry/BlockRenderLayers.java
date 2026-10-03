package net.nennneko5787.lepus.runtime.registry;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.ir.block.BlockModels;
import net.nennneko5787.lepus.core.registry.BlockSlot;

/**
 * Which pool blocks draw on a layer other than {@code SOLID}, and why. SC-150 §5.4.
 *
 * <p><b>The whole of this feature, minus the one call that installs it.</b> Everything else about a
 * block's transparency is arithmetic that lives in {@code BlockModels} and is tested there; what is
 * left is a set of {@code (Block, layer)} pairs that a client hands to whichever mechanism the
 * loader provides, and the set is worth having as its own tested object because the thing that
 * installs it is the part that cannot be tested headlessly.
 *
 * <p><b>Why a pool block at all.</b> Java's chunk layer is chosen per <i>block</i> —
 * {@code ItemBlockRenderTypes.TYPE_BY_BLOCK} is a {@code Map<Block, ChunkSectionLayer>} that
 * defaults to SOLID — and one Bedrock block owns exactly one {@link BlockSlot} and therefore
 * exactly one registered {@code PoolBlock}. So the granularity matches without anything having to be
 * split, which is the reason the pool is keyed by slot and not by anything about a block's
 * appearance. It is luck rather than design, and it is worth recording that it is luck.
 *
 * <p><b>A block with no binding is not in the map.</b> An unbound slot draws as the empty model and
 * has nothing to be transparent about, and the alternative — putting every pool block on CUTOUT —
 * would cost the atlas and the mesher for nothing.
 */
@SpecImpl("SC-150")
public final class BlockRenderLayers {

    private BlockRenderLayers() {
    }

    /**
     * The pool blocks that need a layer of their own, in pool order.
     *
     * @param bindings the current snapshot, from {@link BoundBlocks}
     * @param pool      the registered pool, to turn a slot into the block that carries it
     * @return the pool block to the method it asked for, and nothing for every other block
     */
    public static Map<Block, BlockModels.RenderMethod> needed(Map<BlockSlot, BoundBlocks.Bound> bindings,
            BlockPool pool) {
        Map<Block, BlockModels.RenderMethod> wanted = new LinkedHashMap<>();
        for (BlockSlot slot : pool.slots()) {
            BoundBlocks.Bound bound = bindings.get(slot);
            if (bound == null || !bound.renderMethod().needsOwnLayer()) {
                continue;
            }
            // The pool's block for this slot, resolved through the pool rather than through the
            // binding: the binding knows the SLOT and only the pool knows the Block, and a caller
            // with a slot in hand should not have to know that.
            pool.block(slot).ifPresent(block -> wanted.put(block, bound.renderMethod()));
        }
        return Map.copyOf(wanted);
    }

    /**
     * How many bound blocks ask for a layer, for a log line or a test assertion.
     *
     * <p>Separate from {@link #needed} so that a caller reporting on a world never has to build
     * the map to count it — and so that the count is available on a dedicated server, where nothing
     * is ever installed.
     */
    public static int transparentCount(Map<BlockSlot, BoundBlocks.Bound> bindings) {
        return (int) bindings.values().stream()
                .filter(bound -> bound.renderMethod().needsOwnLayer())
                .count();
    }
}