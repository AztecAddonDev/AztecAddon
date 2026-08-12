package com.example.addon.mixin;

import com.example.addon.modules.AutoRespawnAz;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(DeathScreen.class)
public class DeathScreenMixin {
    @ModifyArgs(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawCenteredTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V"))
    private void modifyDeathMessage(Args args) {
        AutoRespawnAz autoRespawn = Modules.get().get(AutoRespawnAz.class);
        if (autoRespawn != null && autoRespawn.isActive()) {
            String customMessage = autoRespawn.deathMessage.get();
            if (customMessage != null && !customMessage.isEmpty()) {
                args.set(1, Text.of(customMessage));
            }
        }
    }
}
