package dev.aztec.addon.modules;

import dev.aztec.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.DeathScreen;

public class AutoRespawnAz extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgMessage = this.settings.createGroup("Death Message");
    private final SettingGroup sgCoords = this.settings.createGroup("Coordinates");

    private final Setting<Integer> respawnDelay = sgGeneral.add(new IntSetting.Builder()
        .name("respawn-delay")
        .description("How many ticks to wait before respawning (20 ticks = 1 second).")
        .defaultValue(5)
        .min(0)
        .max(100)
        .sliderMax(20)
        .build()
    );

    public final Setting<String> deathMessage = sgMessage.add(new StringSetting.Builder()
        .name("death-message")
        .description("Message to display on the death screen.")
        .defaultValue("You died! Respawning...")
        .build()
    );

    private final Setting<Boolean> copyCoords = sgCoords.add(new BoolSetting.Builder()
        .name("copy-coords")
        .description("Copy death coordinates to clipboard.")
        .defaultValue(true)
        .build()
    );

    private int ticksSinceDeath = 0;
    private boolean hasCopiedCoords = false;
    private boolean hasRespawned = false;

    public AutoRespawnAz() {
        super(AddonTemplate.CATEGORY, "auto-respawn-az", "Automatically respawns after death with customizable settings.");
    }

    @Override
    public void onActivate() {
        ticksSinceDeath = 0;
        hasCopiedCoords = false;
        hasRespawned = false;
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (event.screen instanceof DeathScreen) {
            ticksSinceDeath = 0;
            hasCopiedCoords = false;
            hasRespawned = false;


            if (copyCoords.get() && mc.player != null) {
                String coords = String.format("X: %.2f, Y: %.2f, Z: %.2f",
                    mc.player.getX(), mc.player.getY(), mc.player.getZ());
                mc.keyboard.setClipboard(coords);
                hasCopiedCoords = true;
                info("Death coordinates copied to clipboard!");
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.currentScreen instanceof DeathScreen) {
            ticksSinceDeath++;


            if (deathMessage.get() != null && !deathMessage.get().isEmpty()) {

            }


            if (ticksSinceDeath >= respawnDelay.get() && !hasRespawned) {
                mc.player.requestRespawn();
                info("Respawned automatically!");
                hasRespawned = true;
            }
        }
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        ticksSinceDeath = 0;
        hasCopiedCoords = false;
        hasRespawned = false;
    }
}
