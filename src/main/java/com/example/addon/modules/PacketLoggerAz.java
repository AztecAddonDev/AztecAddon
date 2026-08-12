package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;

public class PacketLoggerAz extends Module {
    private final SettingGroup sgGeneral = this.settings.createGroup("General");

    private final Setting<Boolean> filterInventory = sgGeneral.add(new BoolSetting.Builder()
        .name("filter-inventory")
        .description("Only show inventory/crafting related packets.")
        .defaultValue(true)
        .build()
    );

    public PacketLoggerAz() {
        super(AddonTemplate.CATEGORY, "packet-logger-az", "Logs packets for debugging.");
    }

    @Override
    public void onActivate() {
        info("Packet logger started");
    }

    @Override
    public void onDeactivate() {
        info("Packet logger stopped");
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        String packetName = event.packet.getClass().getSimpleName();


        if (filterInventory.get() && !isInventoryPacket(packetName)) {
            return;
        }

        info("[SEND] " + packetName);
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        String packetName = event.packet.getClass().getSimpleName();


        if (filterInventory.get() && !isInventoryPacket(packetName)) {
            return;
        }

        info("[RECV] " + packetName);
    }

    private boolean isInventoryPacket(String packetName) {
        return packetName.contains("Slot") ||
               packetName.contains("Screen") ||
               packetName.contains("Inventory") ||
               packetName.contains("Click") ||
               packetName.contains("Craft") ||
               packetName.contains("Recipe") ||
               packetName.contains("Container");
    }
}
