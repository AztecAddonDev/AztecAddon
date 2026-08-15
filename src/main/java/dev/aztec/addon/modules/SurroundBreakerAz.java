package dev.aztec.addon.modules;

import dev.aztec.addon.AddonTemplate;
import dev.aztec.addon.mixin.IClientPlayerInteractionManager;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SurroundBreakerAz extends Module {

    public enum BreakMode {
        Normal("Normal"),
        Packet("Packet");

        private final String displayName;
        BreakMode(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
    }

    public enum RebreakMode {
        Instant("Instant"),
        Fast("Fast"),
        Normal("Normal");

        private final String displayName;
        RebreakMode(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
    }

    public enum SwitchMode {
        Normal("Normal"),
        None("None");

        private final String displayName;
        SwitchMode(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBreak = settings.createGroup("Break");
    private final SettingGroup sgRebreak = settings.createGroup("Rebreak");
    private final SettingGroup sgSmart = settings.createGroup("Smart");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("Maximum range to target players.")
        .defaultValue(5)
        .min(3).max(8).sliderMax(6)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotate towards the block when breaking.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SwitchMode> switchMode = sgGeneral.add(new EnumSetting.Builder<SwitchMode>()
        .name("switch-mode")
        .description("How to switch to pickaxe.")
        .defaultValue(SwitchMode.Normal)
        .build()
    );

    private final Setting<BreakMode> breakMode = sgBreak.add(new EnumSetting.Builder<BreakMode>()
        .name("break-mode")
        .description("Normal = vanilla mining. Packet = packet-based mining.")
        .defaultValue(BreakMode.Packet)
        .build()
    );

    private final Setting<RebreakMode> rebreakMode = sgRebreak.add(new EnumSetting.Builder<RebreakMode>()
        .name("rebreak-mode")
        .description("Instant = break immediately when placed. Fast = accelerated mining. Normal = vanilla mining.")
        .defaultValue(RebreakMode.Instant)
        .build()
    );

    private final Setting<Integer> fastSpeed = sgRebreak.add(new IntSetting.Builder()
        .name("fast-speed")
        .description("Extra ticks to add in Fast mode. Higher = slower but safer.")
        .defaultValue(3)
        .min(1).max(10).sliderMax(10)
        .visible(() -> rebreakMode.get() == RebreakMode.Fast)
        .build()
    );

    private final Setting<Integer> rebreakDelay = sgRebreak.add(new IntSetting.Builder()
        .name("rebreak-delay")
        .description("Ticks between instant rebreak attempts.")
        .defaultValue(0)
        .min(0).max(20).sliderMax(20)
        .visible(() -> rebreakMode.get() == RebreakMode.Instant)
        .build()
    );

    private final Setting<Boolean> autoSmart = sgSmart.add(new BoolSetting.Builder()
        .name("auto-smart")
        .description("Only break blocks with obsidian below (for crystal placement).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> antiCev = sgSmart.add(new BoolSetting.Builder()
        .name("anti-cev")
        .description("Also target the block above head.")
        .defaultValue(false)
        .build()
    );

    // ── RENDER SETTINGS ─────────────────────────────────────────────
    private final Setting<Boolean> renderEnabled = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Enable block rendering.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shape is rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> targetSideColor = sgRender.add(new ColorSetting.Builder()
        .name("target-side-color")
        .description("Side color for the target block (before first break).")
        .defaultValue(new SettingColor(255, 50, 50, 45))
        .visible(renderEnabled::get)
        .build()
    );

    private final Setting<SettingColor> targetLineColor = sgRender.add(new ColorSetting.Builder()
        .name("target-line-color")
        .description("Line color for the target block (before first break).")
        .defaultValue(new SettingColor(255, 50, 50, 200))
        .visible(renderEnabled::get)
        .build()
    );

    private final Setting<SettingColor> rebreakSideColor = sgRender.add(new ColorSetting.Builder()
        .name("rebreak-side-color")
        .description("Side color when waiting to rebreak.")
        .defaultValue(new SettingColor(255, 180, 30, 45))
        .visible(renderEnabled::get)
        .build()
    );

    private final Setting<SettingColor> rebreakLineColor = sgRender.add(new ColorSetting.Builder()
        .name("rebreak-line-color")
        .description("Line color when waiting to rebreak.")
        .defaultValue(new SettingColor(255, 180, 30, 200))
        .visible(renderEnabled::get)
        .build()
    );

    private final Setting<SettingColor> readySideColor = sgRender.add(new ColorSetting.Builder()
        .name("ready-side-color")
        .description("Side color when the block is ready to break instantly.")
        .defaultValue(new SettingColor(50, 255, 80, 60))
        .visible(renderEnabled::get)
        .build()
    );

    private final Setting<SettingColor> readyLineColor = sgRender.add(new ColorSetting.Builder()
        .name("ready-line-color")
        .description("Line color when the block is ready to break instantly.")
        .defaultValue(new SettingColor(50, 255, 80, 220))
        .visible(renderEnabled::get)
        .build()
    );

    private final Setting<Boolean> showProgress = sgRender.add(new BoolSetting.Builder()
        .name("show-progress")
        .description("Show the breaking progress overlay on the block.")
        .defaultValue(true)
        .visible(renderEnabled::get)
        .build()
    );

    private final Setting<SettingColor> progressColor = sgRender.add(new ColorSetting.Builder()
        .name("progress-color")
        .description("Color of the progress bar overlay.")
        .defaultValue(new SettingColor(255, 255, 255, 120))
        .visible(() -> renderEnabled.get() && showProgress.get())
        .build()
    );

    private PlayerEntity currentTarget = null;
    private BlockPos lockedBlock = null;
    private boolean hasMinedOnce = false;
    private boolean isCurrentlyBreaking = false;
    private boolean sentFirstBreakPackets = false;
    private int rebreakTicks = 0;
    private int maxRebreakTicks = 0;
    private boolean waitingForBlock = false;
    private int instantRebreakTicks = 0;

    public SurroundBreakerAz() {
        super(AddonTemplate.CATEGORY, "surround-breaker-az", "Breaks enemy surround blocks. Locks onto one block for instant rebreak.");
    }

    @Override
    public void onActivate() {
        currentTarget = null;
        lockedBlock = null;
        hasMinedOnce = false;
        isCurrentlyBreaking = false;
        sentFirstBreakPackets = false;
        rebreakTicks = 0;
        maxRebreakTicks = 0;
        waitingForBlock = false;
        instantRebreakTicks = 0;
    }

    @Override
    public void onDeactivate() {
        if (isCurrentlyBreaking && mc.interactionManager != null) {
            mc.interactionManager.cancelBlockBreaking();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        currentTarget = findTarget();

        if (currentTarget == null) {
            resetAll();
            return;
        }

        if (lockedBlock == null) {
            lockedBlock = findBestSurroundBlock(currentTarget);
            if (lockedBlock == null) {
                return;
            }
            hasMinedOnce = false;
            waitingForBlock = false;
        }

        double distToBlock = mc.player.getEyePos().distanceTo(
            new net.minecraft.util.math.Vec3d(
                lockedBlock.getX() + 0.5,
                lockedBlock.getY() + 0.5,
                lockedBlock.getZ() + 0.5
            )
        );

        if (distToBlock > 4.5) {
            lockedBlock = null;
            hasMinedOnce = false;
            waitingForBlock = false;
            return;
        }

        BlockState state = mc.world.getBlockState(lockedBlock);

        if (!hasMinedOnce) {
            handleFirstBreak(state);
        } else {
            handleRebreak(state);
        }
    }

    private void ensurePickaxeEquipped(FindItemResult pickaxe) {
        if (switchMode.get() == SwitchMode.None) return;

        if (switchMode.get() == SwitchMode.Normal) {
            if (mc.player.getInventory().getSelectedSlot() != pickaxe.slot()) {
                InvUtils.swap(pickaxe.slot(), false);
            }
        }
    }

    private void handleFirstBreak(BlockState state) {
        FindItemResult pickaxe = findPickaxe();

        if (pickaxe.found() && pickaxe.isHotbar()) {
            ensurePickaxeEquipped(pickaxe);
        }

        if (state.isAir()) {
            onBlockMined();
            return;
        }

        if (breakMode.get() == BreakMode.Packet) {
            if (!sentFirstBreakPackets) {
                minePacket(lockedBlock);
                sentFirstBreakPackets = true;
            } else {
                rebreakTicks--;
                if (rebreakTicks <= 0) {
                    onBlockMined();
                }
            }
        } else {
            mineVanilla(lockedBlock);
        }

        if (mc.world.getBlockState(lockedBlock).isAir()) {
            onBlockMined();
        }
    }

    private void onBlockMined() {
        hasMinedOnce = true;
        isCurrentlyBreaking = false;
        sentFirstBreakPackets = false;
        rebreakTicks = 0;
        maxRebreakTicks = 0;
        waitingForBlock = true;
        instantRebreakTicks = 0;
    }

    private void handleRebreak(BlockState state) {
        if (lockedBlock == null) return;

        if (waitingForBlock) {
            if (state.isAir()) {
                return;
            }
            waitingForBlock = false;
        }

        if (state.isAir()) {
            resetRebreakState();
            waitingForBlock = true;
            return;
        }

        switch (rebreakMode.get()) {
            case Instant -> handleInstantRebreak(state);
            case Fast -> handleFastRebreak(state);
            case Normal -> handleNormalRebreak();
        }
    }

    private void handleInstantRebreak(BlockState state) {
        if (instantRebreakTicks > 0) {
            instantRebreakTicks--;
            return;
        }

        instantRebreakTicks = rebreakDelay.get();

        FindItemResult pickaxe = findPickaxe();
        if (pickaxe.found() && pickaxe.isHotbar()) {
            ensurePickaxeEquipped(pickaxe);
        }

        if (rotate.get()) {
            Rotations.rotate(
                Rotations.getYaw(lockedBlock),
                Rotations.getPitch(lockedBlock),
                this::sendInstantRebreakPacket
            );
        } else {
            sendInstantRebreakPacket();
        }
    }

    private void handleFastRebreak(BlockState state) {
        FindItemResult pickaxe = findPickaxe();
        if (pickaxe.found() && pickaxe.isHotbar()) {
            ensurePickaxeEquipped(pickaxe);
        }

        if (!sentFirstBreakPackets) {
            sendStartPacket(lockedBlock);
            sentFirstBreakPackets = true;

            int slotToUse = pickaxe.found() ? pickaxe.slot() : mc.player.getInventory().getSelectedSlot();
            double delta = BlockUtils.getBreakDelta(slotToUse, state);
            rebreakTicks = (int) Math.ceil(1.0 / delta) + fastSpeed.get();
            maxRebreakTicks = rebreakTicks;
        } else {
            rebreakTicks--;
            if (rebreakTicks <= 0) {
                sendStopPacket(lockedBlock);
                sentFirstBreakPackets = false;
                rebreakTicks = 1;
                maxRebreakTicks = 1;
            }
        }
    }

    private void handleNormalRebreak() {
        FindItemResult pickaxe = findPickaxe();
        if (pickaxe.found() && pickaxe.isHotbar()) {
            ensurePickaxeEquipped(pickaxe);
        }

        if (rotate.get()) {
            Rotations.rotate(
                Rotations.getYaw(lockedBlock),
                Rotations.getPitch(lockedBlock),
                () -> {
                    mc.interactionManager.updateBlockBreakingProgress(lockedBlock, Direction.UP);
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
            );
        } else {
            mc.interactionManager.updateBlockBreakingProgress(lockedBlock, Direction.UP);
            mc.player.swingHand(Hand.MAIN_HAND);
        }

        isCurrentlyBreaking = true;

        if (mc.world.getBlockState(lockedBlock).isAir()) {
            isCurrentlyBreaking = false;
        }
    }

    private void sendInstantRebreakPacket() {
        Direction dir = Direction.UP;
        IClientPlayerInteractionManager im = (IClientPlayerInteractionManager) mc.interactionManager;

        im.invokeSendSequencedPacket(mc.world, sequence ->
            new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                lockedBlock,
                dir,
                sequence
            )
        );

        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void minePacket(BlockPos pos) {
        sendStartPacket(pos);
        sendStopPacket(pos);

        FindItemResult pickaxe = findPickaxe();
        BlockState state = mc.world.getBlockState(pos);

        int slotToUse = pickaxe.found() ? pickaxe.slot() : mc.player.getInventory().getSelectedSlot();
        double delta = BlockUtils.getBreakDelta(slotToUse, state);
        rebreakTicks = (int) Math.ceil(1.0 / delta) + 1;
        maxRebreakTicks = rebreakTicks;
    }

    private void mineVanilla(BlockPos pos) {
        if (rotate.get()) {
            Rotations.rotate(
                Rotations.getYaw(pos),
                Rotations.getPitch(pos),
                () -> {
                    mc.interactionManager.updateBlockBreakingProgress(pos, Direction.UP);
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
            );
        } else {
            mc.interactionManager.updateBlockBreakingProgress(pos, Direction.UP);
            mc.player.swingHand(Hand.MAIN_HAND);
        }

        isCurrentlyBreaking = true;
    }

    private void resetRebreakState() {
        rebreakTicks = 0;
        maxRebreakTicks = 0;
        sentFirstBreakPackets = false;
        instantRebreakTicks = 0;
    }

    private void sendStartPacket(BlockPos pos) {
        Direction dir = Direction.UP;
        IClientPlayerInteractionManager im = (IClientPlayerInteractionManager) mc.interactionManager;
        im.invokeSendSequencedPacket(mc.world, sequence ->
            new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, dir, sequence)
        );
    }

    private void sendStopPacket(BlockPos pos) {
        Direction dir = Direction.UP;
        IClientPlayerInteractionManager im = (IClientPlayerInteractionManager) mc.interactionManager;
        im.invokeSendSequencedPacket(mc.world, sequence ->
            new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, dir, sequence)
        );
    }

    private FindItemResult findPickaxe() {
        return InvUtils.findInHotbar(itemStack ->
            itemStack.getItem() == Items.NETHERITE_PICKAXE ||
                itemStack.getItem() == Items.DIAMOND_PICKAXE
        );
    }

    private PlayerEntity findTarget() {
        List<PlayerEntity> targets = new ArrayList<>();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (player.isDead()) continue;

            double distance = mc.player.distanceTo(player);
            if (distance > range.get()) continue;

            targets.add(player);
        }

        if (targets.isEmpty()) return null;

        targets.sort(Comparator.comparingDouble(mc.player::distanceTo));
        return targets.get(0);
    }

    private BlockPos findBestSurroundBlock(PlayerEntity target) {
        BlockPos targetPos = target.getBlockPos();
        List<BlockPos> candidates = new ArrayList<>();

        Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

        for (Direction dir : directions) {
            BlockPos checkPos = targetPos.offset(dir);
            if (isValidBreakTarget(checkPos)) {
                candidates.add(checkPos);
            }
        }

        if (antiCev.get()) {
            BlockPos aboveHead = targetPos.up(2);
            if (isValidBreakTarget(aboveHead)) {
                candidates.add(aboveHead);
            }
        }

        if (candidates.isEmpty()) return null;

        if (autoSmart.get()) {
            for (BlockPos pos : candidates) {
                BlockPos below = pos.down();
                if (mc.world.getBlockState(below).getBlock() == Blocks.OBSIDIAN) {
                    return pos;
                }
            }
        }

        return candidates.get(0);
    }

    private boolean isValidBreakTarget(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);

        if (state.getBlock() != Blocks.OBSIDIAN &&
            state.getBlock() != Blocks.CRYING_OBSIDIAN) {
            return false;
        }

        double distance = mc.player.getEyePos().distanceTo(
            new net.minecraft.util.math.Vec3d(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5
            )
        );

        return distance <= 4.5;
    }

    private void resetAll() {
        if (isCurrentlyBreaking && mc.interactionManager != null) {
            mc.interactionManager.cancelBlockBreaking();
        }

        currentTarget = null;
        lockedBlock = null;
        hasMinedOnce = false;
        isCurrentlyBreaking = false;
        sentFirstBreakPackets = false;
        rebreakTicks = 0;
        maxRebreakTicks = 0;
        waitingForBlock = false;
        instantRebreakTicks = 0;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!renderEnabled.get()) return;
        if (mc.player == null || mc.world == null) return;
        if (lockedBlock == null) return;

        SettingColor sideColor;
        SettingColor lineColor;
        boolean isReady = false;

        if (!hasMinedOnce) {
            sideColor = targetSideColor.get();
            lineColor = targetLineColor.get();
        } else if (waitingForBlock) {
            sideColor = rebreakSideColor.get();
            lineColor = rebreakLineColor.get();
        } else {
            sideColor = readySideColor.get();
            lineColor = readyLineColor.get();
            isReady = true;
        }

        event.renderer.box(lockedBlock, sideColor, lineColor, shapeMode.get(), 0);

        if (showProgress.get() && !waitingForBlock && !isReady) {
            double progress = calculateProgress();
            if (progress > 0.0 && progress < 1.0) {
                renderProgressBar(event, lockedBlock, progress);
            }
        }
    }

    private double calculateProgress() {
        if (!hasMinedOnce) {
            if (breakMode.get() == BreakMode.Packet) {
                if (maxRebreakTicks <= 0) return 0.0;
                return 1.0 - ((double) rebreakTicks / (double) maxRebreakTicks);
            } else {
                if (mc.interactionManager != null && isCurrentlyBreaking) {
                    IClientPlayerInteractionManager im = (IClientPlayerInteractionManager) mc.interactionManager;
                    return im.getCurrentBreakingProgress();
                }
                return 0.0;
            }
        } else {
            switch (rebreakMode.get()) {
                case Instant:
                    return 1.0;
                case Fast:
                    if (maxRebreakTicks <= 0) return 0.0;
                    return 1.0 - ((double) rebreakTicks / (double) maxRebreakTicks);
                case Normal:
                    if (mc.interactionManager != null && isCurrentlyBreaking) {
                        IClientPlayerInteractionManager im = (IClientPlayerInteractionManager) mc.interactionManager;
                        return im.getCurrentBreakingProgress();
                    }
                    return 0.0;
            }
        }
        return 0.0;
    }

    private void renderProgressBar(Render3DEvent event, BlockPos pos, double progress) {
        double x1 = pos.getX();
        double y1 = pos.getY();
        double z1 = pos.getZ();
        double x2 = pos.getX() + 1;
        double y2 = pos.getY() + progress;
        double z2 = pos.getZ() + 1;

        SettingColor color = progressColor.get();

        event.renderer.box(x1, y1, z1, x2, y2, z2, color, color, ShapeMode.Sides, 0);

        if (progress > 0.05) {
            SettingColor topColor = new SettingColor(
                Math.min(255, color.r + 50),
                Math.min(255, color.g + 50),
                Math.min(255, color.b + 50),
                Math.min(255, color.a + 40)
            );
            event.renderer.box(x1, y2 - 0.02, z1, x2, y2, z2, topColor, topColor, ShapeMode.Lines, 0);
        }
    }
}
