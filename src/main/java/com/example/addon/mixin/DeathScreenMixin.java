package com.example.addon.mixin;

import com.example.addon.modules.AutoRespawnAz;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(DeathScreen.class)
public class DeathScreenMixin {

    // ✅ El método ahora es 'static' porque interceptamos un constructor (<init>)
    @ModifyArg(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screen/Screen;<init>(Lnet/minecraft/text/Text;)V"
        ),
        index = 0
    )
    private static Text modifyDeathMessage(Text originalTitle) {
        AutoRespawnAz autoRespawn = Modules.get().get(AutoRespawnAz.class);
        if (autoRespawn != null && autoRespawn.isActive()) {
            String customMessage = autoRespawn.deathMessage.get();
            if (customMessage != null && !customMessage.isEmpty()) {
                return Text.of(customMessage);
            }
        }
        return originalTitle;
    }
}
