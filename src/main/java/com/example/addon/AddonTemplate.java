package com.example.addon;

import com.example.addon.commands.CommandExample;
import com.example.addon.hud.AztecWatermarkAz;
import com.example.addon.hud.HudExample;
import com.example.addon.modules.*;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Aztec");
    public static final HudGroup HUD_GROUP = new HudGroup("Example");

    @Override
    public void onInitialize() {
        LOG.info("Initializing AztecAddon");

        // Modules
        Modules.get().add(new ModuleExample());
        Modules.get().add(new AutoRespawnAz());
        Modules.get().add(new AutoReplyAz());
        Modules.get().add(new AztecWeebHookAz());
        Modules.get().add(new EmergencyActionAz());
                Modules.get().add(new AztecBotAz());
        Modules.get().add(new PacketLoggerAz());
        Modules.get().add(new AztecAnnouncer());
        Modules.get().add(new AztecSurround());
        Modules.get().add(new AzCustomFov());
        Modules.get().add(new AutoTotemAz());
        Modules.get().add(new StashLogger());
        Modules.get().add(new AztecBoatFly());
        Modules.get().add(new AztecPhase());
        Modules.get().add(new AztecBookBan());
        Modules.get().add(new AztecPacketFly());

        // Commands
        Commands.add(new CommandExample());

        // HUD
        Hud.get().register(HudExample.INFO);
        Hud.get().register(AztecWatermarkAz.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("MeteorDevelopment", "meteor-addon-template");
    }
}
