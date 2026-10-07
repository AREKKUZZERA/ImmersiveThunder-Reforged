package com.netcatgirl.immersivethunder.mixin;

import com.netcatgirl.immersivethunder.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.sonicether.soundphysics.SoundPhysics", remap = false)
public abstract class SoundPhysicsCompatibilityMixin {

    // Convert the estimated 2.4 dB per solid block to Sound Physics occlusion units.
    @Unique
    private static final double THUNDER_OCCLUSION_PER_BLOCK = 2.4D / 2.606D;

    // Sound Physics already accounts for the sound path; limit the extra listener-roof estimate.
    @Unique
    private static final double MAX_ADDITIONAL_THUNDER_OCCLUSION = 3.0D;

    @Inject(
            method = "calculateOcclusion",
            at = @At("RETURN"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private static void immersivethunder$addRoofOcclusion(
            @Coerce Object soundPosition,
            @Coerce Object playerPosition,
            @Coerce Object soundSource,
            @Coerce Object soundEvent,
            CallbackInfoReturnable<Double> callback
    ) {
        if (!immersivethunder$isReplacementThunder(soundEvent)) {
            return;
        }

        double soundPhysicsOcclusion = callback.getReturnValue();
        if (!Double.isFinite(soundPhysicsOcclusion)) {
            return;
        }

        double roofOcclusion = immersivethunder$measureRoofOcclusion();
        if (roofOcclusion > 0.0D) {
            callback.setReturnValue(soundPhysicsOcclusion + roofOcclusion);
        }
    }

    @Unique
    private static boolean immersivethunder$isReplacementThunder(Object soundEvent) {
        if (!(soundEvent instanceof SoundEvent event)) {
            return false;
        }

        Object soundId = BuiltInRegistries.SOUND_EVENT.getKey(event);
        return Constants.THUNDER_CLOSE.equals(soundId)
                || Constants.THUNDER_MEDIUM.equals(soundId)
                || Constants.THUNDER_FAR.equals(soundId);
    }

    @Unique
    private static double immersivethunder$measureRoofOcclusion() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            return 0.0D;
        }

        BlockPos playerPosition = player.blockPosition();
        int surfaceY = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING,
                playerPosition.getX(),
                playerPosition.getZ()
        );
        int firstSampleY = playerPosition.getY() + 2;
        if (surfaceY <= firstSampleY) {
            return 0.0D;
        }

        double additionalOcclusion = 0.0D;
        BlockPos.MutableBlockPos samplePosition = new BlockPos.MutableBlockPos(
                playerPosition.getX(),
                firstSampleY,
                playerPosition.getZ()
        );

        for (int y = firstSampleY; y < surfaceY && additionalOcclusion < MAX_ADDITIONAL_THUNDER_OCCLUSION; y++) {
            BlockPos sample = samplePosition.setY(y);
            if (!level.getBlockState(sample).getCollisionShape(level, sample).isEmpty()) {
                additionalOcclusion = Math.min(
                        additionalOcclusion + THUNDER_OCCLUSION_PER_BLOCK,
                        MAX_ADDITIONAL_THUNDER_OCCLUSION
                );
            }
        }

        return additionalOcclusion;
    }
}
