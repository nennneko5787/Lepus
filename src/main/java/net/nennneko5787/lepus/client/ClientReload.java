package net.nennneko5787.lepus.client;

import net.minecraft.client.Minecraft;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * The client half of {@code ClientResources}: re-reads the generated pack. SC-180 §8.1.
 *
 * <p>Its own class, tiny, because it is the only thing in the mod that names
 * {@code Minecraft.reloadResourcePacks} — and because keeping it out of the shared runtime is what
 * lets a dedicated server never load it.
 */
@SpecImpl("SC-180")
public final class ClientReload {

    private ClientReload() {
    }

    /**
     * Reloads, on the render thread.
     *
     * <p>The caller is the <b>server</b> thread: binding happens as a world loads, and in single
     * player the integrated server shares this process. A resource reload rebuilds every atlas and
     * every baked model, so starting one from the wrong thread is not a race that shows up later —
     * it is a race in the middle of rendering.
     */
    public static void now() {
        Minecraft client = Minecraft.getInstance();
        // BEFORE the reload, not after. A chunk layer is consulted when a section mesh is BUILT, and
        // a reload rebuilds every model and atlas but not the meshes; installing first means the
        // meshes built by whatever comes next - a fresh world, a reloaded one - see the right
        // layer, and installing after the reload would leave everything already built untouched and
        // looking like the change had no effect. SC-150 section 5.4.
        client.execute(() -> {
            BlockLayerInstaller.install();
            client.reloadResourcePacks();
        });
    }
}
