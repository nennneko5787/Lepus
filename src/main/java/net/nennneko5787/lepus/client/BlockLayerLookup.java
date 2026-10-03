package net.nennneko5787.lepus.client;

import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * Which chunk layer each of our pool blocks draws on, once a client has been told. SC-150 §5.4.
 *
 * <p><b>A holder, and nothing else.</b> The answer — which blocks want a layer — is arithmetic and
 * lives in {@code BlockRenderLayers}; the thing that installs it is the part that differs per
 * loader, because Fabric has a public API and NeoForge has none. So the answer is computed once in
 * shared code, handed here, and each loader pushes it into whatever mechanism it has. That is the
 * only way to keep one copy of the decision when there are two ways to install it.
 *
 * <p><b>Empty until a client installs something, and that is the correct default.</b> Vanilla's own
 * answer for a block it has never heard of is {@code SOLID}, and an empty map here reads exactly
 * like that rather than like a failure — a client that has not bound a world yet draws vanilla's
 * world, which is what it would have drawn anyway.
 *
 * <p><b>Replaced whole, never mutated in place.</b> A world ends and another begins, and the blocks
 * of the first one keep their slots in a map that is still reachable from a mesh built for them;
 * replacing the reference means the old set stops being reachable the moment the new one is
 * installed, rather than on a reload someone has to remember to trigger.
 *
 * <p>Client-only by construction: {@code ChunkSectionLayer} is a client class, and this type is
 * named by no shared code path that a dedicated server would load.
 */
@SpecImpl("SC-150")
public final class BlockLayerLookup {

    /** What a client has not told us yet. Read as {@code SOLID} by everything that asks. */
    private static volatile Map<Block, net.minecraft.client.renderer.chunk.ChunkSectionLayer> layers =
            Map.of();

    private BlockLayerLookup() {
    }

    /** The layer for a block, or empty for vanilla's answer of SOLID. */
    public static java.util.Optional<net.minecraft.client.renderer.chunk.ChunkSectionLayer> of(
            Block block) {
        return block == null ? java.util.Optional.empty() : java.util.Optional.ofNullable(layers.get(block));
    }

    /** Installs a whole set, replacing whatever was there. Called from the client thread. */
    public static void install(
            Map<Block, net.minecraft.client.renderer.chunk.ChunkSectionLayer> wanted) {
        layers = Map.copyOf(wanted);
    }

    /** Forgets everything, so a client between worlds cannot answer with the last world's blocks. */
    public static void clear() {
        layers = Map.of();
    }

    /** How many blocks are currently on a layer of their own. For a log line. */
    public static int size() {
        return layers.size();
    }
}