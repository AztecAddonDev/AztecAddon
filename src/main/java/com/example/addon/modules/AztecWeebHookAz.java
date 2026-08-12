package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.utils.DiscordWebhook;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AztecWeebHookAz extends Module {
    private final SettingGroup sgDiscord = this.settings.createGroup("Discord");
    private final SettingGroup sgCoordinateDetection = this.settings.createGroup("Coordinate Detection");
    private final SettingGroup sgAlerts = this.settings.createGroup("Alerts");
    private final SettingGroup sgAntiSpam = this.settings.createGroup("Anti-Spam");


    private final Setting<String> webhookUrl = sgDiscord.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL to send messages to.")
        .defaultValue("")
        .build()
    );

    private final Setting<Boolean> sendChat = sgDiscord.add(new BoolSetting.Builder()
        .name("send-chat")
        .description("Send normal chat messages to Discord.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> sendCoordinateAlerts = sgDiscord.add(new BoolSetting.Builder()
        .name("send-coordinate-alerts")
        .description("Send coordinate alerts to Discord.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> includePlayerName = sgDiscord.add(new BoolSetting.Builder()
        .name("include-player-name")
        .description("Include player name in Discord messages.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> includeDimension = sgDiscord.add(new BoolSetting.Builder()
        .name("include-dimension")
        .description("Include current dimension in Discord messages.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> includeTimestamp = sgDiscord.add(new BoolSetting.Builder()
        .name("include-timestamp")
        .description("Include timestamp in Discord messages.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreSelfMessages = sgDiscord.add(new BoolSetting.Builder()
        .name("ignore-self-messages")
        .description("Ignore messages sent by yourself to prevent network issues.")
        .defaultValue(true)
        .build()
    );


    private final Setting<Boolean> detectCoordinates = sgCoordinateDetection.add(new BoolSetting.Builder()
        .name("detect-coordinates")
        .description("Detect coordinates in chat messages.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> coordinateThreshold = sgCoordinateDetection.add(new IntSetting.Builder()
        .name("coordinate-threshold")
        .description("Threshold for considering coordinates as 'far'.")
        .defaultValue(1000)
        .min(0)
        .max(30000)
        .sliderMax(5000)
        .build()
    );

    private final Setting<Boolean> detectWithoutAxisLabels = sgCoordinateDetection.add(new BoolSetting.Builder()
        .name("detect-without-axis-labels")
        .description("Detect coordinates without explicit X/Z labels.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectThreeCoordinateFormat = sgCoordinateDetection.add(new BoolSetting.Builder()
        .name("detect-three-coordinate-format")
        .description("Detect three-coordinate format (X Y Z).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> requireContextForImplicitCoordinates = sgCoordinateDetection.add(new BoolSetting.Builder()
        .name("require-context-for-implicit-coordinates")
        .description("Require context keywords for implicit coordinate detection.")
        .defaultValue(true)
        .build()
    );


    private final Setting<Boolean> highlightFarCoordinates = sgAlerts.add(new BoolSetting.Builder()
        .name("highlight-far-coordinates")
        .description("Highlight far coordinates with red embed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> alertKeywords = sgAlerts.add(new StringSetting.Builder()
        .name("alert-keywords")
        .description("Keywords that trigger @everyone mention (comma-separated).")
        .defaultValue("base,coords,coordinates")
        .build()
    );

    private final Setting<String> alertPrefix = sgAlerts.add(new StringSetting.Builder()
        .name("alert-prefix")
        .description("Prefix for coordinate alerts.")
        .defaultValue("COORDINATE ALERT")
        .build()
    );

    private final Setting<SettingColor> normalChatColor = sgAlerts.add(new ColorSetting.Builder()
        .name("normal-chat-color")
        .description("Color for normal chat messages.")
        .defaultValue(new SettingColor(0, 255, 0, 255))
        .build()
    );


    private final Setting<Integer> webhookCooldown = sgAntiSpam.add(new IntSetting.Builder()
        .name("webhook-cooldown")
        .description("Minimum time between webhook sends in seconds.")
        .defaultValue(2)
        .min(0)
        .max(60)
        .sliderMax(10)
        .build()
    );

    private final Setting<Boolean> duplicateDetection = sgAntiSpam.add(new BoolSetting.Builder()
        .name("duplicate-detection")
        .description("Prevent sending duplicate messages.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> duplicateWindow = sgAntiSpam.add(new IntSetting.Builder()
        .name("duplicate-window")
        .description("Time window for duplicate detection in seconds.")
        .defaultValue(10)
        .min(0)
        .max(60)
        .sliderMax(30)
        .build()
    );


    private long lastWebhookSend = 0;
    private final Map<String, Long> messageHistory = new HashMap<>();

    public AztecWeebHookAz() {
        super(AddonTemplate.CATEGORY, "aztec-weeb-hook-az", "Connects Minecraft chat to Discord webhook with coordinate detection.");
    }

    @Override
    public void onActivate() {
        lastWebhookSend = 0;
        messageHistory.clear();
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!isActive()) return;

        String webhook = webhookUrl.get();
        if (!isValidWebhookUrl(webhook)) return;

        String messageString = event.getMessage().getString();
        if (messageString == null || messageString.isEmpty()) return;


        if (ignoreSelfMessages.get() && mc.player != null) {
            String playerName = mc.player.getName().getString();
            if (messageString.startsWith("<" + playerName + ">") ||
                messageString.startsWith("[" + playerName + "]")) {
                return;
            }
        }


        long currentTime = System.currentTimeMillis();
        if (currentTime - lastWebhookSend < webhookCooldown.get() * 1000L) {
            return;
        }


        messageHistory.entrySet().removeIf(entry ->
            currentTime - entry.getValue() > duplicateWindow.get() * 1000L);


        if (duplicateDetection.get() && messageHistory.containsKey(messageString)) {
            return;
        }


        BlockPos coords = extractCoordinates(messageString);
        double distance = coords != null ? calculateDistance(coords) : 0;
        boolean hasCoordinates = coords != null;
        boolean isFarCoordinate = hasCoordinates && distance > coordinateThreshold.get();
        boolean hasAlertKeyword = containsAlertKeyword(messageString);


        boolean shouldSend = false;
        boolean useCoordinateAlert = false;

        if (sendChat.get() && !hasCoordinates) {
            shouldSend = true;
        }

        if (hasCoordinates && sendCoordinateAlerts.get()) {
            shouldSend = true;
            useCoordinateAlert = true;
        }

        if (!shouldSend) return;


        String playerName = includePlayerName.get() && mc.player != null ?
            mc.player.getName().getString() : null;
        String dimension = includeDimension.get() && mc.world != null ?
            mc.world.getRegistryKey().getValue().toString() : null;

        DiscordWebhook.Embed embed;
        boolean useEveryoneMention = false;

        if (useCoordinateAlert && isFarCoordinate && highlightFarCoordinates.get()) {

            useEveryoneMention = true;
            embed = DiscordWebhook.createCoordinateAlertEmbed(
                playerName,
                messageString,
                coords,
                dimension,
                includeTimestamp.get()
            );
        } else if (useCoordinateAlert) {

            String prefix = alertPrefix.get();
            StringBuilder description = new StringBuilder();

            if (prefix != null && !prefix.isEmpty()) {
                description.append("**").append(prefix).append("**\n\n");
            }

            description.append("**Coordinates:** `").append(coords.getX()).append(", ")
                       .append(coords.getY()).append(", ").append(coords.getZ()).append("`\n");
            description.append("**Distance:** `").append(String.format("%.1f", distance)).append(" blocks`\n\n");
            description.append("**Original Message:**\n> ").append(messageString);

            if (playerName != null) {
                description.insert(0, "**Player:** `" + playerName + "`\n");
            }

            if (dimension != null && !dimension.isEmpty()) {
                description.insert(0, "**Dimension:** `" + dimension + "`\n");
            }

            SettingColor color = normalChatColor.get();
            int colorInt = ((color.r & 0xFF) << 16) | ((color.g & 0xFF) << 8) | (color.b & 0xFF);

            embed = new DiscordWebhook.Embed(
                "\uD83D\uDCCD Coordinate Detected",
                description.toString(),
                colorInt
            );

            if (includeTimestamp.get()) {
                embed.setFooter(new DiscordWebhook.Footer(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .format(java.time.LocalDateTime.ofInstant(java.time.Instant.now(), java.time.ZoneId.systemDefault()))
                ));
            }
        } else {

            SettingColor color = normalChatColor.get();
            int colorInt = ((color.r & 0xFF) << 16) | ((color.g & 0xFF) << 8) | (color.b & 0xFF);

            embed = DiscordWebhook.createNormalChatEmbed(
                playerName,
                messageString,
                dimension,
                includeTimestamp.get(),
                colorInt
            );
        }

        DiscordWebhook.WebhookPayload payload = new DiscordWebhook.WebhookPayload(
            "",
            new DiscordWebhook.Embed[]{embed},
            useEveryoneMention
        );


        DiscordWebhook.sendWebhook(webhook, payload).thenAccept(success -> {
            if (success) {
                lastWebhookSend = System.currentTimeMillis();
                messageHistory.put(messageString, System.currentTimeMillis());
            }
        });
    }

    private boolean containsAlertKeyword(String message) {
        String keywords = alertKeywords.get();
        if (keywords == null || keywords.isEmpty()) return false;

        String[] keywordArray = keywords.toLowerCase().split(",");
        String messageLower = message.toLowerCase();

        for (String keyword : keywordArray) {
            if (messageLower.contains(keyword.trim())) {
                return true;
            }
        }
        return false;
    }

    private BlockPos extractCoordinates(String message) {
        if (!detectCoordinates.get()) return null;

        String messageLower = message.toLowerCase();


        Pattern explicitPattern = Pattern.compile("[xX][=: ]+(-?\\d+)[, ]*[yY][=: ]+(-?\\d+)[, ]*[zZ][=: ]+(-?\\d+)");
        Matcher explicitMatcher = explicitPattern.matcher(message);

        if (explicitMatcher.find()) {
            try {
                int x = Integer.parseInt(explicitMatcher.group(1));
                int y = Integer.parseInt(explicitMatcher.group(2));
                int z = Integer.parseInt(explicitMatcher.group(3));
                return new BlockPos(x, y, z);
            } catch (NumberFormatException e) {

            }
        }


        if (detectThreeCoordinateFormat.get()) {
            Pattern threeNumPattern = Pattern.compile("(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)");
            Matcher threeNumMatcher = threeNumPattern.matcher(message);

            if (threeNumMatcher.find()) {

                if (requireContextForImplicitCoordinates.get()) {
                    boolean hasContext = messageLower.contains("coord") ||
                                       messageLower.contains("base") ||
                                       messageLower.contains("pos") ||
                                       messageLower.contains("location");
                    if (!hasContext) return null;
                }

                try {
                    int x = Integer.parseInt(threeNumMatcher.group(1));
                    int y = Integer.parseInt(threeNumMatcher.group(2));
                    int z = Integer.parseInt(threeNumMatcher.group(3));
                    return new BlockPos(x, y, z);
                } catch (NumberFormatException e) {

                }
            }
        }

        return null;
    }

    private double calculateDistance(BlockPos coords) {
        if (mc.player == null || coords == null) return 0;

        BlockPos playerPos = mc.player.getBlockPos();
        return Math.sqrt(
            Math.pow(coords.getX() - playerPos.getX(), 2) +
            Math.pow(coords.getY() - playerPos.getY(), 2) +
            Math.pow(coords.getZ() - playerPos.getZ(), 2)
        );
    }
    private boolean isValidWebhookUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        return url.startsWith("https://discord.com/api/webhooks/") ||
            url.startsWith("https://discordapp.com/api/webhooks/") ||
            url.startsWith("https://canary.discord.com/api/webhooks/");
    }
}
