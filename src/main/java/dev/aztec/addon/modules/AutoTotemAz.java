package dev.aztec.addon.modules;

import dev.aztec.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

public class AutoTotemAz extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgHealth = this.settings.createGroup("Health Settings");
    private final SettingGroup sgTiming = this.settings.createGroup("Timing");


    private final Setting<Boolean> smartSwap = sgGeneral.add(new BoolSetting.Builder()
        .name("smart-swap")
        .description("Only swap totem when necessary (better for CPVP).")
        .defaultValue(true)
        .build()
    );


    private final Setting<Boolean> pauseInInventory = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-in-inventory")
        .description("Pause auto totem while in inventory screens.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> pauseInContainers = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-in-containers")
        .description("Pause auto totem while in container screens (chests, etc.).")
        .defaultValue(true)
        .build()
    );


    private final Setting<Integer> healthThreshold = sgHealth.add(new IntSetting.Builder()
        .name("health-threshold")
        .description("Health percentage to swap totem at (0-100).")
        .defaultValue(50)
        .min(0)
        .max(100)
        .sliderMax(100)
        .build()
    );


    private final Setting<Integer> swapDelay = sgTiming.add(new IntSetting.Builder()
        .name("swap-delay")
        .description("Ticks to wait before swapping totem.")
        .defaultValue(0)
        .min(0)
        .max(20)
        .sliderMax(5)
        .build()
    );

    private final Setting<Integer> checkInterval = sgTiming.add(new IntSetting.Builder()
        .name("check-interval")
        .description("Ticks between totem checks.")
        .defaultValue(1)
        .min(1)
        .max(20)
        .sliderMax(5)
        .build()
    );

    private final Setting<Integer> postSwapCooldown = sgTiming.add(new IntSetting.Builder()
        .name("post-swap-cooldown")
        .description("Ticks to wait after swapping to prevent immediate re-swap.")
        .defaultValue(5)
        .min(0)
        .max(20)
        .sliderMax(10)
        .build()
    );


    private int tickCounter = 0;
    private int swapCooldown = 0;
    private boolean pendingSwap = false;
    private int postCooldown = 0;

    public AutoTotemAz() {
        super(AddonTemplate.CATEGORY, "auto-totem-az", "Advanced auto totem with smart logic for CPVP.");
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        swapCooldown = 0;
        pendingSwap = false;
        postCooldown = 0;
    }

    @Override
    public void onDeactivate() {
        tickCounter = 0;
        swapCooldown = 0;
        pendingSwap = false;
        postCooldown = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;


        if (shouldPause()) return;

        tickCounter++;
        if (tickCounter < checkInterval.get()) return;
        tickCounter = 0;


        if (swapCooldown > 0) swapCooldown--;
        if (postCooldown > 0) postCooldown--;


        if (pendingSwap && swapCooldown <= 0) {
            performSwap();
            pendingSwap = false;
            return;
        }


        if (postCooldown > 0) return;


        if (shouldSwapTotem()) {
            if (swapDelay.get() > 0) {
                pendingSwap = true;
                swapCooldown = swapDelay.get();
            } else {
                performSwap();
            }
        }
    }

       @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof EntityStatusS2CPacket packet) {

            if (packet.getStatus() == 35 && packet.getEntity(mc.world) == mc.player) {

                performSwap();
            }
        }
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {

        if (event.screen != null) {
            pendingSwap = false;
        }
    }

    private boolean shouldPause() {
        if (pauseInInventory.get() && mc.currentScreen != null && !(mc.currentScreen instanceof GenericContainerScreen)) {
            return true;
        }
        if (pauseInContainers.get() && mc.currentScreen instanceof GenericContainerScreen) {
            return true;
        }
        return false;
    }

    private boolean shouldSwapTotem() {
        if (mc.player == null) return false;


        ItemStack offhandItem = mc.player.getEquippedStack(EquipmentSlot.OFFHAND);
        if (offhandItem.getItem() == Items.TOTEM_OF_UNDYING) {
            return false;
        }


        if (smartSwap.get()) {
            int threshold = healthThreshold.get();
            if (threshold > 0 && threshold < 100) {
                float maxHealth = mc.player.getMaxHealth();
                float currentHealth = mc.player.getHealth();
                float healthPercentage = (currentHealth / maxHealth) * 100;

                if (healthPercentage > threshold) {
                    return false;
                }
            }
        }


        FindItemResult totem = InvUtils.findInHotbar(Items.TOTEM_OF_UNDYING);
        if (!totem.found()) {

            totem = InvUtils.find(Items.TOTEM_OF_UNDYING);
        }

        return totem.found();
    }

    private void performSwap() {
        if (mc.player == null || mc.world == null) return;


        FindItemResult totem = InvUtils.findInHotbar(Items.TOTEM_OF_UNDYING);

        if (!totem.found()) {

            totem = InvUtils.find(Items.TOTEM_OF_UNDYING);
        }

        if (!totem.found()) return;



        InvUtils.move().from(totem.slot()).toOffhand();


        postCooldown = postSwapCooldown.get();
    }
}
