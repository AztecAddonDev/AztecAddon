package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;

public class AztecPacketFly extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAntiKick = settings.createGroup("Anti-Kick");


    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Horizontal speed in blocks per second.")
        .defaultValue(10)
        .min(0.1)
        .max(50)
        .sliderMax(20)
        .build()
    );

    private final Setting<Double> verticalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("vertical-speed")
        .description("Vertical speed in blocks per second.")
        .defaultValue(6)
        .min(0.1)
        .max(20)
        .sliderMax(10)
        .build()
    );

    private final Setting<Boolean> phase = sgGeneral.add(new BoolSetting.Builder()
        .name("phase")
        .description("Phase through blocks while flying.")
        .defaultValue(true)
        .build()
    );

    private final Setting<DescendKey> descendKey = sgGeneral.add(new EnumSetting.Builder<DescendKey>()
        .name("descend-key")
        .description("Key to descend while flying.")
        .defaultValue(DescendKey.Sneak)
        .build()
    );


    private final Setting<Boolean> antiKick = sgAntiKick.add(new BoolSetting.Builder()
        .name("anti-kick")
        .description("Prevents the server from kicking you for flying.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> delay = sgAntiKick.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay in ticks between anti-kick packets.")
        .defaultValue(40)
        .min(1)
        .max(100)
        .sliderMax(80)
        .visible(antiKick::get)
        .build()
    );


    private int delayLeft;
    private double lastY = Double.MAX_VALUE;
    private int ticksSinceActivation = 0;


    private static final int SPAWN_GRACE_PERIOD = 40; 

    public AztecPacketFly() {
        super(AddonTemplate.CATEGORY, "aztec-packet-fly", "Fly using packets with built-in phase through blocks.");
    }

    @Override
    public void onActivate() {
        delayLeft = delay.get();
        lastY = Double.MAX_VALUE;
        ticksSinceActivation = 0;

        if (mc.player != null) {
            mc.player.setNoGravity(true);
            if (phase.get()) {
                mc.player.noClip = true;
            }
        }
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null && mc.getNetworkHandler() != null) {

            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(), true, false
            ));


            mc.player.setNoGravity(false);
            mc.player.noClip = false;
        }
    }


    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket) {



            if (ticksSinceActivation < SPAWN_GRACE_PERIOD) {
                return; 
            }


            event.cancel();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;


        ticksSinceActivation++;


        if (mc.player.hasVehicle()) return;


        mc.player.setNoGravity(true);
        if (phase.get()) {
            mc.player.noClip = true;
        }


        mc.player.setVelocity(0, 0, 0);


        double velX = 0, velZ = 0;
        float yaw = mc.player.getYaw();
        double forward = 0, strafe = 0;

        if (Input.isPressed(mc.options.forwardKey)) forward += 1;
        if (Input.isPressed(mc.options.backKey)) forward -= 1;
        if (Input.isPressed(mc.options.leftKey)) strafe += 1;
        if (Input.isPressed(mc.options.rightKey)) strafe -= 1;

        if (forward != 0 || strafe != 0) {
            double len = Math.sqrt(forward * forward + strafe * strafe);
            forward /= len;
            strafe /= len;

            double spd = speed.get() / 20.0;
            double sin = Math.sin(Math.toRadians(yaw));
            double cos = Math.cos(Math.toRadians(yaw));

            velX = (strafe * cos - forward * sin) * spd;
            velZ = (forward * cos + strafe * sin) * spd;
        }


        double velY = 0;
        if (Input.isPressed(mc.options.jumpKey)) {
            velY += verticalSpeed.get() / 20.0;
        }


        boolean descendPressed = descendKey.get() == DescendKey.Sneak
            ? Input.isPressed(mc.options.sneakKey)
            : Input.isPressed(mc.options.sprintKey);

        if (descendPressed) {
            velY -= verticalSpeed.get() / 20.0;
        }


        double newX = mc.player.getX() + velX;
        double newY = mc.player.getY() + velY;
        double newZ = mc.player.getZ() + velZ;


        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
            newX, newY, newZ, true, false
        ));


        mc.player.setPos(newX, newY, newZ);


        if (antiKick.get()) {
            delayLeft--;
            if (delayLeft <= 0) {
                delayLeft = delay.get();
                if (lastY != Double.MAX_VALUE && newY >= lastY) {
                    mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        newX, lastY - 0.03130D, newZ, true, false
                    ));
                }
                lastY = newY;
            }
        }
    }

    public enum DescendKey {
        Sneak,
        Sprint
    }
}
