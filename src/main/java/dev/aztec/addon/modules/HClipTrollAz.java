package dev.aztec.addon.modules;

import dev.aztec.addon.AddonTemplate;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.BlockState;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

public class HClipTrollAz extends Module {

    public enum ClipDirection {
        LEFT("Izquierda"),
        RIGHT("Derecha"),
        RANDOM("Random");

        private final String displayName;

        ClipDirection(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("Rango máximo horizontal para buscar agujeros.")
        .defaultValue(8)
        .min(3)
        .sliderMax(32)
        .build()
    );

    private final Setting<Integer> minRange = sgGeneral.add(new IntSetting.Builder()
        .name("min-range")
        .description("Distancia mínima para buscar agujeros (para trollear CA).")
        .defaultValue(5)
        .min(1)
        .sliderMax(16)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks que permanece en el agujero antes del TPBack.")
        .defaultValue(5)
        .min(1)
        .sliderMax(40)
        .build()
    );

    private final Setting<ClipDirection> direction = sgGeneral.add(new EnumSetting.Builder<ClipDirection>()
        .name("direction")
        .description("Dirección del clip.")
        .defaultValue(ClipDirection.RANDOM)
        .build()
    );

    private final Setting<Boolean> onlyOnGround = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-ground")
        .description("Solo funciona si estás en el suelo.")
        .defaultValue(true)
        .build()
    );

    private final Random random = new Random();
    private BlockPos originalPos = null;
    private int tickCounter = 0;
    private boolean isTPed = false;

    public HClipTrollAz() {
        super(AddonTemplate.CATEGORY, "hclip-troll", "TP a agujeros cercanos y TPBack para trollear CA.");
    }

    @Override
    public void onActivate() {
        originalPos = null;
        tickCounter = 0;
        isTPed = false;
    }

    @Override
    public void onDeactivate() {
        if (isTPed && originalPos != null) {
            teleportBack();
        }
        originalPos = null;
        tickCounter = 0;
        isTPed = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) return;

        if (onlyOnGround.get() && !mc.player.isOnGround()) return;

        tickCounter++;

        if (!isTPed) {
            if (tickCounter >= delay.get()) {
                tickCounter = 0;

                BlockPos hole = findHole();
                if (hole != null) {
                    originalPos = mc.player.getBlockPos().toImmutable();
                    teleportTo(hole);
                    isTPed = true;
                }
            }
        } else {
            if (tickCounter >= delay.get()) {
                tickCounter = 0;
                teleportBack();
                isTPed = false;
            }
        }
    }

    private BlockPos findHole() {
        double playerX = mc.player.getX();
        double playerY = mc.player.getY();
        double playerZ = mc.player.getZ();
        float yaw = mc.player.getYaw();

        float angle;
        ClipDirection dir = direction.get();

        if (dir == ClipDirection.RANDOM) {
            int rand = random.nextInt(3);
            angle = switch (rand) {
                case 0 -> yaw + 90;
                case 1 -> yaw - 90;
                case 2 -> yaw;
                default -> yaw;
            };
        } else if (dir == ClipDirection.LEFT) {
            angle = yaw + 90;
        } else {
            angle = yaw - 90;
        }

        double dx = -Math.sin(Math.toRadians(angle));
        double dz = Math.cos(Math.toRadians(angle));

        int baseX = (int) Math.floor(playerX);
        int baseY = (int) Math.floor(playerY);
        int baseZ = (int) Math.floor(playerZ);

        for (int dist = range.get(); dist >= minRange.get(); dist--) {
            int x = baseX + (int) Math.round(dx * dist);
            int z = baseZ + (int) Math.round(dz * dist);

            for (int dy = -1; dy <= 1; dy++) {
                BlockPos pos = new BlockPos(x, baseY + dy, z);
                if (isValidHole(pos)) {
                    return pos;
                }
            }
        }

        return null;
    }

    private boolean isValidHole(BlockPos pos) {
        if (mc.world == null) return false;

        BlockState current = mc.world.getBlockState(pos);
        if (!current.isAir()) return false;

        BlockState above = mc.world.getBlockState(pos.up());
        if (!above.isAir()) return false;

        BlockState below = mc.world.getBlockState(pos.down());
        if (!below.isOpaque()) return false;

        return true;
    }

    private void teleportTo(BlockPos pos) {
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;

        mc.player.setPosition(x, y, z);
        mc.player.networkHandler.sendPacket(
            new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, mc.player.isOnGround(), false)
        );
    }

    private void teleportBack() {
        if (originalPos == null) return;

        double x = originalPos.getX() + 0.5;
        double y = originalPos.getY();
        double z = originalPos.getZ() + 0.5;

        mc.player.setPosition(x, y, z);
        mc.player.networkHandler.sendPacket(
            new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, mc.player.isOnGround(), false)
        );
    }
}
