package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.GetFovEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;

public class AzCustomFov extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDynamic = settings.createGroup("Dynamic FOV");
    private final SettingGroup sgEffects = settings.createGroup("Effects & States");


    private final Setting<Double> baseFov = sgGeneral.add(new DoubleSetting.Builder()
        .name("base-fov")
        .description("Base field of view.")
        .defaultValue(90.0)
        .min(30.0)
        .max(150.0)
        .sliderRange(30.0, 150.0)
        .build()
    );

    private final Setting<Double> minFov = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-fov")
        .description("Minimum FOV limit.")
        .defaultValue(30.0)
        .min(30.0)
        .sliderRange(30.0, 150.0)
        .build()
    );

    private final Setting<Double> maxFov = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-fov")
        .description("Maximum FOV limit.")
        .defaultValue(150.0)
        .min(30.0)
        .sliderRange(30.0, 150.0)
        .build()
    );

    private final Setting<Double> smoothing = sgGeneral.add(new DoubleSetting.Builder()
        .name("smoothing")
        .description("How smooth the FOV transitions are (lower = smoother).")
        .defaultValue(0.1)
        .min(0.01)
        .sliderRange(0.01, 1.0)
        .build()
    );


    private final Setting<Boolean> dynamicFov = sgDynamic.add(new BoolSetting.Builder()
        .name("dynamic-fov")
        .description("Enable FOV changes based on movement and states.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> sprintFov = sgDynamic.add(new DoubleSetting.Builder()
        .name("sprint-fov")
        .description("FOV when sprinting.")
        .defaultValue(100.0)
        .min(30.0)
        .sliderRange(30.0, 150.0)
        .visible(dynamicFov::get)
        .build()
    );

    private final Setting<Double> flyingFov = sgDynamic.add(new DoubleSetting.Builder()
        .name("flying-fov")
        .description("FOV when flying.")
        .defaultValue(110.0)
        .min(30.0)
        .sliderRange(30.0, 150.0)
        .visible(dynamicFov::get)
        .build()
    );

    private final Setting<Double> swimmingFov = sgDynamic.add(new DoubleSetting.Builder()
        .name("swimming-fov")
        .description("FOV when swimming.")
        .defaultValue(80.0)
        .min(30.0)
        .sliderRange(30.0, 150.0)
        .visible(dynamicFov::get)
        .build()
    );

    private final Setting<Double> speedFov = sgDynamic.add(new DoubleSetting.Builder()
        .name("speed-effect-fov")
        .description("FOV when having Speed effect.")
        .defaultValue(120.0)
        .min(30.0)
        .sliderRange(30.0, 150.0)
        .visible(dynamicFov::get)
        .build()
    );

    private final Setting<Double> slownessFov = sgDynamic.add(new DoubleSetting.Builder()
        .name("slowness-effect-fov")
        .description("FOV when having Slowness effect.")
        .defaultValue(70.0)
        .min(30.0)
        .sliderRange(30.0, 150.0)
        .visible(dynamicFov::get)
        .build()
    );


    private final Setting<Boolean> itemFov = sgEffects.add(new BoolSetting.Builder()
        .name("item-fov")
        .description("Modify FOV when using items (bow, spyglass, etc.).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> bowFov = sgEffects.add(new DoubleSetting.Builder()
        .name("bow-fov")
        .description("FOV when drawing a bow.")
        .defaultValue(60.0)
        .min(30.0)
        .sliderRange(30.0, 150.0)
        .visible(itemFov::get)
        .build()
    );

    private final Setting<Double> spyglassFov = sgEffects.add(new DoubleSetting.Builder()
        .name("spyglass-fov")
        .description("FOV when using spyglass.")
        .defaultValue(20.0)
        .min(10.0)
        .sliderRange(10.0, 150.0)
        .visible(itemFov::get)
        .build()
    );

    private final Setting<Boolean> ignoreVanillaFov = sgEffects.add(new BoolSetting.Builder()
        .name("ignore-vanilla-fov")
        .description("Ignore the vanilla FOV setting and use only this module.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> reduceMotionSickness = sgEffects.add(new BoolSetting.Builder()
        .name("reduce-motion-sickness")
        .description("Reduces FOV changes to prevent motion sickness.")
        .defaultValue(false)
        .build()
    );


    private double currentFov = 90.0;
    private double targetFov = 90.0;

    public AzCustomFov() {
        super(AddonTemplate.CATEGORY, "az-custom-fov", "Advanced custom FOV with dynamic options.");
    }

    @Override
    public void onActivate() {
        currentFov = baseFov.get();
        targetFov = baseFov.get();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        targetFov = calculateTargetFov();
        targetFov = clampFov(targetFov);


        double diff = targetFov - currentFov;
        if (Math.abs(diff) > 0.01) {
            currentFov += diff * smoothing.get();
        } else {
            currentFov = targetFov;
        }
    }

    private double calculateTargetFov() {
        double fov = baseFov.get();

        if (!dynamicFov.get()) return fov;


        if (mc.player.hasStatusEffect(StatusEffects.SPEED)) {
            fov = speedFov.get();
        } else if (mc.player.hasStatusEffect(StatusEffects.SLOWNESS)) {
            fov = slownessFov.get();
        }


        if (mc.player.getAbilities().flying) {
            fov = flyingFov.get();
        } else if (mc.player.isSwimming()) {
            fov = swimmingFov.get();
        } else if (mc.player.isSprinting() && !mc.player.hasStatusEffect(StatusEffects.SPEED)) {
            fov = sprintFov.get();
        }


        if (itemFov.get()) {
            if (mc.player.isUsingItem()) {
                var item = mc.player.getMainHandStack().getItem();
                var offhandItem = mc.player.getOffHandStack().getItem();

                if (item == Items.BOW || item == Items.CROSSBOW || offhandItem == Items.BOW || offhandItem == Items.CROSSBOW) {
                    fov = bowFov.get();
                } else if (item == Items.SPYGLASS || offhandItem == Items.SPYGLASS) {
                    fov = spyglassFov.get();
                }
            }
        }


        if (reduceMotionSickness.get()) {
            double base = baseFov.get();
            double maxChange = 15.0;
            if (fov > base + maxChange) fov = base + maxChange;
            if (fov < base - maxChange) fov = base - maxChange;
        }

        return fov;
    }

    private double clampFov(double fov) {
        double min = minFov.get();
        double max = maxFov.get();
        return Math.max(min, Math.min(max, fov));
    }

    public double getCurrentFov() {
        return currentFov;
    }

    public boolean shouldIgnoreVanillaFov() {
        return ignoreVanillaFov.get();
    }

    @EventHandler
    private void onGetFov(GetFovEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (ignoreVanillaFov.get()) {

            event.fov = (float) getCurrentFov();
        } else {

            double vanillaFov = event.fov;
            double ourFov = getCurrentFov();
            double baseFovValue = baseFov.get();


            double factor = ourFov / baseFovValue;
            event.fov = (float) (vanillaFov * factor);
        }
    }
}
