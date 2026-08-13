package dev.aztec.addon.modules;

import dev.aztec.addon.AddonTemplate;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
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

import java.util.HashSet;
import java.util.Set;

public class MobSpawnESP extends Module {
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
        .description("Rango vertical alrededor del jugador para escanear.")
        .defaultValue(16)
        .min(0)
        .sliderMax(64)
        .build()
    );

    private final Setting<Boolean> showPotentialSpawns = sgGeneral.add(new BoolSetting.Builder()
        .name("show-potential-spawns")
        .description("Muestra dónde podrán aparecer mobs de noche aunque sea de día (ignora la luz solar).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> circles = sgGeneral.add(new BoolSetting.Builder()
        .name("circles")
        .description("Dibuja circunferencias. Si está desactivado dibuja cruces.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Color de los marcadores de spawn.")
        .defaultValue(new SettingColor(255, 0, 0, 120))
        .build()
    );

    private final Set<BlockPos> spawnSpots = new HashSet<>();
    private int scanTimer = 0;

    public MobSpawnESP() {
        // IMPORTANTE:
        // Si tu addon no usa AddonTemplate.CATEGORY,
        // reemplázalo por la categoría que estés usando en tus otros módulos.
        super(AddonTemplate.CATEGORY, "mob-spawn-esp", "Muestra dónde pueden aparecer mobs.");
    }

    @Override
    public void onActivate() {
        spawnSpots.clear();
        scanTimer = 0;
        scanNearbyChunks();
    }

    @Override
    public void onDeactivate() {
        spawnSpots.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        scanTimer++;

        // Escanear cada 10 ticks para detectar chunks cargados y cambios cercanos
        if (scanTimer >= 10) {
            scanTimer = 0;
            scanNearbyChunks();
            cleanFarSpots();
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (mc.world == null || mc.player == null) return;

        BlockPos pos = event.pos;

        if (mc.player.squaredDistanceTo(pos.getX(), pos.getY(), pos.getZ()) <= range.get() * range.get()) {
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;

            Chunk chunk = mc.world.getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
            if (chunk != null) {
                scanChunk(chunk);
            }
        }
    }

    private void scanNearbyChunks() {
        if (mc.world == null || mc.player == null) return;

        int chunkRange = range.get() / 16 + 1;
        ChunkPos playerChunk = new ChunkPos(mc.player.getBlockPos());

        for (int cx = playerChunk.x - chunkRange; cx <= playerChunk.x + chunkRange; cx++) {
            for (int cz = playerChunk.z - chunkRange; cz <= playerChunk.z + chunkRange; cz++) {
                Chunk chunk = mc.world.getChunkManager().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk != null) {
                    scanChunk(chunk);
                }
            }
        }
    }

    private void cleanFarSpots() {
        if (mc.player == null) return;

        double maxDistance = range.get() + 16;
        double maxDistanceSq = maxDistance * maxDistance;

        spawnSpots.removeIf(pos ->
            mc.player.squaredDistanceTo(pos.getX(), pos.getY(), pos.getZ()) > maxDistanceSq
        );
    }

    private void scanChunk(Chunk chunk) {
        if (chunk == null || mc.world == null || mc.player == null) return;

        ChunkPos chunkPos = chunk.getPos();
        ChunkPos playerChunk = new ChunkPos(mc.player.getBlockPos());

        int chunkRange = range.get() / 16 + 1;

        int chunkDist = Math.max(
            Math.abs(chunkPos.x - playerChunk.x),
            Math.abs(chunkPos.z - playerChunk.z)
        );

        if (chunkDist > chunkRange) return;

        // Limpiar spots viejos de este chunk
        spawnSpots.removeIf(pos -> chunkPos.equals(new ChunkPos(pos)));

        int worldBottom = mc.world.getBottomY();
        int worldTop = mc.world.getBottomY() + mc.world.getHeight(); // exclusivo

        int playerY = mc.player.getBlockY();

        int minY = Math.max(worldBottom, playerY - verticalRange.get());
        int maxY = Math.min(worldTop - 1, playerY + verticalRange.get());

        if (minY > maxY) return;

        int startX = chunkPos.getStartX();
        int startZ = chunkPos.getStartZ();

        for (int x = startX; x < startX + 16; x++) {
            for (int z = startZ; z < startZ + 16; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);

                    if (isValidSpawnSpot(pos)) {
                        spawnSpots.add(pos);
                    }
                }
            }
        }
    }

    private boolean isValidSpawnSpot(BlockPos pos) {
        if (mc.world == null) return false;

        int light;

        if (showPotentialSpawns.get()) {
            // Modo día/noche:
            // Solo revisamos la luz de bloque (antorchas, lava, etc.)
            // Si block light es 0, de noche será spawn aunque ahora sea de día.
            light = mc.world.getLightLevel(LightType.BLOCK, pos);
        } else {
            // Modo actual:
            // Revisa la luz combinada (cielo + bloques).
            light = mc.world.getLightLevel(pos);
        }

        if (light > 0) return false;

        BlockState below = mc.world.getBlockState(pos.down());
        if (!below.isOpaque()) return false;

        BlockState current = mc.world.getBlockState(pos);
        if (!current.isAir()) return false;

        BlockState above = mc.world.getBlockState(pos.up());
        return above.isAir();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (spawnSpots.isEmpty() || mc.player == null) return;

        double maxDistanceSq = range.get() * range.get();

        for (BlockPos pos : spawnSpots) {
            if (mc.player.squaredDistanceTo(pos.getX(), pos.getY(), pos.getZ()) > maxDistanceSq) {
                continue;
            }

            if (circles.get()) {
                renderCircle(event, pos, color.get());
            } else {
                renderCross(event, pos, color.get());
            }
        }
    }

    private void renderCircle(Render3DEvent event, BlockPos pos, SettingColor color) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.01;
        double z = pos.getZ() + 0.5;

        double radius = 0.42;
        int segments = 14;
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

    private void renderCross(Render3DEvent event, BlockPos pos, SettingColor color) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.01;
        double z = pos.getZ() + 0.5;
        double size = 0.3;

        event.renderer.line(x - size, y, z, x + size, y, z, color);
        event.renderer.line(x, y, z - size, x, y, z + size, color);
    }
}
