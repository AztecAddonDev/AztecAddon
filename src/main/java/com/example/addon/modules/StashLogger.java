package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.utils.DiscordWebhook;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class StashLogger extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");
    private final SettingGroup sgDiscord = settings.createGroup("Discord");
    private final SettingGroup sgFilter = settings.createGroup("Filter");

    private final Setting<Boolean> autoLog = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-log")
        .description("Automatically log containers when opened.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> savePath = sgGeneral.add(new StringSetting.Builder()
        .name("save-path")
        .description("Path to save stash logs (relative to Minecraft folder).")
        .defaultValue("aztec-stashes")
        .build()
    );

    private final Setting<Integer> minimumItems = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-items")
        .description("Minimum number of non-empty slots to log a container.")
        .defaultValue(3)
        .min(1)
        .max(54)
        .build()
    );

    private final Setting<Boolean> sendToDiscord = sgDiscord.add(new BoolSetting.Builder()
        .name("send-to-discord")
        .description("Send stash logs to Discord webhook.")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> webhookUrl = sgDiscord.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL.")
        .defaultValue("")
        .visible(sendToDiscord::get)
        .build()
    );

    private final Setting<Boolean> includeScreenshots = sgDiscord.add(new BoolSetting.Builder()
        .name("include-screenshots")
        .description("Include screenshot of the container when sending to Discord.")
        .defaultValue(true)
        .visible(sendToDiscord::get)
        .build()
    );

    private final Setting<List<String>> blacklistItems = sgFilter.add(new StringListSetting.Builder()
        .name("blacklist-items")
        .description("Items that will prevent logging if present.")
        .defaultValue(List.of())
        .build()
    );

    private final Setting<Boolean> ignoreEnderChests = sgFilter.add(new BoolSetting.Builder()
        .name("ignore-ender-chests")
        .description("Don't log ender chests.")
        .defaultValue(true)
        .build()
    );

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, List<StashRecord>> stashDatabase = new HashMap<>();

    public StashLogger() {
        super(AddonTemplate.CATEGORY, "stash-logger", "Automatically logs container contents when opened.");
    }

    @Override
    public void onActivate() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path modDataDir = configDir.getParent().resolve(savePath.get());
        info("StashLogger activated - Data directory: " + modDataDir.toString());
        loadDatabase();
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (!autoLog.get()) return;

        if (event.screen instanceof GenericContainerScreen containerScreen) {
            new Thread(() -> {
                try {
                    Thread.sleep(100);
                    processContainer(containerScreen);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }

        if (event.screen instanceof ShulkerBoxScreen shulkerScreen) {
            new Thread(() -> {
                try {
                    Thread.sleep(100);
                    processShulkerBox(shulkerScreen);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    private void processContainer(GenericContainerScreen screen) {
        if (mc.player == null || mc.world == null) return;

        BlockPos playerPos = mc.player.getBlockPos();
        String dimension = mc.world.getRegistryKey().getValue().toString();
        LocalDateTime timestamp = LocalDateTime.now();

        List<ItemRecord> items = new ArrayList<>();
        int nonEmptySlots = 0;

        for (Slot slot : screen.getScreenHandler().slots) {
            if (slot.id >= screen.getScreenHandler().getRows() * 9) continue;

            ItemStack stack = slot.getStack();
            if (!stack.isEmpty()) {
                nonEmptySlots++;
                items.add(new ItemRecord(
                    stack.getItem().getName().getString(),
                    stack.getCount(),
                    stack.getItem().toString()
                ));
            }
        }

        if (nonEmptySlots < minimumItems.get()) return;
        if (containsBlacklistedItem(items)) return;

        StashRecord record = new StashRecord(
            timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            playerPos.getX(),
            playerPos.getY(),
            playerPos.getZ(),
            dimension,
            items,
            nonEmptySlots
        );

        String key = dimension + "_" + playerPos.getX() + "_" + playerPos.getZ();
        stashDatabase.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
        saveDatabase();

        if (sendToDiscord.get() && !webhookUrl.get().isEmpty()) {
            byte[] screenshot = null;
            if (includeScreenshots.get()) {
                screenshot = captureScreenshot();
            }
            sendToDiscordWebhook(record, screenshot);
        }

        info("Stash logged: " + nonEmptySlots + " items at " + playerPos.toShortString());
    }

    private void processShulkerBox(ShulkerBoxScreen screen) {
        if (mc.player == null || mc.world == null) return;

        BlockPos playerPos = mc.player.getBlockPos();
        String dimension = mc.world.getRegistryKey().getValue().toString();
        LocalDateTime timestamp = LocalDateTime.now();

        List<ItemRecord> items = new ArrayList<>();
        int nonEmptySlots = 0;

        for (Slot slot : screen.getScreenHandler().slots) {
            if (slot.id >= 27) continue;

            ItemStack stack = slot.getStack();
            if (!stack.isEmpty()) {
                nonEmptySlots++;
                items.add(new ItemRecord(
                    stack.getItem().getName().getString(),
                    stack.getCount(),
                    stack.getItem().toString()
                ));
            }
        }

        if (nonEmptySlots < minimumItems.get()) return;
        if (containsBlacklistedItem(items)) return;

        StashRecord record = new StashRecord(
            timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            playerPos.getX(),
            playerPos.getY(),
            playerPos.getZ(),
            dimension,
            items,
            nonEmptySlots
        );

        String key = dimension + "_" + playerPos.getX() + "_" + playerPos.getZ();
        stashDatabase.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
        saveDatabase();

        if (sendToDiscord.get() && !webhookUrl.get().isEmpty()) {
            byte[] screenshot = null;
            if (includeScreenshots.get()) {
                screenshot = captureScreenshot();
            }
            sendToDiscordWebhook(record, screenshot);
        }

        info("Shulker logged: " + nonEmptySlots + " items at " + playerPos.toShortString());
    }

    private byte[] captureScreenshot() {
        try {
            int width = mc.getWindow().getFramebufferWidth();
            int height = mc.getWindow().getFramebufferHeight();
            ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4);


            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);

            byte[] pixels = new byte[buffer.remaining()];
            buffer.get(pixels);

            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int i = (x + (height - y - 1) * width) * 4;
                    int r = pixels[i] & 0xFF;
                    int g = pixels[i + 1] & 0xFF;
                    int b = pixels[i + 2] & 0xFF;
                    img.setRGB(x, y, (r << 16) | (g << 8) | b);
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            error("Failed to capture screenshot: " + e.getMessage());
            return null;
        }
    }

    private boolean containsBlacklistedItem(List<ItemRecord> items) {
        List<String> blacklist = blacklistItems.get();
        if (blacklist.isEmpty()) return false;

        for (ItemRecord item : items) {
            String itemName = item.name.toLowerCase();
            for (String blacklisted : blacklist) {
                if (itemName.contains(blacklisted.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void sendToDiscordWebhook(StashRecord record, byte[] screenshotData) {
        String title = "📦 New Stash Discovered";
        StringBuilder description = new StringBuilder();

        description.append("**Location:** ").append((int)record.x).append(", ")
            .append((int)record.y).append(", ").append((int)record.z).append("\n");
        description.append("**Dimension:** ").append(record.dimension).append("\n");
        description.append("**Items:** ").append(record.nonEmptySlots).append(" non-empty slots\n\n");

        description.append("**Top Items:**\n");
        record.items.stream()
            .sorted((a, b) -> Integer.compare(b.count, a.count))
            .limit(10)
            .forEach(item -> description.append("• ").append(item.name)
                .append(" x").append(item.count).append("\n"));

        DiscordWebhook.Embed embed = new DiscordWebhook.Embed(
            title,
            description.toString(),
            0x00FF00
        );
        embed.setFooter(new DiscordWebhook.Footer(record.timestamp));

        DiscordWebhook.WebhookPayload payload = new DiscordWebhook.WebhookPayload(
            "",
            new DiscordWebhook.Embed[]{embed},
            false
        );

        if (screenshotData != null && screenshotData.length > 0) {
            String filename = "stash_" + (int)record.x + "_" + (int)record.y + "_" + (int)record.z + ".png";
            DiscordWebhook.sendWebhookWithFile(webhookUrl.get(), payload, screenshotData, filename, "image/png");
        } else {
            DiscordWebhook.sendWebhook(webhookUrl.get(), payload);
        }
    }

    private void loadDatabase() {
        try {
            Path configDir = FabricLoader.getInstance().getConfigDir();
            Path modDataDir = configDir.getParent().resolve(savePath.get());
            Path path = modDataDir.resolve("stashes.json");

            if (Files.exists(path)) {
                String json = Files.readString(path);
                Type type = new TypeToken<Map<String, List<StashRecord>>>(){}.getType();
                Map<String, List<StashRecord>> loaded = gson.fromJson(json, type);
                if (loaded != null) {
                    stashDatabase.putAll(loaded);
                    info("Loaded " + loaded.size() + " stash records");
                }
            }
        } catch (IOException e) {
            error("Failed to load stash database: " + e.getMessage());
        }
    }

    private void saveDatabase() {
        try {
            Path configDir = FabricLoader.getInstance().getConfigDir();
            Path modDataDir = configDir.getParent().resolve(savePath.get());

            Path parentDir = modDataDir.getParent();
            if (!Files.isWritable(parentDir)) {
                error("Cannot write to directory: " + parentDir.toString());
                return;
            }

            Files.createDirectories(modDataDir);
            Path path = modDataDir.resolve("stashes.json");
            String json = gson.toJson(stashDatabase);
            Files.writeString(path, json);
        } catch (IOException e) {
            error("Failed to save stash database: " + e.getMessage());
        }
    }

    private static class StashRecord {
        String timestamp;
        double x, y, z;
        String dimension;
        List<ItemRecord> items;
        int nonEmptySlots;

        StashRecord(String timestamp, double x, double y, double z, String dimension,
                    List<ItemRecord> items, int nonEmptySlots) {
            this.timestamp = timestamp;
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimension = dimension;
            this.items = items;
            this.nonEmptySlots = nonEmptySlots;
        }
    }

    private static class ItemRecord {
        String name;
        int count;
        String itemId;

        ItemRecord(String name, int count, String itemId) {
            this.name = name;
            this.count = count;
            this.itemId = itemId;
        }
    }
}
