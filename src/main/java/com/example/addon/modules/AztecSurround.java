package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
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
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgDebug = settings.createGroup("Debug");


    private final Setting<CenterMode> centerMode = sgGeneral.add(new EnumSetting.Builder<CenterMode>()
        .name("center-mode")
        .description("How to center the player for surround.")
        .defaultValue(CenterMode.None)
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


    private final Setting<BlockType> blockType = sgBlock.add(new EnumSetting.Builder<BlockType>()
        .name("block-type")
        .description("Which block to use for surround.")
        .defaultValue(BlockType.Obsidian)
        .build()
    );

    private final Setting<SwapMode> swapMode = sgBlock.add(new EnumSetting.Builder<SwapMode>()
        .name("swap-mode")
        .description("How to swap to the block. Silent uses packets, Normal is visible, None requires you to hold it.")
        .defaultValue(SwapMode.Silent)
        .build()
    );

    private final Setting<Boolean> swapBack = sgBlock.add(new BoolSetting.Builder()
        .name("swap-back")
        .description("Swap back to previous item after placing.")
        .defaultValue(true)
        .build()
    );


    private final Setting<Integer> placeDelay = sgTiming.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Ticks to wait between placements.")
        .defaultValue(0)
        .min(0)
        .max(10)
        .sliderMax(5)
        .build()
    );

    private final Setting<Integer> blocksPerTick = sgTiming.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("Maximum blocks to place per tick.")
        .defaultValue(4)
        .min(1)
        .max(10)
        .sliderMax(5)
        .build()
    );


    private final Setting<Boolean> rotate = sgPlacement.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotate head towards the block when placing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> airPlace = sgPlacement.add(new BoolSetting.Builder()
        .name("air-place")
        .description("Permite usar el bloque donde está el jugador como soporte.")
        .defaultValue(false)
        .build()
    );


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


    private final Setting<Boolean> antiCivEnabled = sgAntiCiv.add(new BoolSetting.Builder()
        .name("anti-civ")
        .description("Place blocks in corners. When disabled, only places 4 classic surround blocks.")
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
        .defaultValue(1000)
        .min(0)
        .max(3000)
        .sliderMax(2000)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Color for side blocks.")
        .defaultValue(new SettingColor(0, 255, 0, 100))
        .build()
    );

    private final Setting<SettingColor> missingColor = sgRender.add(new ColorSetting.Builder()
        .name("missing-color")
        .description("Color for missing blocks.")
        .defaultValue(new SettingColor(255, 0, 0, 100))
        .build()
    );

    private final Setting<SettingColor> antiCevColor = sgRender.add(new ColorSetting.Builder()
        .name("anti-cev-color")
        .description("Color for anti-cev blocks.")
        .defaultValue(new SettingColor(255, 255, 0, 100))
        .build()
    );


    private final Setting<Boolean> debugInfo = sgDebug.add(new BoolSetting.Builder()
        .name("debug-info")
        .description("Show debug information.")
        .defaultValue(false)
        .build()
    );


    public enum CenterMode {
        None,
        Move,
        Teleport
    }

    public enum SwapMode {
        Silent,
        Normal,
        None
    }

    public enum BlockType {
        Obsidian,
        EnderChest,
        NetheriteBlock,
        CryingObsidian,
        Any
    }

    public enum PlacementPriority {
        Critical,
        High,
        Normal,
        Low
    }

    public enum PlacementState {
        Queued,
        Pending,
        Confirmed,
        Failed
    }


    private int previousSlot = -1;
    private int lastPlaceTick = 0;
    private final Map<BlockPos, PlacementInfo> pendingPlacements = new HashMap<>();
    private final PriorityQueue<PlacementTask> placementQueue = new PriorityQueue<>(
        Comparator.comparingInt((PlacementTask t) -> t.priority.ordinal()).reversed()
    );
    private BlockPos lastPlayerPos = null;


    private static class PlacementInfo {
        PlacementState state;
        long timestamp;
        int retryCount;
        Block expectedBlock;

        PlacementInfo(PlacementState state, Block expectedBlock) {
            this.state = state;
            this.timestamp = System.currentTimeMillis();
            this.retryCount = 0;
            this.expectedBlock = expectedBlock;
        }
    }


    private static class PlacementTask {
        BlockPos pos;
        PlacementPriority priority;
        Direction placeDirection;
        Block block;

        PlacementTask(BlockPos pos, PlacementPriority priority, Direction placeDirection, Block block) {
            this.pos = pos;
            this.priority = priority;
            this.placeDirection = placeDirection;
            this.block = block;
        }
    }

    public AztecSurround() {
        super(AddonTemplate.CATEGORY, "aztec-surround", "Advanced surround with anti-cev and anti-civ.");
    }

    @Override
    public void onActivate() {
        previousSlot = -1;
        pendingPlacements.clear();
        placementQueue.clear();
        lastPlayerPos = mc.player != null ? mc.player.getBlockPos() : null;

        if (mc.player != null && swapMode.get() == SwapMode.Normal) {
            previousSlot = mc.player.getInventory().getSelectedSlot();
        }

        recalculateSurround();
    }

    @Override
    public void onDeactivate() {
        pendingPlacements.clear();
        placementQueue.clear();

        if (swapBack.get() && previousSlot != -1 && mc.player != null && swapMode.get() == SwapMode.Normal) {
            InvUtils.swap(previousSlot, false);
        }
        previousSlot = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (mc.player.isDead()) {
            if (disableOnDeath.get()) {
                toggle();
            }
            return;
        }

        centerPlayer();

        if (onlyOnGround.get() && !mc.player.isOnGround()) return;

        BlockPos currentPos = mc.player.getBlockPos();
        if (lastPlayerPos == null || !lastPlayerPos.equals(currentPos)) {
            lastPlayerPos = currentPos;
            recalculateSurround();
        }

        rebuildMissing();

        if (mc.player.age - lastPlaceTick >= placeDelay.get()) {
            processPlacementQueue();
        }

        verifyPlacements();
        cleanupOldPlacements();

        if (debugInfo.get()) {
            Block blockToPlace = getBlockToPlace();
            FindItemResult block = findBlockInHotbar(blockToPlace);
            String blockName = blockToPlace != null ? blockToPlace.toString().replace("Block{", "").replace("}", "") : "None";

            String debug = String.format("AztecSurround | Block: %s | Slot: %d | Ping: %dms | Queue: %d | Pending: %d",
                blockName,
                block.found() ? block.slot() : -1,
                getPing(),
                placementQueue.size(),
                pendingPlacements.size()
            );
            info(debug);
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!renderEnabled.get()) return;
        if (mc.player == null || mc.world == null) return;

        BlockPos playerPos = mc.player.getBlockPos();


        Direction[] horizontalDirs = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};


        for (Direction dir : horizontalDirs) {
            BlockPos sidePos = playerPos.offset(dir);

            if (mc.world.getBlockState(sidePos).isAir()) {
                PlacementInfo info = pendingPlacements.get(sidePos);
                if (info != null && info.state == PlacementState.Failed) {
                    renderBlock(event, sidePos, missingColor.get());
                } else if (info != null) {
                    renderBlock(event, sidePos, antiCevColor.get());
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
                if (mc.world.getBlockState(cornerPos).isAir()) {
                    PlacementInfo info = pendingPlacements.get(cornerPos);
                    if (info != null && info.state == PlacementState.Failed) {
                        renderBlock(event, cornerPos, missingColor.get());
                    } else if (info != null) {
                        renderBlock(event, cornerPos, antiCevColor.get());
                    }
                }
            }
        }


        if (antiCivEnabled.get() && doubleCiv.get()) {
            for (Direction dir : horizontalDirs) {
                BlockPos doublePos = playerPos.offset(dir).up();
                if (mc.world.getBlockState(doublePos).isAir()) {
                    PlacementInfo info = pendingPlacements.get(doublePos);
                    if (info != null && info.state == PlacementState.Failed) {
                        renderBlock(event, doublePos, missingColor.get());
                    } else if (info != null) {
                        renderBlock(event, doublePos, antiCevColor.get());
                    }
                }
            }

            BlockPos[] corners = {
                playerPos.north().east(),
                playerPos.north().west(),
                playerPos.south().east(),
                playerPos.south().west()
            };

            for (BlockPos cornerPos : corners) {
                BlockPos doubleCornerPos = cornerPos.up();
                if (mc.world.getBlockState(doubleCornerPos).isAir()) {
                    PlacementInfo info = pendingPlacements.get(doubleCornerPos);
                    if (info != null && info.state == PlacementState.Failed) {
                        renderBlock(event, doubleCornerPos, missingColor.get());
                    } else if (info != null) {
                        renderBlock(event, doubleCornerPos, antiCevColor.get());
                    }
                }
            }
        }


        if (antiCevEnabled.get()) {
            BlockPos topPos = playerPos.up(2);
            if (mc.world.getBlockState(topPos).isAir()) {
                PlacementInfo info = pendingPlacements.get(topPos);
                if (info != null && info.state == PlacementState.Failed) {
                    renderBlock(event, topPos, missingColor.get());
                } else if (info != null) {
                    renderBlock(event, topPos, antiCevColor.get());
                }
            }

            if (doubleCev.get()) {
                BlockPos doubleTopPos = playerPos.up(3);
                if (mc.world.getBlockState(doubleTopPos).isAir()) {
                    PlacementInfo info = pendingPlacements.get(doubleTopPos);
                    if (info != null && info.state == PlacementState.Failed) {
                        renderBlock(event, doubleTopPos, missingColor.get());
                    } else if (info != null) {
                        renderBlock(event, doubleTopPos, antiCevColor.get());
                    }
                }
            }
        }
    }

    private void recalculateSurround() {
        if (mc.player == null || mc.world == null) return;

        BlockPos playerPos = mc.player.getBlockPos();
        Block blockToPlace = getBlockToPlace();

        if (blockToPlace == null) return;


        Direction[] horizontalDirs = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        for (Direction dir : horizontalDirs) {
            BlockPos sidePos = playerPos.offset(dir);
            if (shouldPlaceBlock(sidePos)) {
                addPlacementTask(sidePos, PlacementPriority.Normal, dir.getOpposite(), blockToPlace);
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
                if (shouldPlaceBlock(cornerPos)) {
                    addPlacementTask(cornerPos, PlacementPriority.Normal, Direction.DOWN, blockToPlace);
                }
            }
        }


        if (antiCivEnabled.get() && doubleCiv.get()) {
            for (Direction dir : horizontalDirs) {
                BlockPos doublePos = playerPos.offset(dir).up();
                if (shouldPlaceBlock(doublePos)) {
                    addPlacementTask(doublePos, PlacementPriority.Normal, Direction.DOWN, blockToPlace);
                }
            }

            BlockPos[] corners = {
                playerPos.north().east(),
                playerPos.north().west(),
                playerPos.south().east(),
                playerPos.south().west()
            };

            for (BlockPos cornerPos : corners) {
                BlockPos doubleCornerPos = cornerPos.up();
                if (shouldPlaceBlock(doubleCornerPos)) {
                    addPlacementTask(doubleCornerPos, PlacementPriority.Normal, Direction.DOWN, blockToPlace);
                }
            }
        }


        if (antiCevEnabled.get()) {
            BlockPos topPos = playerPos.up(2);
            if (shouldPlaceBlock(topPos)) {
                addPlacementTask(topPos, PlacementPriority.High, Direction.DOWN, blockToPlace);
            }

            if (doubleCev.get()) {
                BlockPos doubleTopPos = playerPos.up(3);
                if (shouldPlaceBlock(doubleTopPos)) {
                    addPlacementTask(doubleTopPos, PlacementPriority.Normal, Direction.DOWN, blockToPlace);
                }
            }
        }
    }

    private boolean shouldPlaceBlock(BlockPos pos) {
        if (mc.world == null || mc.player == null) return false;

        if (!mc.world.getBlockState(pos).isAir()) {
            return false;
        }

        if (pendingPlacements.containsKey(pos)) {
            PlacementInfo info = pendingPlacements.get(pos);
            if (info.state == PlacementState.Pending || info.state == PlacementState.Confirmed) {
                return false;
            }
        }

        BlockPos playerPos = mc.player.getBlockPos();
        if (pos.equals(playerPos) || pos.equals(playerPos.up())) {
            return false;
        }

        if (!isReachable(pos)) {
            return false;
        }

        return true;
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
            if (distance <= reachDistance) {
                return true;
            }
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

                if (distance <= reachDistance) {
                    return true;
                }
            }
        }

        return false;
    }

    private void addPlacementTask(BlockPos pos, PlacementPriority priority, Direction placeDirection, Block block) {
        boolean alreadyInQueue = placementQueue.stream().anyMatch(task -> task.pos.equals(pos));

        if (pendingPlacements.containsKey(pos)) {
            PlacementInfo info = pendingPlacements.get(pos);
            if (info.state != PlacementState.Failed && info.state != PlacementState.Queued) {
                return;
            }
            info.state = PlacementState.Queued;
            info.expectedBlock = block;
            if (!alreadyInQueue) {
                placementQueue.add(new PlacementTask(pos, priority, placeDirection, block));
            }
        } else {
            placementQueue.add(new PlacementTask(pos, priority, placeDirection, block));
            pendingPlacements.put(pos, new PlacementInfo(PlacementState.Queued, block));
        }
    }

    private void processPlacementQueue() {
        if (placementQueue.isEmpty()) return;

        Block blockToPlace = getBlockToPlace();
        if (blockToPlace == null) return;

        FindItemResult block = findBlockInHotbar(blockToPlace);
        if (!block.found()) {
            if (debugInfo.get()) info("AztecSurround: No block found in hotbar");
            return;
        }

        int placedThisTick = 0;
        while (!placementQueue.isEmpty() && placedThisTick < blocksPerTick.get()) {
            PlacementTask task = placementQueue.poll();
            if (task == null) break;

            if (!shouldPlaceBlock(task.pos)) {
                pendingPlacements.remove(task.pos);
                continue;
            }

            PlacementInfo info = pendingPlacements.get(task.pos);
            if (info == null) continue;

            boolean placed = false;
            if (swapMode.get() == SwapMode.Silent) {
                placed = BlockUtils.place(task.pos, block, rotate.get(), 100, true, true, swapBack.get());
            } else if (swapMode.get() == SwapMode.Normal) {
                if (!block.isMainHand()) {
                    InvUtils.swap(block.slot(), swapBack.get());
                }
                placed = BlockUtils.place(task.pos, block, rotate.get(), 100, true, true, false);
            } else {
                if (block.isMainHand()) {
                    placed = BlockUtils.place(task.pos, block, rotate.get(), 100, true, true, false);
                }
            }

            if (placed) {
                info.state = PlacementState.Pending;

                lastPlaceTick = mc.player.age;
                placedThisTick++;
            } else {
                info.state = PlacementState.Failed;
                info.retryCount++;
            }
        }
    }

    private void rebuildMissing() {
        if (mc.player == null || mc.world == null) return;

        BlockPos playerPos = mc.player.getBlockPos();
        Block blockToPlace = getBlockToPlace();

        if (blockToPlace == null) return;


        Direction[] horizontalDirs = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        for (Direction dir : horizontalDirs) {
            BlockPos sidePos = playerPos.offset(dir);
            if (mc.world.getBlockState(sidePos).isAir()) {
                PlacementInfo info = pendingPlacements.get(sidePos);
                if (info == null || info.state == PlacementState.Failed || info.state == PlacementState.Queued) {
                    addPlacementTask(sidePos, PlacementPriority.High, dir.getOpposite(), blockToPlace);
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
                if (mc.world.getBlockState(cornerPos).isAir()) {
                    PlacementInfo info = pendingPlacements.get(cornerPos);
                    if (info == null || info.state == PlacementState.Failed || info.state == PlacementState.Queued) {
                        addPlacementTask(cornerPos, PlacementPriority.Normal, Direction.DOWN, blockToPlace);
                    }
                }
            }
        }


        if (antiCivEnabled.get() && doubleCiv.get()) {
            for (Direction dir : horizontalDirs) {
                BlockPos doublePos = playerPos.offset(dir).up();
                if (mc.world.getBlockState(doublePos).isAir()) {
                    PlacementInfo info = pendingPlacements.get(doublePos);
                    if (info == null || info.state == PlacementState.Failed || info.state == PlacementState.Queued) {
                        addPlacementTask(doublePos, PlacementPriority.Normal, Direction.DOWN, blockToPlace);
                    }
                }
            }

            BlockPos[] corners = {
                playerPos.north().east(),
                playerPos.north().west(),
                playerPos.south().east(),
                playerPos.south().west()
            };

            for (BlockPos cornerPos : corners) {
                BlockPos doubleCornerPos = cornerPos.up();
                if (mc.world.getBlockState(doubleCornerPos).isAir()) {
                    PlacementInfo info = pendingPlacements.get(doubleCornerPos);
                    if (info == null || info.state == PlacementState.Failed || info.state == PlacementState.Queued) {
                        addPlacementTask(doubleCornerPos, PlacementPriority.Normal, Direction.DOWN, blockToPlace);
                    }
                }
            }
        }


        if (antiCevEnabled.get()) {
            BlockPos topPos = playerPos.up(2);
            if (mc.world.getBlockState(topPos).isAir()) {
                PlacementInfo info = pendingPlacements.get(topPos);
                if (info == null || info.state == PlacementState.Failed || info.state == PlacementState.Queued) {
                    addPlacementTask(topPos, PlacementPriority.Critical, Direction.DOWN, blockToPlace);
                }
            }

            if (doubleCev.get()) {
                BlockPos doubleTopPos = playerPos.up(3);
                if (mc.world.getBlockState(doubleTopPos).isAir()) {
                    PlacementInfo info = pendingPlacements.get(doubleTopPos);
                    if (info == null || info.state == PlacementState.Failed || info.state == PlacementState.Queued) {
                        addPlacementTask(doubleTopPos, PlacementPriority.High, Direction.DOWN, blockToPlace);
                    }
                }
            }
        }
    }

    private void verifyPlacements() {
        if (mc.world == null) return;

        long currentTime = System.currentTimeMillis();

        for (Map.Entry<BlockPos, PlacementInfo> entry : pendingPlacements.entrySet()) {
            BlockPos pos = entry.getKey();
            PlacementInfo info = entry.getValue();

            if (info.state != PlacementState.Pending || currentTime - info.timestamp < 100) {
                continue;
            }

            if (isBlockPlaced(pos, info.expectedBlock)) {
                info.state = PlacementState.Confirmed;
            } else {
                info.state = PlacementState.Failed;
                info.retryCount++;

                if (info.retryCount <= 5) {
                    info.state = PlacementState.Queued;
                }
            }
        }
    }

    private boolean isBlockPlaced(BlockPos pos, Block expectedBlock) {
        if (mc.world == null) return false;
        Block currentBlock = mc.world.getBlockState(pos).getBlock();
        return currentBlock == expectedBlock;
    }

    private void cleanupOldPlacements() {
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<BlockPos, PlacementInfo>> iterator = pendingPlacements.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<BlockPos, PlacementInfo> entry = iterator.next();
            PlacementInfo info = entry.getValue();
            BlockPos pos = entry.getKey();

            boolean shouldRemove = false;

            if (currentTime - info.timestamp > 5000) {
                shouldRemove = true;
            }

            if (info.state == PlacementState.Confirmed) {
                shouldRemove = true;
            }

            if (info.state == PlacementState.Failed && info.retryCount > 5) {
                shouldRemove = true;
            }

            if (shouldRemove) {
                iterator.remove();
                removeTaskFromQueue(pos);
            }
        }
    }

    private void removeTaskFromQueue(BlockPos pos) {
        placementQueue.removeIf(task -> task.pos.equals(pos));
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

    private void renderBlock(Render3DEvent event, BlockPos pos, SettingColor color) {

        double shrink = 0.001;
        Box box = new Box(
            pos.getX() + shrink, pos.getY() + shrink, pos.getZ() + shrink,
            pos.getX() + 1 - shrink, pos.getY() + 1 - shrink, pos.getZ() + 1 - shrink
        );


        int alpha = color.a;
        if (fadeSpeed.get() > 0) {
            PlacementInfo info = pendingPlacements.get(pos);
            if (info != null) {
                long timeSinceCreation = System.currentTimeMillis() - info.timestamp;
                double progress = Math.min(1.0, (double) timeSinceCreation / fadeSpeed.get());
                if (info.state == PlacementState.Queued) {

                    alpha = (int) (color.a * progress);
                } else if (info.state == PlacementState.Pending) {

                    alpha = color.a;
                } else if (info.state == PlacementState.Failed || info.state == PlacementState.Confirmed) {

                    double fadeOutProgress = Math.min(1.0, (double) timeSinceCreation / (fadeSpeed.get() / 3.0));
                    alpha = (int) (color.a * (1.0 - fadeOutProgress));
                    if (alpha <= 0) return;
                }
            }
        }

        SettingColor fadeColor = new SettingColor(color.r, color.g, color.b, alpha);
        event.renderer.box(box, fadeColor, fadeColor, shapeMode.get(), 0);
    }

    private int getPing() {
        if (mc.player == null) return 0;
        if (mc.player.networkHandler == null) return 0;
        var entry = mc.player.networkHandler.getPlayerListEntry(mc.player.getUuid());
        if (entry == null) return 0;
        return entry.getLatency();
    }

    private void centerPlayer() {
        if (mc.player == null || centerMode.get() == CenterMode.None) return;

        BlockPos blockPos = mc.player.getBlockPos();
        double centerX = blockPos.getX() + 0.5;
        double centerZ = blockPos.getZ() + 0.5;

        double currentX = mc.player.getX();
        double currentZ = mc.player.getZ();

        double dx = centerX - currentX;
        double dz = centerZ - currentZ;

        if (Math.abs(dx) < 0.1 && Math.abs(dz) < 0.1) return;

        if (centerMode.get() == CenterMode.Move) {
            if (mc.world.getBlockState(blockPos.down()).isAir()) {
                return;
            }

            double speed = 0.05;
            mc.player.setVelocity(
                Math.signum(dx) * Math.min(Math.abs(dx), speed),
                mc.player.getVelocity().y,
                Math.signum(dz) * Math.min(Math.abs(dz), speed)
            );
        } else if (centerMode.get() == CenterMode.Teleport) {
            mc.player.setPosition(centerX, mc.player.getY(), centerZ);
        }
    }
}
