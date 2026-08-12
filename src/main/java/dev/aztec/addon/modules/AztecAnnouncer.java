package dev.aztec.addon.modules;

import dev.aztec.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class AztecAnnouncer extends Module {
    private final SettingGroup sgWelcome = this.settings.createGroup("Welcome/Leave");
    private final SettingGroup sgWalking = this.settings.createGroup("Walking");


    private final Setting<Boolean> welcomeEnabled = sgWelcome.add(new BoolSetting.Builder()
        .name("welcome-enabled")
        .description("Enable welcome messages when players join.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> leaveEnabled = sgWelcome.add(new BoolSetting.Builder()
        .name("leave-enabled")
        .description("Enable leave messages when players leave.")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<String>> welcomeMessages = sgWelcome.add(new StringListSetting.Builder()
        .name("welcome-messages")
        .description("Welcome messages. Use &username& for player name.")
        .defaultValue(List.of(
            "Welcome &username&!",
            "Hello &username&! Welcome to the server!",
            "Hey &username&! Good to see you!",
            "&username& has joined the server!"
        ))
        .build()
    );

    private final Setting<List<String>> leaveMessages = sgWelcome.add(new StringListSetting.Builder()
        .name("leave-messages")
        .description("Leave messages. Use &username& for player name.")
        .defaultValue(List.of(
            "Goodbye &username&!",
            "See you later &username&!",
            "&username& has left the server.",
            "Farewell &username&!"
        ))
        .build()
    );


    private final Setting<Boolean> walkingEnabled = sgWalking.add(new BoolSetting.Builder()
        .name("walking-enabled")
        .description("Enable walking counter messages.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> walkingInterval = sgWalking.add(new IntSetting.Builder()
        .name("walking-interval")
        .description("Number of blocks between messages.")
        .defaultValue(100)
        .min(10)
        .max(10000)
        .sliderMax(500)
        .build()
    );

    private final Setting<Integer> messageCooldown = sgWelcome.add(new IntSetting.Builder()
        .name("message-cooldown")
        .description("Cooldown in seconds between welcome/leave messages.")
        .defaultValue(5)
        .min(3)
        .max(30)
        .sliderMax(10)
        .build()
    );

    private final Setting<Boolean> welcomeExisting = sgWelcome.add(new BoolSetting.Builder()
        .name("welcome-existing")
        .description("Welcome players that were already in the server when module activates.")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<String>> walkingMessages = sgWalking.add(new StringListSetting.Builder()
        .name("walking-messages")
        .description("Walking messages. Use &numero& for block count.")
        .defaultValue(List.of(
            "You've walked &numero& blocks thanks to AztecAddon!",
            "&numero& blocks walked!",
            "Keep walking! &numero& blocks so far."
        ))
        .build()
    );


    private final Random random = new Random();
    private double lastX = 0;
    private double lastZ = 0;
    private int blocksWalked = 0;
    private int blocksSinceLastMessage = 0;
    private final java.util.Map<UUID, Long> lastWelcomeTime = new java.util.HashMap<>();
    private final java.util.Map<UUID, Long> lastLeaveTime = new java.util.HashMap<>();
    private final Set<UUID> knownPlayers = new HashSet<>();
    private net.minecraft.registry.RegistryKey<net.minecraft.world.World> lastDimension = null;
    private boolean firstTick = true;

    public AztecAnnouncer() {
        super(AddonTemplate.CATEGORY, "aztec-announcer", "Announces various events like player joins/leaves and walking progress.");
    }

    @Override
    public void onActivate() {
        if (mc.player != null && mc.world != null) {
            lastX = mc.player.getX();
            lastZ = mc.player.getZ();
            lastDimension = mc.world.getRegistryKey();
            blocksWalked = 0;
            blocksSinceLastMessage = 0;
            firstTick = true;
        }
        lastWelcomeTime.clear();
        lastLeaveTime.clear();
        knownPlayers.clear();


        if (mc.world != null) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (mc.player != null && !player.getUuid().equals(mc.player.getUuid())) {
                    String name = player.getName().getString();
                    if (isValidPlayerName(name)) {
                        knownPlayers.add(player.getUuid());
                    }
                }
            }
        }


        if (welcomeEnabled.get() && welcomeExisting.get()) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (mc.player != null && !player.getUuid().equals(mc.player.getUuid())) {
                    String name = player.getName().getString();
                    if (isValidPlayerName(name)) {
                        String message = getRandomMessage(welcomeMessages.get());
                        message = replaceUsername(message, name);
                        ChatUtils.sendPlayerMsg(message);
                        lastWelcomeTime.put(player.getUuid(), System.currentTimeMillis());
                    }
                }
            }
        }
    }

    @Override
    public void onDeactivate() {
        lastWelcomeTime.clear();
        lastLeaveTime.clear();
        knownPlayers.clear();
        firstTick = true;
    }


    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!walkingEnabled.get()) return;
        if (mc.player == null || mc.world == null) return;


        net.minecraft.registry.RegistryKey<net.minecraft.world.World> currentDimension = mc.world.getRegistryKey();
        if (lastDimension != null && !currentDimension.equals(lastDimension)) {

            lastX = mc.player.getX();
            lastZ = mc.player.getZ();
            blocksSinceLastMessage = 0;
            lastDimension = currentDimension;
            return;
        }


        if (firstTick) {
            lastX = mc.player.getX();
            lastZ = mc.player.getZ();
            lastDimension = currentDimension;
            firstTick = false;
            return;
        }

        double currentX = mc.player.getX();
        double currentZ = mc.player.getZ();

        double dx = currentX - lastX;
        double dz = currentZ - lastZ;
        double distance = Math.sqrt(dx * dx + dz * dz);


        if (distance > 10) {
            lastX = currentX;
            lastZ = currentZ;
            return;
        }

        if (distance >= 0.1) {
            int blocksMoved = (int) Math.floor(distance);
            if (blocksMoved > 0) {
                blocksWalked += blocksMoved;
                blocksSinceLastMessage += blocksMoved;

                lastX = currentX;
                lastZ = currentZ;

                if (blocksSinceLastMessage >= walkingInterval.get()) {
                    String message = getRandomMessage(walkingMessages.get());
                    message = replaceNumero(message, blocksWalked);
                    ChatUtils.sendPlayerMsg(message);
                    blocksSinceLastMessage = 0;
                }
            }
        }
    }

    private String getRandomMessage(List<String> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        return messages.get(random.nextInt(messages.size()));
    }

    private String replaceUsername(String message, String username) {
        return message.replace("&username&", username);
    }

    private String replaceNumero(String message, int numero) {
        return message.replace("&numero&", String.valueOf(numero));
    }

    private boolean isValidPlayerName(String name) {
        if (name == null || name.isEmpty()) return false;


        if (!name.matches("^[a-zA-Z0-9_]{3,16}$")) return false;


        if (name.contains("-") && name.length() > 20) return false;


        if (name.toLowerCase().contains("player-") ||
            name.toLowerCase().contains("bot") ||
            name.toLowerCase().startsWith("npc")) return false;

        return true;
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (!welcomeEnabled.get()) return;
        if (mc.player == null) return;

        if (event.entity instanceof PlayerEntity player) {
            UUID uuid = player.getUuid();


            if (uuid.equals(mc.player.getUuid())) return;


            if (knownPlayers.contains(uuid)) return;

            String playerName = player.getName().getString();


            if (!isValidPlayerName(playerName)) return;


            long currentTime = System.currentTimeMillis();
            long lastTime = lastWelcomeTime.getOrDefault(uuid, 0L);
            long cooldownMs = messageCooldown.get() * 1000L;

            if (currentTime - lastTime < cooldownMs) return;


            String message = getRandomMessage(welcomeMessages.get());
            message = replaceUsername(message, playerName);
            ChatUtils.sendPlayerMsg(message);


            knownPlayers.add(uuid);
            lastWelcomeTime.put(uuid, currentTime);
        }
    }

    @EventHandler
    private void onEntityRemoved(EntityRemovedEvent event) {
        if (!leaveEnabled.get()) return;
        if (mc.player == null) return;

        if (event.entity instanceof PlayerEntity player) {
            UUID uuid = player.getUuid();


            if (uuid.equals(mc.player.getUuid())) return;


            if (!knownPlayers.contains(uuid)) return;

            String playerName = player.getName().getString();


            if (!isValidPlayerName(playerName)) return;


            long currentTime = System.currentTimeMillis();
            long lastTime = lastLeaveTime.getOrDefault(uuid, 0L);
            long cooldownMs = messageCooldown.get() * 1000L;

            if (currentTime - lastTime < cooldownMs) return;


            String message = getRandomMessage(leaveMessages.get());
            message = replaceUsername(message, playerName);
            ChatUtils.sendPlayerMsg(message);


            knownPlayers.remove(uuid);
            lastLeaveTime.put(uuid, currentTime);
        }
    }
}

