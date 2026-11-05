package fr.orakun.redo.legacy.mixin;

import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

/** The terrain still to build, to know when a rebuilt replay world is drawn in full. */
@Mixin(RenderGlobal.class)
public interface RenderGlobalAccessor {
    @Accessor("chunksToUpdate")
    Set<RenderChunk> redo$chunksToUpdate();

    @Accessor("renderDispatcher")
    ChunkRenderDispatcher redo$renderDispatcher();
}
