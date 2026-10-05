package com.netcatgirl.immersivethunder.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents Sound Physics Remastered from completely absorbing replacement thunder.
 *
 * <p>Sound Physics normally disables environmental processing for vanilla thunder. We still
 * want its occlusion and reverb for our replacement sounds, but its direct diagonal ray to a
 * distant bolt greatly overestimates the material above a shallow underground listener. Thunder
 * instead uses the solid vertical overburden and measured low-frequency soil attenuation.</p>
 */
@Pseudo
@Mixin(targets = "com.sonicether.soundphysics.SoundPhysics", remap = false)
public abstract class SoundPhysicsCompatibilityMixin {

    // Oelze et al. measured soil at 0.12-0.96 dB/(cm*kHz) (doi:10.2136/sssaj2002.7880).
    // Extrapolated to thunder's approximately 200 Hz peak (doi:10.1029/JZ072i024p06149), the
    // conservative low end is about 2.4 dB per metre. Sound Physics maps one occlusion unit to
    // approximately 2.606 dB of direct gain reduction with its default settings.
    @Unique
    private static final double THUNDER_OCCLUSION_PER_SOLID_BLOCK = 2.4D / 2.606D;

    @Unique
    private static final int MAX_MEASURED_OVERBURDEN = 24;

    @Inject(
            method = "calculateOcclusion",
            at = @At("RETURN"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private static void immersivethunder$adjustThunderOcclusion(
            @Coerce Object soundPosition,
            @Coerce Object playerPosition,
            @Coerce Object soundSource,
            @Coerce Object soundEvent,
            CallbackInfoReturnable<Double> callback
    ) {
        if (immersivethunder$isReplacementThunder(soundEvent)) {
            callback.setReturnValue(immersivethunder$measureOverburden() * THUNDER_OCCLUSION_PER_SOLID_BLOCK);
        }
    }

    @Unique
    private static boolean immersivethunder$isReplacementThunder(Object soundEvent) {
        String id = soundEvent.toString();
        return id.equals("immersivethunder:thunder_close")
                || id.equals("immersivethunder:thunder_medium")
                || id.equals("immersivethunder:thunder_far");
    }

    @Unique
    private static int immersivethunder$measureOverburden() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            return 0;
        }

        BlockPos playerPosition = player.blockPosition();
        int surfaceY = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING,
                playerPosition.getX(),
                playerPosition.getZ()
        );
        int solidBlocks = 0;
        BlockPos.MutableBlockPos samplePosition = new BlockPos.MutableBlockPos(
                playerPosition.getX(),
                playerPosition.getY() + 2,
                playerPosition.getZ()
        );

        // Start above the player's head so the floor and blocks beside the listener do not count.
        for (int y = playerPosition.getY() + 2; y < surfaceY && solidBlocks < MAX_MEASURED_OVERBURDEN; y++) {
            if (!level.getBlockState(samplePosition.setY(y)).isAir()) {
                solidBlocks++;
            }
        }
        return solidBlocks;
    }
}
