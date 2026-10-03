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
 * <p><b>There is no {@code clear()}, and that is a decision rather than an omission.</b> One was
 * written; nothing called it; and a docstring promising a reset that no code performs is the worse
 * of the two mistakes. The next {@link BlockLayerInstaller#install()} replaces the whole set
 * anyway, and the only window in which a stale entry could be consulted is the one between worlds,
 * where there is no world to render. Fabric's own map is the opposite case and cannot be emptied at
 * all — see {@code FabricBlockLayers} for why that is harmless rather than a leak to fix.
 *
 * <p>Client-only by construction: {@code ChunkSectionLayer} is a client class, and this type is
 * named by no shared code path that a dedicated server would load.
 */
@SpecImpl("SC-150")
public final class BlockLayerLookup {

    /**
     * The layer a client has not told us yet, and the one it has.
     *
     * <p>Held here rather than in the loader's own class so that the mixin on NeoForge and the
     * registry call on Fabric read the same map, and so that {@link BlockLayerInstaller} can fill
     * it without knowing which of the two is running. The alternative - each loader keeping its own
     * set - is two copies of one decision, and the disagreement between them would be invisible
     * until a pack with a transparent block was opened on one loader and not the other.
     */
    private static volatile Map<Block, net.minecraft.client.renderer.chunk.ChunkSectionLayer> layers =
            Map.of();

    /** The loader's own push, installed once at client init. Empty when there is no client. */
    private static volatile Runnable pusher = () -> {
    };

    private BlockLayerLookup() {
    }

    /**
     * Hands the loader its own way of installing the set. Called once, from the loader's client
     * entry point, and never on a hot path.
     *
     * <p>A {@link Runnable} rather than an interface because the two implementations share no
     * signature to speak of: Fabric writes a map, NeoForge's mixin reads one. An interface here
     * would have exactly one method with a name that fits neither of them.
     */
    public static void register(Runnable push) {
        pusher = push == null ? () -> {
        } : push;
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

    /**
     /**
     * Hands the set to the loader.
     *
     * <p>Separate from {@link #install} because the two halves fail differently. Filling the map
     * is ours and cannot fail; pushing it is the loader's and must be called AFTER, or NeoForge's
     * mixin would answer from the previous world's blocks while the new ones are being installed.
     */
    static void push() {
        pusher.run();
    }

    /** The installed set. The loader's push reads this; nothing else should. */
    static Map<Block, net.minecraft.client.renderer.chunk.ChunkSectionLayer> layers() {
        return layers;
    }
}