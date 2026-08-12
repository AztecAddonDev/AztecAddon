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
    private int pearlSwapDelay = 0; // ✅ FIX: Delay después de swap

    public AztecPhase() {
        super(AddonTemplate.CATEGORY, "aztec-phase", "Phase through blocks using TP or Pearl.");
    }

    @Override
    public void onActivate() {
        cooldownLeft = 0;
        pearlSwapDelay = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (cooldownLeft > 0) {
            cooldownLeft--;
            return;
        }

        // ✅ FIX: Lógica corregida de crawling
        if (onlyCrawling.get()) {
            if (!mc.player.isCrawling()) return; // Solo phase si está crawling
        } else {
            if (mc.player.isCrawling()) return; // No phase si está crawling (modo normal)
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
            // ✅ FIX: Eliminado símbolo § para evitar "Illegal characters"
            ChatUtils.sendPlayerMsg("[AztecPhase] No pearls in hotbar!");
            return;
        }

        // ✅ FIX: Si ya tenemos la perla en la mano, úsala directamente
        if (mc.player.getInventory().getSelectedSlot() == pearl.slot()) {
            throwPearl();
            return;
        }

        // Si no, hacer swap y esperar un tick
        InvUtils.swap(pearl.slot(), true);
        pearlSwapDelay = 1;
    }

    private void throwPearl() {
        Direction collisionDir = getCollisionDirection();
        float yaw = getYawFromDirection(collisionDir);

        Rotations.rotate(yaw, pitch.get().floatValue(), () -> {
            // ✅ FIX: Verificar que realmente tenemos la perla antes de usarla
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
}
