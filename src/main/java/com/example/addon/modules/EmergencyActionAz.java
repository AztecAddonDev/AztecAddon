package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

public class EmergencyActionAz extends Module {
    private final SettingGroup sgAction = this.settings.createGroup("Action");
    private final SettingGroup sgTrigger = this.settings.createGroup("Trigger");
    private final SettingGroup sgThreshold = this.settings.createGroup("Threshold");
    private final SettingGroup sgSafety = this.settings.createGroup("Safety");


    public enum ActionMode {
        VCLIP,
        DISCONNECT,
        COMMAND
    }

    private final Setting<ActionMode> actionMode = sgAction.add(new EnumSetting.Builder<ActionMode>()
        .name("action")
        .description("Action to execute when triggered.")
        .defaultValue(ActionMode.VCLIP)
        .build()
    );

    private final Setting<Integer> vclipValue = sgAction.add(new IntSetting.Builder()
        .name("vclip-value")
        .description("Value for vclip (positive = up, negative = down).")
        .defaultValue(100)
        .min(-1000)
        .max(1000)
        .sliderMax(200)
        .visible(() -> actionMode.get() == ActionMode.VCLIP)
        .build()
    );

    private final Setting<String> customCommand = sgAction.add(new StringSetting.Builder()
        .name("custom-command")
        .description("Custom command to execute when action mode is COMMAND.")
        .defaultValue(".home")
        .visible(() -> actionMode.get() == ActionMode.COMMAND)
        .build()
    );


    public enum TriggerMode {
        TOTEM_POPS,
        DAMAGE_RECEIVED
    }

    private final Setting<TriggerMode> triggerMode = sgTrigger.add(new EnumSetting.Builder<TriggerMode>()
        .name("trigger-mode")
        .description("What triggers the emergency action.")
        .defaultValue(TriggerMode.TOTEM_POPS)
        .build()
    );


    private final Setting<Integer> threshold = sgThreshold.add(new IntSetting.Builder()
        .name("threshold")
        .description("Threshold value for triggering the action.")
        .defaultValue(3)
        .min(1)
        .sliderMax(10)
        .build()
    );


    private final Setting<Integer> cooldown = sgSafety.add(new IntSetting.Builder()
        .name("cooldown")
        .description("Cooldown in seconds before the action can trigger again.")
        .defaultValue(5)
        .min(0)
        .sliderMax(30)
        .build()
    );

    private final Setting<Boolean> onlyOncePerLife = sgSafety.add(new BoolSetting.Builder()
        .name("only-once-per-life")
        .description("Only execute the action once per life.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> resetOnRespawn = sgSafety.add(new BoolSetting.Builder()
        .name("reset-on-respawn")
        .description("Reset counters on respawn.")
        .defaultValue(true)
        .build()
    );


    private int counter = 0;
    private long lastActionTime = 0;
    private boolean hasTriggeredThisLife = false;
    private float lastHealth = Float.MAX_VALUE;
    private boolean wasDead = false;

    public EmergencyActionAz() {
        super(AddonTemplate.CATEGORY, "emergency-action-az", "Automatically executes an action when a threshold is reached.");
    }

    @Override
    public void onActivate() {
        counter = 0;
        lastActionTime = 0;
        hasTriggeredThisLife = false;
        lastHealth = mc.player != null ? mc.player.getHealth() : Float.MAX_VALUE;
        wasDead = mc.player != null && mc.player.getHealth() <= 0;
    }

    @Override
    public void onDeactivate() {
        counter = 0;
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        counter = 0;
        hasTriggeredThisLife = false;
        lastActionTime = 0;
        lastHealth = mc.player != null ? mc.player.getHealth() : Float.MAX_VALUE;
        wasDead = mc.player != null && mc.player.getHealth() <= 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;


        if (wasDead && mc.player.getHealth() > 0) {
            onPlayerRespawn();
        }
        wasDead = mc.player.getHealth() <= 0;


        if (System.currentTimeMillis() - lastActionTime < cooldown.get() * 1000L) {
            return;
        }


        if (onlyOncePerLife.get() && hasTriggeredThisLife) {
            return;
        }



        if (triggerMode.get() == TriggerMode.DAMAGE_RECEIVED) {
            processDamageReceived();
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof EntityStatusS2CPacket packet) {

            if (packet.getStatus() == 35 && packet.getEntity(mc.world) == mc.player) {

                if (System.currentTimeMillis() - lastActionTime < cooldown.get() * 1000L) {
                    return;
                }


                if (onlyOncePerLife.get() && hasTriggeredThisLife) {
                    return;
                }

                counter++;

                if (counter >= threshold.get()) {
                    executeAction();
                }
            }
        }
    }


    private void processDamageReceived() {
        if (mc.player == null) return;

        float currentHealth = mc.player.getHealth();


        if (lastHealth != Float.MAX_VALUE && currentHealth < lastHealth) {
            float damageReceived = lastHealth - currentHealth;

            counter += (int) damageReceived;

            if (counter >= threshold.get()) {
                executeAction();
            }
        }

        lastHealth = currentHealth;
    }

    private void executeAction() {
        switch (actionMode.get()) {
            case VCLIP:
                executeVclip();
                break;
            case DISCONNECT:
                executeDisconnect();
                break;
            case COMMAND:
                executeCustomCommand();
                break;
        }


        String triggerInfo = triggerMode.get() == TriggerMode.TOTEM_POPS
            ? " after " + counter + " totem pops"
            : " after " + (int)counter + " damage received";
        info("Emergency action triggered: " + actionMode.get() + triggerInfo);


        counter = 0;
        lastActionTime = System.currentTimeMillis();
        hasTriggeredThisLife = true;
    }

    private void executeVclip() {
        int value = vclipValue.get();
        String cmd = ".vclip " + value;
        ChatUtils.sendPlayerMsg(cmd);
    }

    private void executeDisconnect() {
        ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
        if (networkHandler != null) {

            networkHandler.getConnection().disconnect(net.minecraft.text.Text.of("Emergency disconnect triggered by Aztec Addon"));
        }
    }

    private void executeCustomCommand() {
        String cmd = customCommand.get();
        if (cmd == null || cmd.isEmpty()) return;
        ChatUtils.sendPlayerMsg(cmd);
    }

    public void onPlayerRespawn() {
        if (resetOnRespawn.get()) {
            counter = 0;
            hasTriggeredThisLife = false;
            lastActionTime = 0;
            lastHealth = mc.player != null ? mc.player.getHealth() : Float.MAX_VALUE;
        }
    }
}
