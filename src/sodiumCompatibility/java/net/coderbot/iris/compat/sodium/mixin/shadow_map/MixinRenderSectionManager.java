package net.coderbot.iris.compat.sodium.mixin.shadow_map;

import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import me.jellysquid.mods.sodium.interop.vanilla.math.frustum.Frustum;
import me.jellysquid.mods.sodium.opengl.device.RenderDevice;
import me.jellysquid.mods.sodium.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.render.chunk.RenderSectionManager;
import me.jellysquid.mods.sodium.render.chunk.draw.ChunkRenderList;
import me.jellysquid.mods.sodium.render.chunk.passes.ChunkRenderPassManager;
import me.jellysquid.mods.sodium.render.chunk.state.ChunkRenderBounds;
import net.coderbot.iris.pipeline.ShadowRenderer;
import net.coderbot.iris.shadows.ShadowRenderingState;
import net.coderbot.iris.compat.sodium.impl.shadow_map.SwappableRenderSectionManager;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

/**
 * Modifies {@link RenderSectionManager} to support maintaining a separate visibility list for the shadow camera, as well
 * as disabling chunk rebuilds when computing visibility for the shadow camera.
 */
@Mixin(RenderSectionManager.class)
public class MixinRenderSectionManager implements SwappableRenderSectionManager {
    @Shadow(remap = false)
    @Final
    @Mutable
    private ChunkRenderList chunkRenderList;

    @Shadow(remap = false)
    @Final
    @Mutable
    private ObjectList<RenderSection> tickableChunks;

    @Shadow(remap = false)
    @Final
    @Mutable
    private ObjectList<BlockEntity> visibleBlockEntities;

	@Shadow(remap = false)
	private boolean needsUpdate;

	@Shadow(remap = false)
	@Final
	private boolean isBlockFaceCullingEnabled;

    @Unique
    private ChunkRenderList chunkRenderListSwap;

    @Unique
    private ObjectList<RenderSection> tickableChunksSwap;

    @Unique
    private ObjectList<BlockEntity> visibleBlockEntitiesSwap;

    @Unique
	private boolean needsUpdateSwap;

    @Unique
    private static final ObjectArrayFIFOQueue<?> EMPTY_QUEUE = new ObjectArrayFIFOQueue<>();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void iris$onInit(RenderDevice device, SodiumWorldRenderer worldRenderer,
							 ChunkRenderPassManager renderPassManager, ClientLevel world, int renderDistance, CallbackInfo ci) {
        this.chunkRenderListSwap = new ChunkRenderList();
        this.tickableChunksSwap = new ObjectArrayList<>();
        this.visibleBlockEntitiesSwap = new ObjectArrayList<>();
        this.needsUpdateSwap = true;
    }

    @Override
    public void iris$swapVisibilityState() {
        ChunkRenderList chunkRenderListTmp = chunkRenderList;
        chunkRenderList = chunkRenderListSwap;
        chunkRenderListSwap = chunkRenderListTmp;

        ObjectList<RenderSection> tickableChunksTmp = tickableChunks;
        tickableChunks = tickableChunksSwap;
        tickableChunksSwap = tickableChunksTmp;

        ObjectList<BlockEntity> visibleBlockEntitiesTmp = visibleBlockEntities;
        visibleBlockEntities = visibleBlockEntitiesSwap;
        visibleBlockEntitiesSwap = visibleBlockEntitiesTmp;

        boolean needsUpdateTmp = needsUpdate;
        needsUpdate = needsUpdateSwap;
        needsUpdateSwap = needsUpdateTmp;
    }

    @Inject(method = "update", at = @At("RETURN"))
	private void iris$captureVisibleBlockEntities(Camera camera, Frustum frustum, int frame, boolean spectator, CallbackInfo ci) {
		if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
			ShadowRenderer.visibleBlockEntities = visibleBlockEntities;
		}
	}

	@Inject(method = "schedulePendingUpdates", at = @At("HEAD"), cancellable = true, remap = false)
	private void iris$noRebuildEnqueueingInShadowPass(RenderSection section, CallbackInfo ci) {
		if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
			ci.cancel();
		}
	}

	@Redirect(method = "resetLists", remap = false,
			at = @At(value = "INVOKE", target = "java/util/Collection.iterator ()Ljava/util/Iterator;"))
	private Iterator<?> iris$noQueueClearingInShadowPass(Collection<?> collection) {
		if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
			return Collections.emptyIterator();
		} else {
			return collection.iterator();
		}
	}

	@Redirect(method = "calculateVisibilityFlags",
			at = @At(value = "FIELD",
					target = "me/jellysquid/mods/sodium/render/chunk/RenderSectionManager.isBlockFaceCullingEnabled : Z"))
	private boolean iris$disableBlockFaceCullingInShadowPass(RenderSectionManager manager) {
		return isBlockFaceCullingEnabled && !ShadowRenderingState.areShadowsCurrentlyBeingRendered();
	}

	// TODO: check needsUpdate and needsUpdateSwap patches?
}