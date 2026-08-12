package dev.aztec.addon.mixin;

import dev.aztec.addon.modules.AzCustomFov;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "getFov", at = @At("HEAD"), cancellable = true)
    private void onGetFov(CallbackInfoReturnable<Float> cir) {
        AzCustomFov fovModule = Modules.get().get(AzCustomFov.class);
        if (fovModule != null && fovModule.isActive()) {
            cir.setReturnValue((float) fovModule.getCurrentFov());
        }
    }
}
