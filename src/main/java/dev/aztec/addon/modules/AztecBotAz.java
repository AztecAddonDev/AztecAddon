package dev.aztec.addon.modules;

import dev.aztec.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class AztecBotAz extends Module {
    private final SettingGroup sgGeneral = this.settings.createGroup("General");
    private final SettingGroup sgSafety = this.settings.createGroup("Safety");


    private final Setting<Boolean> enabled = sgGeneral.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Enable the bot functionality.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> prefix = sgGeneral.add(new StringSetting.Builder()
        .name("prefix")
        .description("Command prefix to trigger bot responses.")
        .defaultValue("&")
        .build()
    );

    private final Setting<String> responsePrefix = sgGeneral.add(new StringSetting.Builder()
        .name("response-prefix")
        .description("Prefix for bot responses.")
        .defaultValue("Aztec:")
        .build()
    );

    private final Setting<String> discordLink = sgGeneral.add(new StringSetting.Builder()
        .name("discord-link")
        .description("Discord link for the &discord command.")
        .defaultValue("https://discord.gg/RYNY6vk5Rc")
        .build()
    );


    private final Setting<Boolean> ignoreSelf = sgSafety.add(new BoolSetting.Builder()
        .name("ignore-self")
        .description("Ignore messages sent by yourself. Disable for singleplayer testing.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> disableCooldown = sgSafety.add(new BoolSetting.Builder()
        .name("disable-cooldown")
        .description("Disable cooldown for testing purposes.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> responseCooldown = sgSafety.add(new IntSetting.Builder()
        .name("response-cooldown")
        .description("Cooldown in seconds between bot responses.")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Integer> sendDelay = sgSafety.add(new IntSetting.Builder()
        .name("send-delay")
        .description("Delay in ticks before sending response (20 ticks = 1 second).")
        .defaultValue(5)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );


    private long lastResponseTime = 0;
    private String lastProcessedMessage = "";
    private Queue<PendingResponse> responseQueue = new LinkedList<>();
    private int ticksUntilNextSend = 0;


    private static class PlayerData {
        double x, y, z;
        float health, maxHealth;
        long lastSeen;

        PlayerData(double x, double y, double z, float health, float maxHealth) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.health = health;
            this.maxHealth = maxHealth;
            this.lastSeen = System.currentTimeMillis();
        }

        void update(double x, double y, double z, float health, float maxHealth) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.health = health;
            this.maxHealth = maxHealth;
            this.lastSeen = System.currentTimeMillis();
        }
    }

    private Map<String, PlayerData> playerCache = new HashMap<>();

    private static class PendingResponse {
        final String message;
        final long timestamp;

        PendingResponse(String message) {
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public AztecBotAz() {
        super(AddonTemplate.CATEGORY, "aztec-bot-az", "Responds to chat commands automatically.");
    }

    @Override
    public void onActivate() {
        lastResponseTime = 0;
        lastProcessedMessage = "";
        responseQueue.clear();
        ticksUntilNextSend = 0;
    }

    @Override
    public void onDeactivate() {
        responseQueue.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive()) return;


        if (mc.world != null) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                String playerName = player.getName().getString();
                PlayerData data = playerCache.get(playerName);
                if (data == null) {
                    playerCache.put(playerName, new PlayerData(player.getX(), player.getY(), player.getZ(), player.getHealth(), player.getMaxHealth()));
                } else {
                    data.update(player.getX(), player.getY(), player.getZ(), player.getHealth(), player.getMaxHealth());
                }
            }
        }


        if (ticksUntilNextSend > 0) {
            ticksUntilNextSend--;
        }


        if (!responseQueue.isEmpty() && ticksUntilNextSend <= 0) {
            PendingResponse response = responseQueue.poll();
            sendResponse(response.message);
            lastResponseTime = System.currentTimeMillis();

            ticksUntilNextSend = sendDelay.get();
        }
    }

    public void processChatMessage(String messageString) {
        if (!isActive()) return;
        if (!enabled.get()) return;

        if (messageString == null || messageString.isEmpty()) return;


        if (messageString.equals(lastProcessedMessage)) {
            return;
        }
        lastProcessedMessage = messageString;


        if (ignoreSelf.get() && isOwnMessage(messageString)) {
            return;
        }


        String content = extractMessageContent(messageString);


        String cmdPrefix = prefix.get();
        if (!content.startsWith(cmdPrefix)) {
            return;
        }


        String command = content.substring(cmdPrefix.length()).trim().toLowerCase();
        if (command.isEmpty()) return;


        String response = processCommand(command);
        if (response != null) {

            if (!disableCooldown.get() && lastResponseTime > 0 && System.currentTimeMillis() - lastResponseTime < responseCooldown.get() * 1000L) {
                return;
            }

            responseQueue.add(new PendingResponse(response));

            if (ticksUntilNextSend <= 0) {
                ticksUntilNextSend = sendDelay.get();
            }
        }
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!isActive()) return;
        if (!enabled.get()) return;

        String messageString = event.getMessage().getString();
        if (messageString == null || messageString.isEmpty()) return;


        if (messageString.equals(lastProcessedMessage)) {
            return;
        }
        lastProcessedMessage = messageString;


        if (ignoreSelf.get() && isOwnMessage(messageString)) {

            if (isServerRejection(messageString)) {
                String response = responsePrefix.get() + " It seems I can't help you with that interaction.";


                if (!disableCooldown.get() && lastResponseTime > 0 && System.currentTimeMillis() - lastResponseTime < responseCooldown.get() * 1000L) {
                    return;
                }

                responseQueue.add(new PendingResponse(response));
                if (ticksUntilNextSend <= 0) {
                    ticksUntilNextSend = sendDelay.get();
                }
            }
            return;
        }


        String content = extractMessageContent(messageString);


        String cmdPrefix = prefix.get();
        if (content.startsWith(cmdPrefix)) {

            String command = content.substring(cmdPrefix.length()).trim().toLowerCase();
            if (command.isEmpty()) return;


            String response = processCommand(command);
            if (response != null) {

                if (!disableCooldown.get() && lastResponseTime > 0 && System.currentTimeMillis() - lastResponseTime < responseCooldown.get() * 1000L) {
                    return;
                }

                responseQueue.add(new PendingResponse(response));

                if (ticksUntilNextSend <= 0) {
                    ticksUntilNextSend = sendDelay.get();
                }
            } else {

                String senderName = extractSenderName(messageString);
                String unknownResponse = responsePrefix.get() + " That command doesn't appear in my list :( " + (senderName != null ? senderName : "");


                if (!disableCooldown.get() && lastResponseTime > 0 && System.currentTimeMillis() - lastResponseTime < responseCooldown.get() * 1000L) {
                    return;
                }

                responseQueue.add(new PendingResponse(unknownResponse));
                if (ticksUntilNextSend <= 0) {
                    ticksUntilNextSend = sendDelay.get();
                }
            }
        }
    }

    private boolean isOwnMessage(String message) {
        if (mc.player == null) return false;

        String playerName = mc.player.getName().getString();


        if (message.startsWith("<" + playerName + ">")) return true;
        if (message.startsWith("[" + playerName + "]")) return true;


        if (message.startsWith(responsePrefix.get())) return true;

        return false;
    }

    private boolean isServerRejection(String message) {
        String lowerMessage = message.toLowerCase();


        if (lowerMessage.matches(".*\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}.*")) {
            return true;
        }


        if (lowerMessage.contains(".com") || lowerMessage.contains(".net") ||
            lowerMessage.contains(".org") || lowerMessage.contains(".io") ||
            lowerMessage.contains(".gg") || lowerMessage.contains(".xyz")) {
            return true;
        }


        if (lowerMessage.contains("please don't share") ||
            lowerMessage.contains("advertising") ||
            lowerMessage.contains("not allowed") ||
            lowerMessage.contains("blocked") ||
            lowerMessage.contains("filtered")) {
            return true;
        }

        return false;
    }

    private String extractMessageContent(String message) {

        if (message.startsWith("<")) {
            int endBracket = message.indexOf(">");
            if (endBracket != -1 && endBracket + 1 < message.length()) {
                return message.substring(endBracket + 1).trim();
            }
        }
        if (message.startsWith("[")) {
            int endBracket = message.indexOf("]");
            if (endBracket != -1 && endBracket + 1 < message.length()) {
                return message.substring(endBracket + 1).trim();
            }
        }

        if (message.contains(":")) {
            int colonIndex = message.indexOf(":");
            if (colonIndex + 1 < message.length()) {
                return message.substring(colonIndex + 1).trim();
            }
        }

        return message.trim();
    }

    private String extractSenderName(String message) {

        if (message.startsWith("<")) {
            int endBracket = message.indexOf(">");
            if (endBracket != -1) {
                return message.substring(1, endBracket).trim();
            }
        }
        if (message.startsWith("[")) {
            int endBracket = message.indexOf("]");
            if (endBracket != -1) {
                return message.substring(1, endBracket).trim();
            }
        }
        if (message.contains(":")) {
            int colonIndex = message.indexOf(":");
            if (colonIndex > 0) {
                return message.substring(0, colonIndex).trim();
            }
        }
        return null;
    }

    private String processCommand(String command) {
        String responsePrefix = this.responsePrefix.get();
        String cmdPrefix = prefix.get();


        String[] parts = command.split("\\s+");
        String baseCommand = parts[0].toLowerCase();
        String parameter = parts.length > 1 ? parts[1] : null;

        switch (baseCommand) {
            case "help":
                return responsePrefix + " Commands: fps, ping, pos/coords, direction, speed, health, food, armor, xp, gamemode, biome, dimension, time, day, weather, server, players, effects, discord, about, version, tpatome, hand, inv, item [name]";

            case "fps":
                int fps = getCurrentFPS();
                return responsePrefix + " My FPS are " + fps;

            case "ping":
                int ping = getPing();
                return responsePrefix + " My ping is " + ping + " ms";

            case "pos":
            case "coords":
                if (parameter != null) {

                    PlayerData data = playerCache.get(parameter);
                    if (data != null) {
                        long timeAgo = (System.currentTimeMillis() - data.lastSeen) / 1000;
                        return responsePrefix + " " + parameter + " - X: " + (int)data.x + " Y: " + (int)data.y + " Z: " + (int)data.z + " (seen " + timeAgo + "s ago)";
                    }
                    return responsePrefix + " Player not in cache: " + parameter;
                }
                if (mc.player == null) return null;
                return responsePrefix + " X: " + (int)mc.player.getX() + " Y: " + (int)mc.player.getY() + " Z: " + (int)mc.player.getZ();

            case "direction":
                if (mc.player == null) return null;
                return responsePrefix + " Direction: " + getDirection();

            case "speed":
                if (mc.player == null) return null;
                double speed = Math.sqrt(mc.player.getVelocity().x * mc.player.getVelocity().x + mc.player.getVelocity().z * mc.player.getVelocity().z);
                return responsePrefix + " Speed: " + String.format("%.2f", speed * 20) + " blocks/sec";

            case "health":
                if (parameter != null) {

                    PlayerData data = playerCache.get(parameter);
                    if (data != null) {
                        long timeAgo = (System.currentTimeMillis() - data.lastSeen) / 1000;
                        return responsePrefix + " " + parameter + " - Health: " + (int)data.health + "/" + (int)data.maxHealth + " (seen " + timeAgo + "s ago)";
                    }
                    return responsePrefix + " Player not in cache: " + parameter;
                }
                if (mc.player == null) return null;
                float health = mc.player.getHealth();
                float maxHealth = mc.player.getMaxHealth();
                return responsePrefix + " Health: " + (int)health + "/" + (int)maxHealth;

            case "food":
                if (mc.player == null) return null;
                int foodLevel = mc.player.getHungerManager().getFoodLevel();
                float saturation = mc.player.getHungerManager().getSaturationLevel();
                return responsePrefix + " Food: " + foodLevel + "/20, Saturation: " + String.format("%.1f", saturation);

            case "armor":
                if (mc.player == null) return null;
                int armor = getArmorPercentage();
                return responsePrefix + " Armor: " + armor + "%";

            case "xp":
                if (mc.player == null) return null;
                int xpLevel = mc.player.experienceLevel;
                int xpProgress = (int)(mc.player.experienceProgress * 100);
                return responsePrefix + " XP Level: " + xpLevel + ", Progress: " + xpProgress + "%";

            case "gamemode":
                if (mc.player == null) return null;
                return responsePrefix + " Gamemode: " + (mc.player.getAbilities().creativeMode ? "Creative" : "Survival");

            case "biome":
                if (mc.player == null || mc.world == null) return null;
                try {
                    var biome = mc.world.getBiome(mc.player.getBlockPos());
                    String biomeName = biome.value().toString();
                    return responsePrefix + " Biome: " + biomeName;
                } catch (Exception e) {
                    return responsePrefix + " Biome: Unknown";
                }

            case "dimension":
                if (mc.world == null) return null;
                String dimension = mc.world.getRegistryKey().getValue().toString();
                String dimensionName = formatDimension(dimension);
                return responsePrefix + " Dimension: " + dimensionName;

            case "time":
                if (mc.world == null) return null;
                long time = mc.world.getTimeOfDay();
                int hours = (int)((time / 1000 + 6) % 24);
                int minutes = (int)((time % 1000) * 60 / 1000);
                return responsePrefix + " World time: " + String.format("%02d:%02d", hours, minutes);

            case "day":
                if (mc.world == null) return null;
                long day = mc.world.getTimeOfDay() / 24000L;
                return responsePrefix + " Day: " + (day + 1);

            case "weather":
                if (mc.world == null) return null;
                boolean isRaining = mc.world.isRaining();
                boolean isThundering = mc.world.isThundering();
                String weather = isThundering ? "Thunder" : (isRaining ? "Rain" : "Clear");
                return responsePrefix + " Weather: " + weather;

            case "server":
                ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
                if (networkHandler == null) return null;
                String server = networkHandler.getServerInfo() != null ? networkHandler.getServerInfo().address.toString() : "Singleplayer";
                return responsePrefix + " Server: " + server;

            case "players":
                if (mc.world == null) return null;
                int playerCount = mc.world.getPlayers().size();
                return responsePrefix + " Online players: " + playerCount;

            case "effects":
                if (mc.player == null) return null;
                var effects = mc.player.getActiveStatusEffects();
                if (effects.isEmpty()) {
                    return responsePrefix + " No active effects";
                }
                StringBuilder effectList = new StringBuilder();
                effects.forEach((effect, instance) -> {
                    effectList.append(effect.value().toString()).append(" ");
                });
                return responsePrefix + " Effects: " + effectList.toString().trim();

            case "discord":
                return responsePrefix + " Discord de Aztec Addon: " + discordLink.get();

            case "about":
                return responsePrefix + " Aztec Addon - QoL, Misc, Render and Movement utilities.";

            case "version":
                return responsePrefix + " Aztec Addon version: 0.1.0";

            case "tpatome":
                if (mc.player == null) return null;
                String senderName = extractSenderName(lastProcessedMessage);
                if (senderName != null && !senderName.isEmpty()) {

                    senderName = senderName.replaceAll("[^a-zA-Z0-9_]", "");
                    if (!senderName.isEmpty()) {
                        ChatUtils.sendPlayerMsg("/tpa " + senderName);
                        return responsePrefix + " Sending teleport request to " + senderName;
                    }
                }
                return responsePrefix + " Could not detect player name";

            case "hand":
                if (mc.player == null) return null;
                ItemStack handItem = mc.player.getMainHandStack();
                if (handItem.isEmpty()) {
                    return responsePrefix + " Empty hand";
                }
                return responsePrefix + " Hand: " + handItem.getName().getString() + " x" + handItem.getCount();

            case "inv":
                if (mc.player == null) return null;
                int emptySlots = 0;
                int totalSlots = 36;
                for (int i = 0; i < 36; i++) {
                    if (mc.player.getInventory().getStack(i).isEmpty()) {
                        emptySlots++;
                    }
                }
                return responsePrefix + " Inventory: " + (totalSlots - emptySlots) + "/" + totalSlots + " slots used";

            case "item":
                if (parameter == null) {
                    return responsePrefix + " Usage: " + cmdPrefix + "item [item name]";
                }
                if (mc.player == null) return null;
                int itemCount = 0;
                for (int i = 0; i < 36; i++) {
                    ItemStack stack = mc.player.getInventory().getStack(i);
                    if (!stack.isEmpty()) {
                        String itemName = stack.getName().getString().toLowerCase();
                        if (itemName.contains(parameter.toLowerCase())) {
                            itemCount += stack.getCount();
                        }
                    }
                }
                if (itemCount > 0) {
                    return responsePrefix + " Found " + itemCount + " of " + parameter;
                }
                return responsePrefix + " No " + parameter + " found in inventory";

            default:
                return null;
        }
    }

    private int getPing() {
        if (mc.player == null || mc.player.networkHandler == null) return 0;
        return mc.player.networkHandler.getPlayerListEntry(mc.player.getUuid()) != null
            ? mc.player.networkHandler.getPlayerListEntry(mc.player.getUuid()).getLatency()
            : 0;
    }

    private int getCurrentFPS() {


        return 60;
    }

    private int getArmorPercentage() {
        if (mc.player == null) return 0;

        int totalArmor = 0;


        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                ItemStack armorStack = mc.player.getEquippedStack(slot);
                if (!armorStack.isEmpty()) {
                    totalArmor += getArmorProtection(armorStack);
                }
            }
        }


        int percentage = (int)((totalArmor / 20.0) * 100);
        return Math.min(percentage, 100);
    }

    private int getArmorProtection(ItemStack stack) {
        Item item = stack.getItem();


        String itemName = item.toString().toLowerCase();







        if (itemName.contains("netherite") || itemName.contains("diamond")) {
            if (itemName.contains("boots")) return 3;
            if (itemName.contains("leggings")) return 6;
            if (itemName.contains("chestplate")) return 8;
            if (itemName.contains("helmet")) return 3;
        }
        if (itemName.contains("iron") || itemName.contains("gold")) {
            if (itemName.contains("boots")) return 2;
            if (itemName.contains("leggings")) return 5;
            if (itemName.contains("chestplate")) return 6;
            if (itemName.contains("helmet")) return 2;
        }
        if (itemName.contains("chainmail")) {
            if (itemName.contains("boots")) return 1;
            if (itemName.contains("leggings")) return 4;
            if (itemName.contains("chestplate")) return 5;
            if (itemName.contains("helmet")) return 2;
        }
        if (itemName.contains("leather")) {
            if (itemName.contains("boots")) return 1;
            if (itemName.contains("leggings")) return 2;
            if (itemName.contains("chestplate")) return 3;
            if (itemName.contains("helmet")) return 1;
        }

        return 0;
    }

    private String formatDimension(String dimensionId) {
        switch (dimensionId) {
            case "minecraft:overworld":
                return "Overworld";
            case "minecraft:the_nether":
                return "Nether";
            case "minecraft:the_end":
                return "End";
            default:
                return dimensionId.replace("minecraft:", "").replace("_", " ");
        }
    }

    private String getDirection() {
        if (mc.player == null) return "Unknown";
        float yaw = mc.player.getYaw();

        if (yaw >= -45 && yaw < 45) return "South";
        if (yaw >= 45 && yaw < 135) return "West";
        if (yaw >= 135 || yaw < -135) return "North";
        return "East";
    }

    private void sendResponse(String message) {
        if (message == null || message.isEmpty()) return;

        final int MAX_LENGTH = 255;


        if (message.length() <= MAX_LENGTH) {
            ChatUtils.sendPlayerMsg(message);
            return;
        }


        List<String> chunks = splitMessage(message, MAX_LENGTH);


        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);


            if (chunk.length() > MAX_LENGTH) {
                chunk = chunk.substring(0, MAX_LENGTH);
            }

            responseQueue.add(new PendingResponse(chunk));


            if (i < chunks.size() - 1) {
                ticksUntilNextSend = Math.max(ticksUntilNextSend, sendDelay.get() + 5);
            }
        }
    }

    private List<String> splitMessage(String message, int maxLength) {
        List<String> chunks = new ArrayList<>();


        int start = 0;
        while (start < message.length()) {
            int end = Math.min(start + maxLength, message.length());


            if (end < message.length()) {

                int lastSpace = message.lastIndexOf(' ', end);
                if (lastSpace > start + 20) {
                    end = lastSpace;
                }
            }

            chunks.add(message.substring(start, end));
            start = end;


            if (start < message.length() && message.charAt(start) == ' ') {
                start++;
            }
        }

        return chunks;
    }
}
