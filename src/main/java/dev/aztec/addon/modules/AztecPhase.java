package dev.aztec.addon.modules;

import dev.aztec.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

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
        .description("How to switch to the pearl. Silent = packet spoof (no visual change), Normal = visible swap.")
        .defaultValue(SwitchMode.Silent)
        .visible(() -> mode.get() == PhaseMode.Pearl)
        .build()
    );

    private final Setting<Boolean> swapBack = sgPearl.add(new BoolSetting.Builder()
        .name("swap-back")
        .description("Swap back to original slot after throwing pearl. (Only for Normal mode)")
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
        BlockPos playerPos = mc.player.getBlockPos();
        Direction bestDir = getBestEscapeDirection();

        BlockPos bestTarget = findPhaseTarget(playerPos, bestDir);

        if (bestTarget == null) {
            for (Direction dir : Direction.values()) {
                if (dir.getAxis().isVertical()) continue;
                if (dir == bestDir) continue;

                BlockPos checkPos = playerPos.offset(dir);
                if (!mc.world.getBlockState(checkPos).isAir()) {
                    bestTarget = findPhaseTarget(playerPos, dir);
                    if (bestTarget != null) break;
                }
            }
        }

        if (bestTarget == null) {
            double x = mc.player.getX() + bestDir.getOffsetX() * tpDistance.get();
            double z = mc.player.getZ() + bestDir.getOffsetZ() * tpDistance.get();
            mc.player.setPos(x, mc.player.getY(), z);
            return;
        }

        double targetX = bestTarget.getX() + 0.5;
        double targetZ = bestTarget.getZ() + 0.5;
        double targetY = bestTarget.getY();

        mc.player.setPos(targetX, targetY, targetZ);
    }

    private Direction getBestEscapeDirection() {
        List<Direction> collisionDirs = getAllCollisionDirections();

        if (collisionDirs.isEmpty()) {
            return mc.player.getHorizontalFacing();
        }

        if (collisionDirs.size() == 1) {
            return collisionDirs.get(0);
        }

        Vec3d inputVector = getPlayerInputVector();

        if (inputVector.lengthSquared() > 0.001) {
            return getDirectionFromVector(inputVector);
        }

        return collisionDirs.get(0);
    }

    private List<Direction> getAllCollisionDirections() {
        List<Direction> collisions = new ArrayList<>();
        BlockPos playerPos = mc.player.getBlockPos();

        for (Direction dir : Direction.values()) {
            if (dir.getAxis().isVertical()) continue;

            BlockPos checkPos = playerPos.offset(dir);
            if (!mc.world.getBlockState(checkPos).isAir()) {
                collisions.add(dir);
            }
        }

        return collisions;
    }

    private Vec3d getPlayerInputVector() {
        float forward = 0;
        float sideways = 0;

        if (mc.options.forwardKey.isPressed()) forward += 1;
        if (mc.options.backKey.isPressed()) forward -= 1;
        if (mc.options.leftKey.isPressed()) sideways += 1;
        if (mc.options.rightKey.isPressed()) sideways -= 1;

        if (forward == 0 && sideways == 0) {
            return Vec3d.ZERO;
        }

        float yaw = mc.player.getYaw();
        float radYaw = (float) Math.toRadians(yaw);

        double x = -Math.sin(radYaw) * forward - Math.cos(radYaw) * sideways;
        double z = Math.cos(radYaw) * forward - Math.sin(radYaw) * sideways;

        return new Vec3d(x, 0, z).normalize();
    }

    private Direction getDirectionFromVector(Vec3d vector) {
        if (Math.abs(vector.x) > Math.abs(vector.z)) {
            return vector.x > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return vector.z > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    private BlockPos findPhaseTarget(BlockPos playerPos, Direction direction) {
        int distance = tpDistance.get();

        for (int i = 1; i <= distance; i++) {
            BlockPos checkPos = playerPos.offset(direction, i);

            if (isSpaceAvailable(checkPos)) {
                return checkPos;
            }

            if (!mc.world.getBlockState(checkPos).isAir() &&
                isSpaceAvailable(checkPos.up())) {
                return checkPos.up();
            }
        }

        for (int i = 1; i <= distance; i++) {
            for (Direction offset : getPerpendicularDirections(direction)) {
                BlockPos diagonalPos = playerPos.offset(direction, i).offset(offset);

                if (isSpaceAvailable(diagonalPos)) {
                    return diagonalPos;
                }

                if (!mc.world.getBlockState(diagonalPos).isAir() &&
                    isSpaceAvailable(diagonalPos.up())) {
                    return diagonalPos.up();
                }
            }
        }

        return null;
    }

    private boolean isSpaceAvailable(BlockPos pos) {
        if (!mc.world.getBlockState(pos).isAir()) return false;
        if (!mc.world.getBlockState(pos.up()).isAir()) return false;

        BlockPos below = pos.down();
        return !mc.world.getBlockState(below).isAir();
    }

    private Direction[] getPerpendicularDirections(Direction direction) {
        return switch (direction) {
            case NORTH, SOUTH -> new Direction[]{Direction.EAST, Direction.WEST};
            case EAST, WEST -> new Direction[]{Direction.NORTH, Direction.SOUTH};
            default -> new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        };
    }

    private void doPearl() {
        var pearl = InvUtils.findInHotbar(Items.ENDER_PEARL);
        if (!pearl.found()) {
            ChatUtils.warningPrefix("AztecAddon", "No pearls in hotbar!");
            return;
        }

        int pearlSlot = pearl.slot();
        int currentSlot = mc.player.getInventory().getSelectedSlot();

        if (switchMode.get() == SwitchMode.Silent) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(pearlSlot));
            throwPearlSilent(currentSlot);
        } else {
            if (currentSlot != pearlSlot) {
                InvUtils.swap(pearlSlot, true);
            }
            if (swapBack.get()) {
                originalSlot = currentSlot;
            }
            throwPearlNormal();
        }
    }

    private void throwPearlSilent(int clientSlot) {
        Direction escapeDir = getBestEscapeDirection();
        float yaw = getYawFromDirection(escapeDir);

        Rotations.rotate(yaw, pitch.get().floatValue(), () -> {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(clientSlot));
            toggle();
        });
    }

    private void throwPearlNormal() {
        Direction escapeDir = getBestEscapeDirection();
        float yaw = getYawFromDirection(escapeDir);

        Rotations.rotate(yaw, pitch.get().floatValue(), () -> {
            if (mc.player.getMainHandStack().getItem() == Items.ENDER_PEARL) {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);

                if (swapBack.get() && originalSlot != -1) {
                    InvUtils.swap(originalSlot, true);
                    originalSlot = -1;
                }

                toggle();
            } else {
                ChatUtils.warningPrefix("AztecAddon", "Failed to throw pearl!");
            }
        });
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
