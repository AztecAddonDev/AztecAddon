package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class AztecPhase extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTP = settings.createGroup("TP Mode");
    private final SettingGroup sgPearl = settings.createGroup("Pearl Mode");

    private final Setting<PhaseMode> mode = sgGeneral.add(new EnumSetting.Builder<PhaseMode>()
        .name("mode")
        .description("Phase mode.")
        .defaultValue(PhaseMode.TP)
        .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown")
        .description("Cooldown between phases in ticks.")
        .defaultValue(10)
        .min(0)
        .max(100)
        .sliderMax(40)
        .build()
    );

    private final Setting<Boolean> onlyCrawling = sgGeneral.add(new BoolSetting.Builder()
        .name("only-while-crawling")
        .description("Only phase when crawling (1x1x1 gaps).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> tpDistance = sgTP.add(new IntSetting.Builder()
        .name("tp-distance")
        .description("Distance to teleport in blocks.")
        .defaultValue(2)
        .min(1)
        .max(10)
        .sliderMax(5)
        .visible(() -> mode.get() == PhaseMode.TP)
        .build()
    );

    private final Setting<SwitchMode> switchMode = sgPearl.add(new EnumSetting.Builder<SwitchMode>()
        .name("switch-mode")
        .description("How to switch to the pearl. Silent = no animation, Normal = visible swap.")
        .defaultValue(SwitchMode.Silent)
        .visible(() -> mode.get() == PhaseMode.Pearl)
        .build()
    );

    private final Setting<Boolean> swapBack = sgPearl.add(new BoolSetting.Builder()
        .name("swap-back")
        .description("Swap back to original slot after throwing pearl.")
        .defaultValue(true)
        .visible(() -> mode.get() == PhaseMode.Pearl && switchMode.get() == SwitchMode.Normal)
        .build()
    );

    private final Setting<Double> pitch = sgPearl.add(new DoubleSetting.Builder()
        .name("pitch")
        .description("Pitch to throw the pearl. Negative = down, Positive = up.")
        .defaultValue(0.0)
        .min(-90.0)
        .max(90.0)
        .sliderRange(-90.0, 90.0)
        .visible(() -> mode.get() == PhaseMode.Pearl)
        .build()
    );

    private int cooldownLeft;
    private int originalSlot = -1;

    public AztecPhase() {
        super(AddonTemplate.CATEGORY, "aztec-phase", "Phase through blocks using TP or Pearl.");
    }

    @Override
    public void onActivate() {
        cooldownLeft = 0;
        originalSlot = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (cooldownLeft > 0) {
            cooldownLeft--;
            return;
        }

        if (onlyCrawling.get()) {
            if (!mc.player.isCrawling()) return;
        } else {
            if (mc.player.isCrawling()) return;
        }

        if (!mc.player.horizontalCollision) return;

        switch (mode.get()) {
            case TP -> doTP();
            case Pearl -> doPearl();
        }

        cooldownLeft = cooldown.get();
    }

    private void doTP() {
        Direction dir = mc.player.getHorizontalFacing();
        double x = mc.player.getX() + dir.getOffsetX() * tpDistance.get();
        double z = mc.player.getZ() + dir.getOffsetZ() * tpDistance.get();

        BlockPos destPos = BlockPos.ofFloored(x, mc.player.getY(), z);
        if (!mc.world.getBlockState(destPos).isAir()) {
            destPos = destPos.up();
            if (!mc.world.getBlockState(destPos).isAir()) {
                return;
            }
            mc.player.setPos(x, mc.player.getY() + 1, z);
            return;
        }

        mc.player.setPos(x, mc.player.getY(), z);
    }

    private void doPearl() {
        var pearl = InvUtils.findInHotbar(Items.ENDER_PEARL);
        if (!pearl.found()) {
            ChatUtils.sendPlayerMsg("[AztecPhase] No pearls in hotbar!");
            return;
        }

        if (swapBack.get() && switchMode.get() == SwitchMode.Normal) {
            originalSlot = mc.player.getInventory().getSelectedSlot();
        }

        if (mc.player.getInventory().getSelectedSlot() == pearl.slot()) {
            throwPearl();
            return;
        }

        if (switchMode.get() == SwitchMode.Silent) {
            InvUtils.swap(pearl.slot(), false);
            throwPearl();
        } else {
            InvUtils.swap(pearl.slot(), true);
            mc.execute(() -> {
                throwPearl();
                if (swapBack.get() && originalSlot != -1) {
                    InvUtils.swap(originalSlot, true);
                    originalSlot = -1;
                }
            });
        }
    }

    private void throwPearl() {
        Direction collisionDir = getCollisionDirection();
        float yaw = getYawFromDirection(collisionDir);

        Rotations.rotate(yaw, pitch.get().floatValue(), () -> {
            if (mc.player.getMainHandStack().getItem() == Items.ENDER_PEARL) {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                toggle();
            } else {
                ChatUtils.sendPlayerMsg("[AztecPhase] Failed to throw pearl!");
            }
        });
    }

    private Direction getCollisionDirection() {
        BlockPos playerPos = mc.player.getBlockPos();

        for (Direction dir : Direction.values()) {
            if (dir.getAxis().isVertical()) continue;

            BlockPos checkPos = playerPos.offset(dir);
            if (!mc.world.getBlockState(checkPos).isAir()) {
                return dir;
            }
        }

        return mc.player.getHorizontalFacing();
    }

    private float getYawFromDirection(Direction dir) {
        return switch (dir) {
            case NORTH -> 180f;
            case SOUTH -> 0f;
            case WEST -> 90f;
            case EAST -> -90f;
            default -> mc.player.getYaw();
        };
    }

    public enum PhaseMode {
        TP,
        Pearl
    }

    public enum SwitchMode {
        Silent,
        Normal
    }
}
