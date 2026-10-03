package net.nennneko5787.lepus.neoforge.mixin;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.nennneko5787.lepus.client.BlockLayerLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Puts a bound block on the chunk layer its {@code render_method} asked for. SC-150 §5.4.
 *
 * <p><b>NeoForge has no API for this, and the search is recorded so it is not repeated.</b>
 * {@code IBlockExtension} — the extension every block gets on this loader — has no render hook at
 * all: its only visual methods are {@code getLightEmission} and
 * {@code hasDynamicLightEmission}. {@code RenderTypeHelper} looks like one and is not: it turns a
 * {@link ChunkSectionLayer} into the {@code RenderType} a given context wants, which is the
 * CONSUMER side of the question. Fabric has {@code BlockRenderLayerMap.putBlock}, which is the
 * producer side, and this file is what the producer side costs without it.
 *
 * <p><b>The target is one static method and it is short.</b> From 1.21.11's bytecode:
 * {@code ItemBlockRenderTypes.getChunkRenderType(BlockState)} reads
 * {@code TYPE_BY_BLOCK.get(state.getBlock())} and falls back to {@code SOLID}, where
 * {@code TYPE_BY_BLOCK} is a {@code Map<Block, ChunkSectionLayer>}. One HEAD injection is therefore
 * enough, and an accessor for the private field is not needed — injecting returns before the map is
 * ever touched, so nothing can observe the difference.
 *
 * <p><b>It injects rather than writing the map, and that is deliberate.</b> Fabric writes the same
 * map from outside; writing it here too would mean a Fabric client and a NeoForge client reach
 * {@code TYPE_BY_BLOCK} by different routes, and the two would then have to be kept in agreement.
 * One of them is a documented public call and one is a redirect, and only one of them should be
 * load-bearing for a mismatch to show up in.
 *
 * <p><b>Version-bound, because the class is.</b> 1.21.11 has
 * {@code ItemBlockRenderTypes}; 26.2 does not — its layer comes from the sprite's transparency
 * instead ({@code BakedQuad.MaterialInfo.of} calls
 * {@code ChunkSectionLayer.byTransparency}), so there is no map to redirect on that version at all.
 * Hence {@code src/1.21.11-neoforge} and not {@code src/neoforge}: see the intersection directory's
 * comment in {@code build.neoforge.gradle.kts}. §5.4 records the 26.2 question as open.
 */
@Mixin(ItemBlockRenderTypes.class)
public abstract class ItemBlockRenderTypesMixin {

    @Inject(method = "getChunkRenderType", at = @At("HEAD"), cancellable = true)
    private void lepus$honourDeclaredRenderMethod(BlockState state,
            CallbackInfoReturnable<ChunkSectionLayer> callback) {
        BlockLayerLookup.of(state.getBlock()).ifPresent(callback::setReturnValue);
    }
}