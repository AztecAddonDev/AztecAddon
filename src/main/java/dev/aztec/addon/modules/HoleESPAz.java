package dev.aztec.addon.modules;

import dev.aztec.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.*;

public class HoleESPAz extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");
    private final SettingGroup sgRender = settings.createGroup("Render");

    // ── GENERAL ─────────────────────────────────────────────────────
    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("Maximum range to scan for holes.")
        .defaultValue(10)
        .min(5).max(20).sliderMax(15)
        .build()
    );

    private final Setting<Boolean> show1x1 = sgGeneral.add(new BoolSetting.Builder()
        .name("show-1x1")
        .description("Show 1x1 holes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> show2x1 = sgGeneral.add(new BoolSetting.Builder()
        .name("show-2x1")
        .description("Show 2x1 holes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> show2x2 = sgGeneral.add(new BoolSetting.Builder()
        .name("show-2x2")
        .description("Show 2x2 holes.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> hideOwn = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-own")
        .description("Hide the hole you're currently in.")
        .defaultValue(true)
        .build()
    );

    // ── RENDER ──────────────────────────────────────────────────────
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> noSafeColor = sgRender.add(new ColorSetting.Builder()
        .name("no-safe-color")
        .description("Color for holes with breakable blocks (crystals can destroy).")
        .defaultValue(new SettingColor(255, 50, 50, 80))
        .build()
    );

    private final Setting<SettingColor> midSafeColor = sgRender.add(new ColorSetting.Builder()
        .name("mid-safe-color")
        .description("Color for holes with obsidian/crying/echest (crystal resistant but breakable).")
        .defaultValue(new SettingColor(255, 200, 50, 80))
        .build()
    );

    private final Setting<SettingColor> safeColor = sgRender.add(new ColorSetting.Builder()
        .name("safe-color")
        .description("Color for holes with bedrock (indestructible).")
        .defaultValue(new SettingColor(50, 255, 80, 80))
        .build()
    );

    private final Setting<Integer> fadeSpeed = sgRender.add(new IntSetting.Builder()
        .name("fade-speed")
        .description("Speed of fade effect in milliseconds. 0 = no fade.")
        .defaultValue(500)
        .min(0).max(2000).sliderMax(1000)
        .build()
    );

    private final Setting<Boolean> pulseEffect = sgRender.add(new BoolSetting.Builder()
        .name("pulse-effect")
        .description("Add subtle pulse animation to rendered holes.")
        .defaultValue(true)
        .build()
    );

    // ── STATE ───────────────────────────────────────────────────────
    private final Map<BlockPos, HoleInfo> holes = new HashMap<>();
    private int lastScanTick = 0;

    public HoleESPAz() {
        super(AddonTemplate.CATEGORY, "hole-esp-az", "Shows safe holes with crystal resistance classification.");
    }

    @Override
    public void onActivate() {
        holes.clear();
        lastScanTick = 0;
    }

    @Override
    public void onDeactivate() {
        holes.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (mc.player.age - lastScanTick < 5) return;
        lastScanTick = mc.player.age;

        holes.clear();
        scanHoles();
    }

    private void scanHoles() {
        BlockPos playerPos = mc.player.getBlockPos();
        int rangeVal = range.get();

        for (int x = -rangeVal; x <= rangeVal; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -rangeVal; z <= rangeVal; z++) {
                    BlockPos pos = playerPos.add(x, y, z);

                    if (hideOwn.get() && isPlayerInHole(pos)) continue;

                    if (show1x1.get() && is1x1Hole(pos)) {
                        SafetyLevel safety = getSafetyLevel(pos, HoleType.ONE_BY_ONE);
                        holes.put(pos, new HoleInfo(HoleType.ONE_BY_ONE, safety));
                    }

                    if (show2x1.get()) {
                        BlockPos east = pos.east();
                        if (is2x1Hole(pos, east)) {
                            SafetyLevel safety = getSafetyLevel2x1(pos, east);
                            holes.put(pos, new HoleInfo(HoleType.TWO_BY_ONE, safety));
                        }

                        BlockPos south = pos.south();
                        if (is2x1Hole(pos, south)) {
                            SafetyLevel safety = getSafetyLevel2x1(pos, south);
                            holes.put(pos, new HoleInfo(HoleType.TWO_BY_ONE, safety));
                        }
                    }

                    if (show2x2.get()) {
                        BlockPos east = pos.east();
                        BlockPos south = pos.south();
                        BlockPos southeast = pos.east().south();

                        if (is2x2Hole(pos, east, south, southeast)) {
                            SafetyLevel safety = getSafetyLevel2x2(pos, east, south, southeast);
                            holes.put(pos, new HoleInfo(HoleType.TWO_BY_TWO, safety));
                        }
                    }
                }
            }
        }
    }

    private boolean isPlayerInHole(BlockPos pos) {
        if (mc.player == null) return false;
        BlockPos playerPos = mc.player.getBlockPos();
        return pos.equals(playerPos);
    }

    private boolean is1x1Hole(BlockPos pos) {
        if (mc.world == null) return false;

        if (!mc.world.getBlockState(pos).isAir()) return false;

        BlockPos below = pos.down();
        BlockState belowState = mc.world.getBlockState(below);
        if (belowState.isAir() || !belowState.isSolidBlock(mc.world, below)) return false;

        Direction[] sides = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        for (Direction dir : sides) {
            BlockPos side = pos.offset(dir);
            BlockState sideState = mc.world.getBlockState(side);
            if (sideState.isAir() || !sideState.isSolidBlock(mc.world, side)) return false;
        }

        return true;
    }

    private boolean is2x1Hole(BlockPos pos1, BlockPos pos2) {
        if (mc.world == null) return false;

        if (!mc.world.getBlockState(pos1).isAir() || !mc.world.getBlockState(pos2).isAir()) return false;

        if (!mc.world.getBlockState(pos1.down()).isSolidBlock(mc.world, pos1.down())) return false;
        if (!mc.world.getBlockState(pos2.down()).isSolidBlock(mc.world, pos2.down())) return false;

        Set<BlockPos> checked = new HashSet<>();
        checked.add(pos1);
        checked.add(pos2);

        Direction[] allDirs = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

        for (BlockPos pos : new BlockPos[]{pos1, pos2}) {
            for (Direction dir : allDirs) {
                BlockPos side = pos.offset(dir);
                if (checked.contains(side)) continue;

                BlockState sideState = mc.world.getBlockState(side);
                if (sideState.isAir() || !sideState.isSolidBlock(mc.world, side)) return false;
            }
        }

        return true;
    }

    private boolean is2x2Hole(BlockPos p1, BlockPos p2, BlockPos p3, BlockPos p4) {
        if (mc.world == null) return false;

        BlockPos[] positions = {p1, p2, p3, p4};

        for (BlockPos pos : positions) {
            if (!mc.world.getBlockState(pos).isAir()) return false;
            if (!mc.world.getBlockState(pos.down()).isSolidBlock(mc.world, pos.down())) return false;
        }

        Set<BlockPos> checked = new HashSet<>(Arrays.asList(positions));
        Direction[] allDirs = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

        for (BlockPos pos : positions) {
            for (Direction dir : allDirs) {
                BlockPos side = pos.offset(dir);
                if (checked.contains(side)) continue;

                BlockState sideState = mc.world.getBlockState(side);
                if (sideState.isAir() || !sideState.isSolidBlock(mc.world, side)) return false;
            }
        }

        return true;
    }

    private SafetyLevel getSafetyLevel(BlockPos pos, HoleType type) {
        if (mc.world == null) return SafetyLevel.NO_SAFE;

        List<BlockPos> walls = getWallPositions(pos, type);
        BlockPos floor = pos.down();

        boolean hasBedrock = false;
        boolean hasObsidian = false;
        boolean hasBreakable = false;

        BlockState floorState = mc.world.getBlockState(floor);
        BlockSafety floorSafety = classifyBlock(floorState);
        if (floorSafety == BlockSafety.BEDROCK) hasBedrock = true;
        else if (floorSafety == BlockSafety.OBSIDIAN) hasObsidian = true;
        else if (floorSafety == BlockSafety.BREAKABLE) hasBreakable = true;

        for (BlockPos wall : walls) {
            BlockState wallState = mc.world.getBlockState(wall);
            BlockSafety wallSafety = classifyBlock(wallState);

            if (wallSafety == BlockSafety.BEDROCK) hasBedrock = true;
            else if (wallSafety == BlockSafety.OBSIDIAN) hasObsidian = true;
            else if (wallSafety == BlockSafety.BREAKABLE) hasBreakable = true;
        }

        if (hasBreakable) return SafetyLevel.NO_SAFE;
        if (hasBedrock && !hasObsidian) return SafetyLevel.SAFE;
        if (hasObsidian) return SafetyLevel.MID_SAFE;

        return SafetyLevel.NO_SAFE;
    }

    private SafetyLevel getSafetyLevel2x1(BlockPos pos1, BlockPos pos2) {
        SafetyLevel s1 = getSafetyLevel(pos1, HoleType.TWO_BY_ONE);
        SafetyLevel s2 = getSafetyLevel(pos2, HoleType.TWO_BY_ONE);

        return getWorstSafety(s1, s2);
    }

    private SafetyLevel getSafetyLevel2x2(BlockPos p1, BlockPos p2, BlockPos p3, BlockPos p4) {
        SafetyLevel s1 = getSafetyLevel(p1, HoleType.TWO_BY_TWO);
        SafetyLevel s2 = getSafetyLevel(p2, HoleType.TWO_BY_TWO);
        SafetyLevel s3 = getSafetyLevel(p3, HoleType.TWO_BY_TWO);
        SafetyLevel s4 = getSafetyLevel(p4, HoleType.TWO_BY_TWO);

        return getWorstSafety(getWorstSafety(s1, s2), getWorstSafety(s3, s4));
    }

    private SafetyLevel getWorstSafety(SafetyLevel a, SafetyLevel b) {
        if (a == SafetyLevel.NO_SAFE || b == SafetyLevel.NO_SAFE) return SafetyLevel.NO_SAFE;
        if (a == SafetyLevel.MID_SAFE || b == SafetyLevel.MID_SAFE) return SafetyLevel.MID_SAFE;
        return SafetyLevel.SAFE;
    }

    private List<BlockPos> getWallPositions(BlockPos pos, HoleType type) {
        List<BlockPos> walls = new ArrayList<>();
        Direction[] sides = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

        for (Direction dir : sides) {
            walls.add(pos.offset(dir));
        }

        return walls;
    }

    private BlockSafety classifyBlock(BlockState state) {
        Block block = state.getBlock();

        if (block == Blocks.BEDROCK) return BlockSafety.BEDROCK;

        if (block == Blocks.OBSIDIAN ||
            block == Blocks.CRYING_OBSIDIAN ||
            block == Blocks.ENDER_CHEST ||
            block == Blocks.RESPAWN_ANCHOR ||
            block == Blocks.ANCIENT_DEBRIS ||
            block == Blocks.NETHERITE_BLOCK) {
            return BlockSafety.OBSIDIAN;
        }

        return BlockSafety.BREAKABLE;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        for (Map.Entry<BlockPos, HoleInfo> entry : holes.entrySet()) {
            BlockPos pos = entry.getKey();
            HoleInfo info = entry.getValue();

            SettingColor color = switch (info.safety) {
                case NO_SAFE -> noSafeColor.get();
                case MID_SAFE -> midSafeColor.get();
                case SAFE -> safeColor.get();
            };

            renderHole(event, pos, info.type, color);
        }
    }

    private void renderHole(Render3DEvent event, BlockPos pos, HoleType type, SettingColor color) {
        double shrink = 0.001;
        Box box;

        if (type == HoleType.ONE_BY_ONE) {
            box = new Box(
                pos.getX() + shrink, pos.getY() + shrink, pos.getZ() + shrink,
                pos.getX() + 1 - shrink, pos.getY() + 1 - shrink, pos.getZ() + 1 - shrink
            );
        } else if (type == HoleType.TWO_BY_ONE) {
            BlockPos east = pos.east();
            if (holes.containsKey(east)) {
                box = new Box(
                    pos.getX() + shrink, pos.getY() + shrink, pos.getZ() + shrink,
                    pos.getX() + 2 - shrink, pos.getY() + 1 - shrink, pos.getZ() + 1 - shrink
                );
            } else {
                box = new Box(
                    pos.getX() + shrink, pos.getY() + shrink, pos.getZ() + shrink,
                    pos.getX() + 1 - shrink, pos.getY() + 1 - shrink, pos.getZ() + 2 - shrink
                );
            }
        } else {
            box = new Box(
                pos.getX() + shrink, pos.getY() + shrink, pos.getZ() + shrink,
                pos.getX() + 2 - shrink, pos.getY() + 1 - shrink, pos.getZ() + 2 - shrink
            );
        }

        int alpha = color.a;

        if (fadeSpeed.get() > 0) {
            double pulse = Math.sin(System.currentTimeMillis() / 500.0 * Math.PI) * 0.15 + 0.85;
            alpha = (int) (alpha * pulse);
        } else if (pulseEffect.get()) {
            double pulse = Math.sin(System.currentTimeMillis() / 500.0 * Math.PI) * 0.15 + 0.85;
            alpha = (int) (alpha * pulse);
        }

        SettingColor fadeColor = new SettingColor(color.r, color.g, color.b, alpha);
        event.renderer.box(box, fadeColor, fadeColor, shapeMode.get(), 0);
    }

    private enum HoleType {
        ONE_BY_ONE,
        TWO_BY_ONE,
        TWO_BY_TWO
    }

    private enum SafetyLevel {
        NO_SAFE,
        MID_SAFE,
        SAFE
    }

    private enum BlockSafety {
        BEDROCK,
        OBSIDIAN,
        BREAKABLE
    }

    private static class HoleInfo {
        HoleType type;
        SafetyLevel safety;
        long timestamp;

        HoleInfo(HoleType type, SafetyLevel safety) {
            this.type = type;
            this.safety = safety;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
