package dev.aztec.addon.modules;

import dev.aztec.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.*;

public class AztecSurround extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");
    private final SettingGroup sgBlock = settings.createGroup("Block");
    private final SettingGroup sgTiming = settings.createGroup("Timing");
    private final SettingGroup sgPlacement = settings.createGroup("Placement");
    private final SettingGroup sgAntiCev = settings.createGroup("Anti-Cev");
    private final SettingGroup sgAntiCiv = settings.createGroup("Anti-Civ");
    private final SettingGroup sgNoFall = settings.createGroup("NoFall");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgDebug = settings.createGroup("Debug");

    // ── GENERAL ─────────────────────────────────────────────────────
    private final Setting<CenterMode> centerMode = sgGeneral.add(new EnumSetting.Builder<CenterMode>()
        .name("center-mode")
        .description("How to center the player for surround.")
        .defaultValue(CenterMode.None)
        .build()
    );

    private final Setting<Double> centerSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("center-speed")
        .description("Speed of center movement.")
        .defaultValue(0.3)
        .min(0.1).max(1.0).sliderMax(0.5)
        .build()
    );

    private final Setting<Boolean> onlyOnGround = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-ground")
        .description("Only place blocks when on ground.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> disableOnDeath = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-on-death")
        .description("Disable module when you die.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> toggleOnMove = sgGeneral.add(new BoolSetting.Builder()
        .name("toggle-on-move")
        .description("Disable module when you press movement keys.")
        .defaultValue(true)
        .build()
    );

    // ── BLOCK ───────────────────────────────────────────────────────
    private final Setting<BlockType> blockType = sgBlock.add(new EnumSetting.Builder<BlockType>()
        .name("block-type")
        .description("Which block to use for surround.")
        .defaultValue(BlockType.Obsidian)
        .build()
    );

    private final Setting<SwapMode> swapMode = sgBlock.add(new EnumSetting.Builder<SwapMode>()
        .name("swap-mode")
        .description("How to swap to the block.")
        .defaultValue(SwapMode.Silent)
        .build()
    );

    private final Setting<Boolean> swapBack = sgBlock.add(new BoolSetting.Builder()
        .name("swap-back")
        .description("Swap back to previous item after placing.")
        .defaultValue(true)
        .build()
    );

    // ── TIMING ──────────────────────────────────────────────────────
    private final Setting<Integer> placeDelay = sgTiming.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Ticks to wait between placements.")
        .defaultValue(0)
        .min(0).max(10).sliderMax(5)
        .build()
    );

    private final Setting<Integer> blocksPerTick = sgTiming.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("Maximum blocks to place per tick.")
        .defaultValue(6)
        .min(1).max(12).sliderMax(6)
        .build()
    );

    // ── PLACEMENT ───────────────────────────────────────────────────
    private final Setting<Boolean> rotate = sgPlacement.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotate head towards the block when placing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> airPlace = sgPlacement.add(new BoolSetting.Builder()
        .name("air-place")
        .description("Allow placing without adjacent support.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> breakReplaceables = sgPlacement.add(new BoolSetting.Builder()
        .name("break-replaceables")
        .description("Break grass, flowers, and other replaceable blocks before placing.")
        .defaultValue(true)
        .build()
    );

    // ── ANTI-CEV ────────────────────────────────────────────────────
    private final Setting<Boolean> antiCevEnabled = sgAntiCev.add(new BoolSetting.Builder()
        .name("anti-cev")
        .description("Place block above head to prevent crystal placement.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> doubleCev = sgAntiCev.add(new BoolSetting.Builder()
        .name("double-cev")
        .description("Place two blocks above head for extra protection.")
        .defaultValue(false)
        .visible(antiCevEnabled::get)
        .build()
    );

    // ── ANTI-CIV ────────────────────────────────────────────────────
    private final Setting<Boolean> antiCivEnabled = sgAntiCiv.add(new BoolSetting.Builder()
        .name("anti-civ")
        .description("Place blocks in corners.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> doubleCiv = sgAntiCiv.add(new BoolSetting.Builder()
        .name("double-civ")
        .description("Place blocks above surround and corners.")
        .defaultValue(false)
        .visible(antiCivEnabled::get)
        .build()
    );

    // ── NOFALL ──────────────────────────────────────────────────────
    private final Setting<Boolean> noFallEnabled = sgNoFall.add(new BoolSetting.Builder()
        .name("no-fall")
        .description("Place block below player if there is air.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> noFallRange = sgNoFall.add(new IntSetting.Builder()
        .name("no-fall-range")
        .description("How many blocks below to check for air.")
        .defaultValue(3)
        .min(1).max(10).sliderMax(5)
        .build()
    );

    // ── RENDER ──────────────────────────────────────────────────────
    private final Setting<Boolean> renderEnabled = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Render surround positions.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Lines)
        .build()
    );

    private final Setting<Integer> fadeSpeed = sgRender.add(new IntSetting.Builder()
        .name("fade-speed")
        .description("Speed of fade effect in milliseconds. 0 = no fade.")
        .defaultValue(800)
        .min(0).max(3000).sliderMax(2000)
        .build()
    );

    private final Setting<Boolean> pulseEffect = sgRender.add(new BoolSetting.Builder()
        .name("pulse-effect")
        .description("Add subtle pulse animation to rendered blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Color for side blocks.")
        .defaultValue(new SettingColor(100, 200, 150, 80))
        .build()
    );

    private final Setting<SettingColor> missingColor = sgRender.add(new ColorSetting.Builder()
        .name("missing-color")
        .description("Color for missing blocks.")
        .defaultValue(new SettingColor(220, 100, 100, 80))
        .build()
    );

    private final Setting<SettingColor> antiCevColor = sgRender.add(new ColorSetting.Builder()
        .name("anti-cev-color")
        .description("Color for anti-cev blocks.")
        .defaultValue(new SettingColor(200, 200, 120, 80))
        .build()
    );

    // ── DEBUG ───────────────────────────────────────────────────────
    private final Setting<Boolean> debugInfo = sgDebug.add(new BoolSetting.Builder()
        .name("debug-info")
        .description("Show debug information.")
        .defaultValue(false)
        .build()
    );

    // ── ENUMS ───────────────────────────────────────────────────────
    public enum CenterMode { None, Move, Teleport }
    public enum SwapMode { Silent, Normal, None }
    public enum BlockType { Obsidian, EnderChest, NetheriteBlock, CryingObsidian, Any }

    // ── STATE ───────────────────────────────────────────────────────
    private int previousSlot = -1;
    private int lastPlaceTick = 0;
    private final Map<BlockPos, Long> placedTimestamps = new HashMap<>();
    private BlockPos lastPlayerPos = null;
    private boolean centeringDone = false;

    public AztecSurround() {
        super(AddonTemplate.CATEGORY, "aztec-surround", "Advanced surround with anti-cev and anti-civ.");
    }

    @Override
    public void onActivate() {
        previousSlot = -1;
        placedTimestamps.clear();
        lastPlayerPos = null;
        centeringDone = false;

        if (mc.player != null && swapMode.get() == SwapMode.Normal) {
            previousSlot = mc.player.getInventory().getSelectedSlot();
        }
    }

    @Override
    public void onDeactivate() {
        placedTimestamps.clear();

        if (swapBack.get() && previousSlot != -1 && mc.player != null && swapMode.get() == SwapMode.Normal) {
            InvUtils.swap(previousSlot, false);
        }
        previousSlot = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (mc.player.isDead()) {
            if (disableOnDeath.get()) toggle();
            return;
        }

        if (toggleOnMove.get() && isPlayerMoving()) {
            toggle();
            return;
        }

        if (!centeringDone) {
            boolean centered = centerPlayer();
            if (centered) {
                centeringDone = true;
                lastPlayerPos = mc.player.getBlockPos();
            }
            return;
        }

        if (centerMode.get() != CenterMode.None && !isPlayerCentered()) {
            centeringDone = false;
            return;
        }

        if (onlyOnGround.get() && !mc.player.isOnGround()) return;

        BlockPos currentPos = mc.player.getBlockPos();
        if (lastPlayerPos == null || !lastPlayerPos.equals(currentPos)) {
            lastPlayerPos = currentPos;
        }

        if (mc.player.age - lastPlaceTick >= placeDelay.get()) {
            placeSurroundBlocks();
        }

        if (noFallEnabled.get()) {
            checkNoFall();
        }

        cleanupTimestamps();

        if (debugInfo.get()) {
            Block blockToPlace = getBlockToPlace();
            FindItemResult block = findBlockInHotbar(blockToPlace);
            String blockName = blockToPlace != null ? blockToPlace.toString().replace("Block{", "").replace("}", "") : "None";
            info("AztecSurround | Block: %s | Slot: %d | Ping: %dms | Centered: %s",
                blockName, block.found() ? block.slot() : -1, getPing(),
                centeringDone ? "Yes" : "No");
        }
    }

    private boolean isPlayerCentered() {
        if (mc.player == null) return false;
        if (centerMode.get() == CenterMode.None) return true;

        BlockPos blockPos = mc.player.getBlockPos();
        double centerX = blockPos.getX() + 0.5;
        double centerZ = blockPos.getZ() + 0.5;

        double dx = Math.abs(centerX - mc.player.getX());
        double dz = Math.abs(centerZ - mc.player.getZ());

        return dx < 0.15 && dz < 0.15;
    }

    private void placeSurroundBlocks() {
        if (mc.player == null || mc.world == null) return;

        Block blockToPlace = getBlockToPlace();
        if (blockToPlace == null) return;

        FindItemResult block = findBlockInHotbar(blockToPlace);
        if (!block.found()) return;

        BlockPos playerPos = mc.player.getBlockPos();
        int placedThisTick = 0;

        Direction[] horizontalDirs = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        for (Direction dir : horizontalDirs) {
            if (placedThisTick >= blocksPerTick.get()) break;
            BlockPos sidePos = playerPos.offset(dir);
            if (tryPlaceBlock(sidePos, block, blockToPlace)) {
                placedThisTick++;
            }
        }

        if (antiCevEnabled.get()) {
            BlockPos topPos = playerPos.up(2);
            if (tryPlaceBlock(topPos, block, blockToPlace)) {
                placedThisTick++;
            }

            if (doubleCev.get()) {
                BlockPos doubleTopPos = playerPos.up(3);
                if (tryPlaceBlock(doubleTopPos, block, blockToPlace)) {
                    placedThisTick++;
                }
            }
        }

        if (antiCivEnabled.get()) {
            BlockPos[] corners = {
                playerPos.north().east(),
                playerPos.north().west(),
                playerPos.south().east(),
                playerPos.south().west()
            };

            for (BlockPos cornerPos : corners) {
                if (placedThisTick >= blocksPerTick.get()) break;
                if (tryPlaceBlock(cornerPos, block, blockToPlace)) {
                    placedThisTick++;
                }
            }

            if (doubleCiv.get()) {
                for (Direction dir : horizontalDirs) {
                    if (placedThisTick >= blocksPerTick.get()) break;
                    BlockPos doublePos = playerPos.offset(dir).up();
                    if (tryPlaceBlock(doublePos, block, blockToPlace)) {
                        placedThisTick++;
                    }
                }

                for (BlockPos cornerPos : corners) {
                    if (placedThisTick >= blocksPerTick.get()) break;
                    BlockPos doubleCornerPos = cornerPos.up();
                    if (tryPlaceBlock(doubleCornerPos, block, blockToPlace)) {
                        placedThisTick++;
                    }
                }
            }
        }

        if (placedThisTick > 0) {
            lastPlaceTick = mc.player.age;
        }
    }

    private boolean tryPlaceBlock(BlockPos pos, FindItemResult block, Block blockToPlace) {
        if (mc.world == null || mc.player == null) return false;

        if (centerMode.get() != CenterMode.None && !isPlayerCentered()) return false;

        BlockState state = mc.world.getBlockState(pos);

        if (state.getBlock() == blockToPlace) return false;

        if (!isPlaceable(state)) {
            if (breakReplaceables.get() && isBreakable(state)) {
                breakBlockAt(pos);
                return false;
            }
            return false;
        }

        BlockPos playerPos = mc.player.getBlockPos();
        if (pos.equals(playerPos) || pos.equals(playerPos.up())) return false;

        if (!isReachable(pos)) return false;

        Long lastPlaced = placedTimestamps.get(pos);
        if (lastPlaced != null && System.currentTimeMillis() - lastPlaced < 500) return false;

        boolean placed = false;

        if (swapMode.get() == SwapMode.Silent) {
            placed = BlockUtils.place(pos, block, rotate.get(), 100, true, true, swapBack.get());
        } else if (swapMode.get() == SwapMode.Normal) {
            if (!block.isMainHand()) {
                InvUtils.swap(block.slot(), swapBack.get());
            }
            placed = BlockUtils.place(pos, block, rotate.get(), 100, true, true, false);
        } else if (swapMode.get() == SwapMode.None) {
            if (block.isMainHand()) {
                placed = BlockUtils.place(pos, block, rotate.get(), 100, true, true, false);
            }
        }

        if (placed) {
            placedTimestamps.put(pos, System.currentTimeMillis());
        }

        return placed;
    }

    private boolean isPlaceable(BlockState state) {
        if (state.isAir()) return true;
        return state.isReplaceable();
    }

    private boolean isBreakable(BlockState state) {
        if (state.isAir()) return false;

        Block block = state.getBlock();

        return block == Blocks.SHORT_GRASS ||
            block == Blocks.TALL_GRASS ||
            block == Blocks.FERN ||
            block == Blocks.LARGE_FERN ||
            block == Blocks.DEAD_BUSH ||
            block == Blocks.DANDELION ||
            block == Blocks.POPPY ||
            block == Blocks.BLUE_ORCHID ||
            block == Blocks.ALLIUM ||
            block == Blocks.AZURE_BLUET ||
            block == Blocks.RED_TULIP ||
            block == Blocks.ORANGE_TULIP ||
            block == Blocks.WHITE_TULIP ||
            block == Blocks.PINK_TULIP ||
            block == Blocks.OXEYE_DAISY ||
            block == Blocks.CORNFLOWER ||
            block == Blocks.LILY_OF_THE_VALLEY ||
            block == Blocks.SUNFLOWER ||
            block == Blocks.LILAC ||
            block == Blocks.ROSE_BUSH ||
            block == Blocks.PEONY ||
            block == Blocks.SNOW ||
            block == Blocks.VINE ||
            block == Blocks.GLOW_LICHEN ||
            block == Blocks.SEAGRASS ||
            block == Blocks.TALL_SEAGRASS ||
            block == Blocks.KELP ||
            block == Blocks.KELP_PLANT ||
            block == Blocks.BROWN_MUSHROOM ||
            block == Blocks.RED_MUSHROOM ||
            block == Blocks.CRIMSON_FUNGUS ||
            block == Blocks.WARPED_FUNGUS ||
            block == Blocks.NETHER_SPROUTS ||
            block == Blocks.TWISTING_VINES ||
            block == Blocks.TWISTING_VINES_PLANT ||
            block == Blocks.WEEPING_VINES ||
            block == Blocks.WEEPING_VINES_PLANT ||
            block == Blocks.SUGAR_CANE ||
            block == Blocks.COBWEB ||
            state.isReplaceable();
    }

    private void breakBlockAt(BlockPos pos) {
        if (mc.interactionManager == null || mc.player == null) return;

        try {
            mc.interactionManager.updateBlockBreakingProgress(pos, Direction.UP);
            mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        } catch (Exception ignored) {
        }
    }

    private void checkNoFall() {
        if (mc.player == null || mc.world == null) return;

        if (centerMode.get() != CenterMode.None && !isPlayerCentered()) return;

        BlockPos playerPos = mc.player.getBlockPos();
        Block blockToPlace = getBlockToPlace();
        if (blockToPlace == null) return;

        FindItemResult block = findBlockInHotbar(blockToPlace);
        if (!block.found()) return;

        for (int i = 1; i <= noFallRange.get(); i++) {
            BlockPos belowPos = playerPos.down(i);

            if (mc.world.getBlockState(belowPos).isAir()) {
                if (canPlaceBelow(belowPos)) {
                    tryPlaceBlock(belowPos, block, blockToPlace);
                    break;
                }
            } else {
                break;
            }
        }
    }

    private boolean canPlaceBelow(BlockPos pos) {
        if (mc.world == null) return false;

        Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN};

        for (Direction dir : directions) {
            BlockPos neighborPos = pos.offset(dir);
            if (!mc.world.getBlockState(neighborPos).isAir()) {
                return true;
            }
        }

        return false;
    }

    private boolean isPlayerMoving() {
        if (mc.player == null) return false;

        return mc.options.forwardKey.isPressed() ||
            mc.options.backKey.isPressed() ||
            mc.options.leftKey.isPressed() ||
            mc.options.rightKey.isPressed() ||
            mc.options.jumpKey.isPressed() ||
            mc.options.sneakKey.isPressed();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!renderEnabled.get()) return;
        if (mc.player == null || mc.world == null) return;

        if (centerMode.get() != CenterMode.None && !centeringDone) return;

        BlockPos playerPos = mc.player.getBlockPos();

        Direction[] horizontalDirs = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

        for (Direction dir : horizontalDirs) {
            BlockPos sidePos = playerPos.offset(dir);
            if (isPlaceable(mc.world.getBlockState(sidePos))) {
                renderBlock(event, sidePos, missingColor.get());
            }
        }

        if (antiCivEnabled.get()) {
            BlockPos[] corners = {
                playerPos.north().east(),
                playerPos.north().west(),
                playerPos.south().east(),
                playerPos.south().west()
            };

            for (BlockPos cornerPos : corners) {
                if (isPlaceable(mc.world.getBlockState(cornerPos))) {
                    renderBlock(event, cornerPos, sideColor.get());
                }
            }

            if (doubleCiv.get()) {
                for (Direction dir : horizontalDirs) {
                    BlockPos doublePos = playerPos.offset(dir).up();
                    if (isPlaceable(mc.world.getBlockState(doublePos))) {
                        renderBlock(event, doublePos, sideColor.get());
                    }
                }

                for (BlockPos cornerPos : corners) {
                    BlockPos doubleCornerPos = cornerPos.up();
                    if (isPlaceable(mc.world.getBlockState(doubleCornerPos))) {
                        renderBlock(event, doubleCornerPos, sideColor.get());
                    }
                }
            }
        }

        if (antiCevEnabled.get()) {
            BlockPos topPos = playerPos.up(2);
            if (isPlaceable(mc.world.getBlockState(topPos))) {
                renderBlock(event, topPos, antiCevColor.get());
            }

            if (doubleCev.get()) {
                BlockPos doubleTopPos = playerPos.up(3);
                if (isPlaceable(mc.world.getBlockState(doubleTopPos))) {
                    renderBlock(event, doubleTopPos, antiCevColor.get());
                }
            }
        }
    }

    private void renderBlock(Render3DEvent event, BlockPos pos, SettingColor color) {
        double shrink = 0.001;
        Box box = new Box(
            pos.getX() + shrink, pos.getY() + shrink, pos.getZ() + shrink,
            pos.getX() + 1 - shrink, pos.getY() + 1 - shrink, pos.getZ() + 1 - shrink
        );

        int alpha = color.a;

        if (fadeSpeed.get() > 0) {
            Long timestamp = placedTimestamps.get(pos);
            if (timestamp != null) {
                long timeSince = System.currentTimeMillis() - timestamp;
                double progress = Math.min(1.0, (double) timeSince / fadeSpeed.get());
                alpha = (int) (color.a * (1.0 - easeInOutCubic(progress)));
                if (alpha <= 0) return;
            }

            if (pulseEffect.get()) {
                double pulse = Math.sin(System.currentTimeMillis() / 500.0 * Math.PI) * 0.15 + 0.85;
                alpha = (int) (alpha * pulse);
            }
        }

        SettingColor fadeColor = new SettingColor(color.r, color.g, color.b, alpha);
        event.renderer.box(box, fadeColor, fadeColor, shapeMode.get(), 0);
    }

    private double easeInOutCubic(double x) {
        return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
    }

    private boolean isReachable(BlockPos pos) {
        if (mc.player == null || mc.world == null) return false;

        double reachDistance = mc.player.getAbilities().creativeMode ? 5.0 : 4.5;

        if (airPlace.get()) {
            double centerX = pos.getX() + 0.5;
            double centerY = pos.getY() + 0.5;
            double centerZ = pos.getZ() + 0.5;
            double distance = Math.sqrt(
                Math.pow(mc.player.getX() - centerX, 2) +
                    Math.pow(mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()) - centerY, 2) +
                    Math.pow(mc.player.getZ() - centerZ, 2)
            );
            if (distance <= reachDistance) return true;
        }

        Direction[] directions = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

        for (Direction dir : directions) {
            BlockPos neighborPos = pos.offset(dir);

            if (!mc.world.getBlockState(neighborPos).isAir()) {
                double hitX = neighborPos.getX() + 0.5 - dir.getOffsetX() * 0.5;
                double hitY = neighborPos.getY() + 0.5 - dir.getOffsetY() * 0.5;
                double hitZ = neighborPos.getZ() + 0.5 - dir.getOffsetZ() * 0.5;

                double distance = Math.sqrt(
                    Math.pow(mc.player.getX() - hitX, 2) +
                        Math.pow(mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()) - hitY, 2) +
                        Math.pow(mc.player.getZ() - hitZ, 2)
                );

                if (distance <= reachDistance) return true;
            }
        }

        return false;
    }

    private Block getBlockToPlace() {
        return switch (blockType.get()) {
            case Obsidian -> Blocks.OBSIDIAN;
            case EnderChest -> Blocks.ENDER_CHEST;
            case NetheriteBlock -> Blocks.NETHERITE_BLOCK;
            case CryingObsidian -> Blocks.CRYING_OBSIDIAN;
            case Any -> {
                if (findBlockInHotbar(Blocks.OBSIDIAN).found()) yield Blocks.OBSIDIAN;
                else if (findBlockInHotbar(Blocks.ENDER_CHEST).found()) yield Blocks.ENDER_CHEST;
                else if (findBlockInHotbar(Blocks.NETHERITE_BLOCK).found()) yield Blocks.NETHERITE_BLOCK;
                else if (findBlockInHotbar(Blocks.CRYING_OBSIDIAN).found()) yield Blocks.CRYING_OBSIDIAN;
                else yield null;
            }
        };
    }

    private FindItemResult findBlockInHotbar(Block block) {
        if (block == null || mc.player == null) return new FindItemResult(-1, 0);
        return InvUtils.findInHotbar(itemStack -> Block.getBlockFromItem(itemStack.getItem()) == block);
    }

    private int getPing() {
        if (mc.player == null || mc.player.networkHandler == null) return 0;
        var entry = mc.player.networkHandler.getPlayerListEntry(mc.player.getUuid());
        return entry != null ? entry.getLatency() : 0;
    }

    private void cleanupTimestamps() {
        long currentTime = System.currentTimeMillis();
        placedTimestamps.entrySet().removeIf(entry -> currentTime - entry.getValue() > 5000);
    }

    private boolean centerPlayer() {
        if (mc.player == null || centerMode.get() == CenterMode.None) return true;

        BlockPos blockPos = mc.player.getBlockPos();
        double centerX = blockPos.getX() + 0.5;
        double centerZ = blockPos.getZ() + 0.5;

        double dx = centerX - mc.player.getX();
        double dz = centerZ - mc.player.getZ();

        if (Math.abs(dx) < 0.15 && Math.abs(dz) < 0.15) return true;

        if (centerMode.get() == CenterMode.Move) {
            if (mc.world.getBlockState(blockPos.down()).isAir()) return false;

            double speed = centerSpeed.get();
            double vx = dx != 0 ? Math.signum(dx) * Math.min(Math.abs(dx) * 2, speed) : 0;
            double vz = dz != 0 ? Math.signum(dz) * Math.min(Math.abs(dz) * 2, speed) : 0;

            mc.player.setVelocity(vx, mc.player.getVelocity().y, vz);
            return false;
        } else if (centerMode.get() == CenterMode.Teleport) {
            mc.player.setPosition(centerX, mc.player.getY(), centerZ);
            mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
            return true;
        }

        return false;
    }
}
