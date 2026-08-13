package dev.aztec.addon.modules;

import dev.aztec.addon.AddonTemplate;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MobSpawnESP extends Module {

    public enum AreaSize {
        BxB("1x1"),
        THREE("3x3"),
        FOUR("4x4"),
        FIVE("5x5"),
        SIX("6x6");

        private final String displayName;

        AreaSize(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }

        public int getSize() {
            return switch (this) {
                case BxB -> 1;
                case THREE -> 3;
                case FOUR -> 4;
                case FIVE -> 5;
                case SIX -> 6;
            };
        }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("Rango horizontal en bloques.")
        .defaultValue(12)
        .min(1)
        .sliderMax(48)
        .build()
    );

    private final Setting<Integer> verticalRange = sgGeneral.add(new IntSetting.Builder()
        .name("vertical-range")
        .description("Rango vertical arriba/abajo del jugador que se escanea.")
        .defaultValue(12)
        .min(0)
        .sliderMax(64)
        .build()
    );

    private final Setting<Boolean> showPotentialSpawns = sgGeneral.add(new BoolSetting.Builder()
        .name("show-potential-spawns")
        .description("Muestra dónde podrán aparecer mobs de noche aunque sea de día (ignora luz solar).")
        .defaultValue(true)
        .build()
    );

    private final Setting<AreaSize> areaSize = sgGeneral.add(new EnumSetting.Builder<AreaSize>()
        .name("area-size")
        .description("Tamaño del área para agrupar spawns. BxB renderiza bloque por bloque.")
        .defaultValue(AreaSize.BxB)
        .build()
    );

    private final Setting<Boolean> circles = sgGeneral.add(new BoolSetting.Builder()
        .name("circles")
        .description("Dibuja circunferencias. Si está desactivado dibuja cruces.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> circleQuality = sgGeneral.add(new IntSetting.Builder()
        .name("circle-quality")
        .description("Calidad de la circunferencia. Menos = más FPS.")
        .defaultValue(8)
        .min(3)
        .sliderMax(24)
        .build()
    );

    private final Setting<Integer> scanInterval = sgGeneral.add(new IntSetting.Builder()
        .name("scan-interval")
        .description("Cada cuántos ticks se reescanea el área si no te moviste de chunk.")
        .defaultValue(20)
        .min(5)
        .sliderMax(100)
        .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Color de los marcadores de spawn.")
        .defaultValue(new SettingColor(255, 0, 0, 120))
        .build()
    );

    private final Set<BlockPos> spawnSpots = new HashSet<>();
    private final List<RenderGroup> areaGroups = new ArrayList<>();

    private boolean groupsDirty = true;
    private AreaSize lastAreaSize = null;

    private int scanTimer = 0;
    private int lastPlayerChunkX = Integer.MIN_VALUE;
    private int lastPlayerChunkZ = Integer.MIN_VALUE;

    public MobSpawnESP() {
        super(AddonTemplate.CATEGORY, "mob-spawn-esp", "Muestra dónde pueden aparecer mobs.");
    }

    @Override
    public void onActivate() {
        spawnSpots.clear();
        areaGroups.clear();

        groupsDirty = true;
        scanTimer = 0;

        if (mc.player != null) {
            lastPlayerChunkX = mc.player.getBlockX() >> 4;
            lastPlayerChunkZ = mc.player.getBlockZ() >> 4;
        }

        scanNearbyChunks();
    }

    @Override
    public void onDeactivate() {
        spawnSpots.clear();
        areaGroups.clear();
        groupsDirty = true;
        lastAreaSize = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        int playerChunkX = mc.player.getBlockX() >> 4;
        int playerChunkZ = mc.player.getBlockZ() >> 4;

        // Si cambias de chunk, reescanear inmediatamente
        if (playerChunkX != lastPlayerChunkX || playerChunkZ != lastPlayerChunkZ) {
            lastPlayerChunkX = playerChunkX;
            lastPlayerChunkZ = playerChunkZ;
            scanTimer = 0;

            scanNearbyChunks();
            cleanFarSpots();
            return;
        }

        // Si estás quieto, reescanear según scan-interval
        scanTimer++;
        if (scanTimer >= scanInterval.get()) {
            scanTimer = 0;
            scanNearbyChunks();
            cleanFarSpots();
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (mc.world == null || mc.player == null) return;

        BlockPos pos = event.pos;

        int playerX = mc.player.getBlockX();
        int playerZ = mc.player.getBlockZ();

        double dx = pos.getX() + 0.5 - playerX;
        double dz = pos.getZ() + 0.5 - playerZ;

        double maxDistSq = range.get() * range.get();

        // Solo reaccionar si el cambio está dentro del rango horizontal
        if (dx * dx + dz * dz > maxDistSq) return;

        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;

        // Escanear el chunk y vecinos porque la luz puede cruzar bordes de chunk
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                scanChunkAt(chunkX + ox, chunkZ + oz);
            }
        }
    }

    private void scanNearbyChunks() {
        if (mc.world == null || mc.player == null) return;

        int chunkRange = range.get() / 16 + 1;
        ChunkPos playerChunk = new ChunkPos(mc.player.getBlockPos());

        for (int cx = playerChunk.x - chunkRange; cx <= playerChunk.x + chunkRange; cx++) {
            for (int cz = playerChunk.z - chunkRange; cz <= playerChunk.z + chunkRange; cz++) {
                scanChunkAt(cx, cz);
            }
        }
    }

    private void scanChunkAt(int chunkX, int chunkZ) {
        if (mc.world == null) return;

        Chunk chunk = mc.world.getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk != null) {
            scanChunk(chunk);
        }
    }

    private void cleanFarSpots() {
        if (mc.player == null) return;

        int playerX = mc.player.getBlockX();
        int playerZ = mc.player.getBlockZ();

        double maxDistance = range.get() + 16;
        double maxDistanceSq = maxDistance * maxDistance;

        boolean removed = spawnSpots.removeIf(pos -> {
            double dx = pos.getX() + 0.5 - playerX;
            double dz = pos.getZ() + 0.5 - playerZ;
            return dx * dx + dz * dz > maxDistanceSq;
        });

        if (removed) {
            groupsDirty = true;
        }
    }

    private void scanChunk(Chunk chunk) {
        if (chunk == null || mc.world == null || mc.player == null) return;

        ChunkPos chunkPos = chunk.getPos();
        int chunkX = chunkPos.x;
        int chunkZ = chunkPos.z;

        ChunkPos playerChunk = new ChunkPos(mc.player.getBlockPos());

        int chunkRange = range.get() / 16 + 1;

        int chunkDist = Math.max(
            Math.abs(chunkPos.x - playerChunk.x),
            Math.abs(chunkPos.z - playerChunk.z)
        );

        if (chunkDist > chunkRange) return;

        // Limpiar spots viejos de este chunk sin crear objetos ChunkPos
        boolean removed = spawnSpots.removeIf(pos ->
            (pos.getX() >> 4) == chunkX && (pos.getZ() >> 4) == chunkZ
        );

        if (removed) {
            groupsDirty = true;
        }

        int worldBottom = mc.world.getBottomY();
        int worldTop = mc.world.getBottomY() + mc.world.getHeight(); // exclusivo

        int playerX = mc.player.getBlockX();
        int playerY = mc.player.getBlockY();
        int playerZ = mc.player.getBlockZ();

        // AQUÍ se usa verticalRange correctamente
        int minY = Math.max(worldBottom, playerY - verticalRange.get());
        int maxY = Math.min(worldTop - 1, playerY + verticalRange.get());

        if (minY > maxY) return;

        double maxDistSq = range.get() * range.get();

        int startX = chunkPos.getStartX();
        int startZ = chunkPos.getStartZ();

        BlockPos.Mutable mutable = new BlockPos.Mutable();

        boolean added = false;

        for (int x = startX; x < startX + 16; x++) {
            double dx = x + 0.5 - playerX;

            for (int z = startZ; z < startZ + 16; z++) {
                double dz = z + 0.5 - playerZ;

                // Recortar por rango horizontal circular
                if (dx * dx + dz * dz > maxDistSq) continue;

                for (int y = minY; y <= maxY; y++) {
                    mutable.set(x, y, z);

                    if (isValidSpawnSpot(mutable)) {
                        spawnSpots.add(mutable.toImmutable());
                        added = true;
                    }
                }
            }
        }

        if (added) {
            groupsDirty = true;
        }
    }

    private boolean isValidSpawnSpot(BlockPos.Mutable pos) {
        if (mc.world == null) return false;

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        // Primero validar geometría antes de consultar luz (más barato)
        BlockState current = mc.world.getBlockState(pos);
        if (!current.isAir()) return false;

        pos.set(x, y + 1, z);
        BlockState above = mc.world.getBlockState(pos);

        if (!above.isAir()) {
            pos.set(x, y, z);
            return false;
        }

        pos.set(x, y - 1, z);
        BlockState below = mc.world.getBlockState(pos);

        pos.set(x, y, z);

        if (!below.isOpaque()) return false;

        // Ahora sí preguntar al light engine
        int light;

        if (showPotentialSpawns.get()) {
            // Solo luz artificial (antorchas, glowstone, lava, etc.)
            light = mc.world.getLightLevel(LightType.BLOCK, pos);
        } else {
            // Luz combinada (cielo + artificial)
            light = mc.world.getLightLevel(pos);
        }

        return light <= 0;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null) return;

        AreaSize currentSize = areaSize.get();

        if (currentSize.getSize() == 1) {
            renderSingleBlocks(event);
            lastAreaSize = currentSize;
            return;
        }

        if (groupsDirty || currentSize != lastAreaSize) {
            rebuildAreaGroups(currentSize);
        }

        renderAreaGroups(event);
    }

    private void renderSingleBlocks(Render3DEvent event) {
        if (spawnSpots.isEmpty()) return;

        int playerX = mc.player.getBlockX();
        int playerZ = mc.player.getBlockZ();

        double maxDistSq = range.get() * range.get();

        for (BlockPos pos : spawnSpots) {
            double dx = pos.getX() + 0.5 - playerX;
            double dz = pos.getZ() + 0.5 - playerZ;

            // Distancia SOLO horizontal: saltar no debería afectar el render
            if (dx * dx + dz * dz > maxDistSq) continue;

            if (circles.get()) {
                renderCircle(
                    event,
                    pos.getX() + 0.5,
                    pos.getY() + 0.01,
                    pos.getZ() + 0.5,
                    0.42,
                    color.get()
                );
            } else {
                renderCross(
                    event,
                    pos.getX() + 0.5,
                    pos.getY() + 0.01,
                    pos.getZ() + 0.5,
                    0.3,
                    color.get()
                );
            }
        }
    }

    private void renderAreaGroups(Render3DEvent event) {
        if (areaGroups.isEmpty()) return;

        int playerX = mc.player.getBlockX();
        int playerZ = mc.player.getBlockZ();

        double maxDistSq = range.get() * range.get();

        for (RenderGroup group : areaGroups) {
            double dx = group.x - playerX;
            double dz = group.z - playerZ;

            // Distancia SOLO horizontal
            if (dx * dx + dz * dz > maxDistSq) continue;

            if (circles.get()) {
                renderCircle(event, group.x, group.y, group.z, group.radius, color.get());
            } else {
                double crossSize = group.area ? group.radius * 0.7 : 0.3;
                renderCross(event, group.x, group.y, group.z, crossSize, color.get());
            }
        }
    }

    private void rebuildAreaGroups(AreaSize sizeEnum) {
        areaGroups.clear();

        int size = sizeEnum.getSize();

        Set<Long> processedAreas = new HashSet<>();
        BlockPos.Mutable check = new BlockPos.Mutable();

        for (BlockPos pos : spawnSpots) {
            int y = pos.getY();

            int areaX = Math.floorDiv(pos.getX(), size);
            int areaZ = Math.floorDiv(pos.getZ(), size);

            long key = packAreaKey(areaX, y, areaZ);

            if (!processedAreas.add(key)) continue;

            int startX = areaX * size;
            int startZ = areaZ * size;

            boolean allSpawns = true;

            outer:
            for (int dx = 0; dx < size; dx++) {
                for (int dz = 0; dz < size; dz++) {
                    check.set(startX + dx, y, startZ + dz);

                    if (!spawnSpots.contains(check)) {
                        allSpawns = false;
                        break outer;
                    }
                }
            }

            if (allSpawns) {
                double centerX = startX + (size / 2.0);
                double centerZ = startZ + (size / 2.0);
                double radius = size / 2.0 - 0.1;

                areaGroups.add(new RenderGroup(centerX, y + 0.01, centerZ, radius, true));
            } else {
                for (int dx = 0; dx < size; dx++) {
                    for (int dz = 0; dz < size; dz++) {
                        check.set(startX + dx, y, startZ + dz);

                        if (spawnSpots.contains(check)) {
                            areaGroups.add(new RenderGroup(
                                startX + dx + 0.5,
                                y + 0.01,
                                startZ + dz + 0.5,
                                0.42,
                                false
                            ));
                        }
                    }
                }
            }
        }

        groupsDirty = false;
        lastAreaSize = sizeEnum;
    }

    // Empaqueta areaX, Y, areaZ en un long para evitar usar Strings
    private static long packAreaKey(int areaX, int y, int areaZ) {
        long x = (areaX + 33554432L) & 0x3FFFFFFL; // 26 bits
        long z = (areaZ + 33554432L) & 0x3FFFFFFL; // 26 bits
        long yy = (y + 2048L) & 0xFFFL;             // 12 bits

        return (x << 38) | (z << 12) | yy;
    }

    private void renderCircle(Render3DEvent event, double x, double y, double z, double radius, SettingColor color) {
        if (radius <= 0) return;

        int segments = Math.max(3, circleQuality.get());
        double step = Math.PI * 2 / segments;

        double lastX = x + radius;
        double lastZ = z;

        for (int i = 1; i <= segments; i++) {
            double angle = i * step;
            double newX = x + radius * Math.cos(angle);
            double newZ = z + radius * Math.sin(angle);

            event.renderer.line(lastX, y, lastZ, newX, y, newZ, color);

            lastX = newX;
            lastZ = newZ;
        }
    }

    private void renderCross(Render3DEvent event, double x, double y, double z, double size, SettingColor color) {
        event.renderer.line(x - size, y, z, x + size, y, z, color);
        event.renderer.line(x, y, z - size, x, y, z + size, color);
    }

    private static class RenderGroup {
        final double x;
        final double y;
        final double z;
        final double radius;
        final boolean area;

        RenderGroup(double x, double y, double z, double radius, boolean area) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.area = area;
        }
    }
}
