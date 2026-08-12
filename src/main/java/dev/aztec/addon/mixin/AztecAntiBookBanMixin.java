package dev.aztec.addon.mixin;

import dev.aztec.addon.modules.AztecAntiBookBan;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class AztecAntiBookBanMixin {

    @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
    private void onInteractItem(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        AztecAntiBookBan module = Modules.get().get(AztecAntiBookBan.class);
        if (module == null || !module.isBlockOpeningEnabled()) return;

        ItemStack stack = player.getStackInHand(hand);

        if (module.isDangerousBook(stack)) {
            cir.setReturnValue(ActionResult.FAIL);
            module.onBookBlocked();
            ChatUtils.sendPlayerMsg("§c[AntiBookBan] §lBlocked dangerous book! §r§7Opening prevented.");
        }
    }
}
