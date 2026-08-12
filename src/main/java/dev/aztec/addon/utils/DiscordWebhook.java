package dev.aztec.addon.utils;

import com.google.gson.Gson;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class DiscordWebhook {

    private static final Gson GSON = new Gson();

    public static class Embed {
        private String title;
        private String description;
        private int color;
        private Footer footer;

        public Embed(String title, String description, int color) {
            this.title = title;
            this.description = description;
            this.color = color;
        }

        public void setFooter(Footer footer) {
            this.footer = footer;
        }
    }

    public static class Footer {
        private String text;

        public Footer(String text) {
            this.text = text;
        }
    }

    public static class WebhookPayload {
        private String content;
        private String username;
        private String avatar_url;
        private Embed[] embeds;
        private AllowedMentions allowed_mentions;

        public WebhookPayload(String content, Embed[] embeds, boolean useEveryone) {
            this.content = content;
            this.embeds = embeds;
            if (useEveryone) {
                this.content = "@everyone " + content;
                this.allowed_mentions = new AllowedMentions();
            }
        }
    }

    public static class AllowedMentions {
        private String[] parse;

        public AllowedMentions() {
            this.parse = new String[]{"everyone"};
        }
    }

    public static CompletableFuture<Boolean> sendWebhook(String webhookUrl, WebhookPayload payload) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URL url = URI.create(webhookUrl).toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Accept", "application/json");
                connection.setDoOutput(true);

                String jsonPayload = GSON.toJson(payload);

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                connection.disconnect();

                return responseCode >= 200 && responseCode < 300;

            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    public static void sendWebhookWithFile(String webhookUrl, WebhookPayload payload, byte[] fileData, String filename, String contentType) {
        new Thread(() -> {
            try {
                URL url = URI.create(webhookUrl).toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setUseCaches(false);
                connection.setRequestProperty("User-Agent", "AztecAddon");

                String boundary = "----AztecAddonBoundary" + System.currentTimeMillis();
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

                try (OutputStream out = connection.getOutputStream();
                     PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), true)) {
                    writer.append("--").append(boundary).append("\r\n");
                    writer.append("Content-Disposition: form-data; name=\"payload_json\"\r\n");
                    writer.append("Content-Type: application/json\r\n");
                    writer.append("\r\n");
                    writer.append(GSON.toJson(payload));
                    writer.append("\r\n");
                    writer.flush();

                    writer.append("--").append(boundary).append("\r\n");
                    writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(filename).append("\"\r\n");
                    writer.append("Content-Type: ").append(contentType).append("\r\n");
                    writer.append("\r\n");
                    writer.flush();

                    out.write(fileData);
                    out.flush();

                    writer.append("\r\n");
                    writer.append("--").append(boundary).append("--\r\n");
                    writer.flush();
                }

                int responseCode = connection.getResponseCode();
                connection.disconnect();

                if (responseCode < 200 || responseCode >= 300) {
                    System.err.println("[DiscordWebhook] Failed file upload: " + responseCode);
                }
            } catch (Exception e) {
                System.err.println("[DiscordWebhook] Error uploading file: " + e.getMessage());
            }
        }).start();
    }

    public static Embed createCoordinateAlertEmbed(String playerName, String message,
                                                   Object coords,
                                                   String dimension, boolean includeTimestamp) {
        StringBuilder description = new StringBuilder();

        description.append("\uD83D\uDDE9 **Far Coordinates Detected**\n\n");

        if (playerName != null) {
            description.append("**Player:** `" + playerName + "`\n");
        }

        if (dimension != null && !dimension.isEmpty()) {
            description.append("**Dimension:** `" + dimension + "`\n");
        }

        description.append("\n**Original Message:**\n");
        description.append("> " + message);

        Embed embed = new Embed("\u26A0\uFE0F Coordinate Alert", description.toString(), 0xFF0000);

        if (includeTimestamp) {
            embed.setFooter(new Footer(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .format(java.time.LocalDateTime.ofInstant(java.time.Instant.now(), java.time.ZoneId.systemDefault()))));
        }

        return embed;
    }

    public static Embed createNormalChatEmbed(String playerName, String message,
                                              String dimension, boolean includeTimestamp, int color) {
        StringBuilder description = new StringBuilder();

        if (playerName != null) {
            description.append("**Player:** `" + playerName + "`\n");
        }

        if (dimension != null && !dimension.isEmpty()) {
            description.append("**Dimension:** `" + dimension + "`\n");
        }

        description.append("\n**Message:**\n");
        description.append("> " + message);

        Embed embed = new Embed("\uD83D\uDCE1 Chat Message", description.toString(), color);

        if (includeTimestamp) {
            embed.setFooter(new Footer(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .format(java.time.LocalDateTime.ofInstant(java.time.Instant.now(), java.time.ZoneId.systemDefault()))));
        }

        return embed;
    }
}
