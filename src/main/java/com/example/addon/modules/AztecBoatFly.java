package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.VehicleMoveS2CPacket;
import net.minecraft.util.math.Vec3d;

public class AztecBoatFly extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSpeed = settings.createGroup("Speed");
    private final SettingGroup sgAntiKick = settings.createGroup("Anti-Kick");


    private final Setting<Boolean> noClip = sgGeneral.add(new BoolSetting.Builder()
        .name("no-clip")
        .description("Allows the boat to phase through blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> playerNoClip = sgGeneral.add(new BoolSetting.Builder()
        .name("player-no-clip")
        .description("Also applies noClip to the player to prevent damage.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cancelServerPackets = sgGeneral.add(new BoolSetting.Builder()
        .name("cancel-server-packets")
        .description("Cancels incoming vehicle move packets to prevent rubberband.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> lockYaw = sgGeneral.add(new BoolSetting.Builder()
        .name("lock-yaw")
        .description("Locks the boat yaw to your player yaw.")
        .defaultValue(true)
        .build()
    );


    private final Setting<Double> horizontalSpeed = sgSpeed.add(new DoubleSetting.Builder()
        .name("horizontal-speed")
        .description("Horizontal speed in blocks per second.")
        .defaultValue(10)
        .min(0)
        .sliderMax(50)
        .build()
    );

    private final Setting<Double> verticalSpeed = sgSpeed.add(new DoubleSetting.Builder()
        .name("vertical-speed")
        .description("Vertical speed in blocks per second.")
        .defaultValue(6)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> autoFall = sgSpeed.add(new BoolSetting.Builder()
        .name("auto-fall")
        .description("Boat slowly falls when not pressing jump/sprint.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> fallSpeed = sgSpeed.add(new DoubleSetting.Builder()
        .name("fall-speed")
        .description("How fast the boat falls when auto-fall is enabled.")
        .defaultValue(0.5)
        .min(0)
        .sliderMax(5)
        .visible(autoFall::get)
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
        .description("Delay in ticks between anti-kick movements.")
        .defaultValue(40)
        .min(1)
        .sliderMax(80)
        .visible(antiKick::get)
        .build()
    );


    private BoatEntity boat;
    private int delayLeft;
    private double lastY = Double.MAX_VALUE;
    private boolean wasInBoat = false;

    public AztecBoatFly() {
        super(AddonTemplate.CATEGORY, "aztec-boat-fly", "Fly with boats through blocks without rubberband.");
    }

    @Override
    public void onActivate() {
        boat = null;
        delayLeft = delay.get();
        lastY = Double.MAX_VALUE;
        wasInBoat = false;

        if (mc.player != null && mc.player.getVehicle() instanceof BoatEntity boatEntity) {
            boat = boatEntity;
            if (noClip.get()) {
                boat.noClip = true;
            }
            boat.setNoGravity(true);

            if (playerNoClip.get()) {
                mc.player.noClip = true;
            }

            wasInBoat = true;
        }
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null && mc.getNetworkHandler() != null) {

            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(), true, false
            ));
        }

        if (boat != null) {
            if (mc.getNetworkHandler() != null) {

                mc.getNetworkHandler().sendPacket(new VehicleMoveC2SPacket(
                    new Vec3d(boat.getX(), boat.getY(), boat.getZ()),
                    boat.getYaw(),
                    boat.getPitch(),
                    false
                ));
            }

            boat.noClip = false;
            boat.setNoGravity(false);
            boat = null;
        }

        if (mc.player != null) {
            mc.player.noClip = false;
        }

        wasInBoat = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;


        boolean currentlyInBoat = mc.player.getVehicle() instanceof BoatEntity;
        if (wasInBoat && !currentlyInBoat && mc.getNetworkHandler() != null) {

            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(), true, false
            ));
        }
        wasInBoat = currentlyInBoat;


        if (mc.player.getVehicle() instanceof BoatEntity boatEntity) {
            if (boat != boatEntity) {

                if (boat != null) {
                    boat.noClip = false;
                    boat.setNoGravity(false);
                }
                boat = boatEntity;
                if (noClip.get()) {
                    boat.noClip = true;
                }
                boat.setNoGravity(true);

                if (playerNoClip.get()) {
                    mc.player.noClip = true;
                }
            }
        } else {

            if (boat != null) {
                boat.noClip = false;
                boat.setNoGravity(false);
                boat = null;
            }
            mc.player.noClip = false;
            return;
        }

        if (boat == null) return;


        if (noClip.get() && !boat.noClip) {
            boat.noClip = true;
        }

        if (playerNoClip.get()) {
            mc.player.noClip = true;
        }


        if (lockYaw.get()) {
            boat.setYaw(mc.player.getYaw());
        }


        Vec3d horizontalVel = PlayerUtils.getHorizontalVelocity(horizontalSpeed.get());


        double velY = 0;
        if (Input.isPressed(mc.options.jumpKey)) {
            velY += verticalSpeed.get() / 20.0;
        } else if (Input.isPressed(mc.options.sprintKey)) {
            velY -= verticalSpeed.get() / 20.0;
        } else if (autoFall.get()) {
            velY -= fallSpeed.get() / 20.0;
        }


        boat.setVelocity(horizontalVel.x, velY, horizontalVel.z);


        if (antiKick.get()) {
            delayLeft--;
            if (delayLeft <= 0) {
                delayLeft = delay.get();
                double currentY = boat.getY();
                if (lastY != Double.MAX_VALUE && currentY >= lastY) {
                    mc.getNetworkHandler().sendPacket(new VehicleMoveC2SPacket(
                        new Vec3d(boat.getX(), lastY - 0.03130D, boat.getZ()),
                        boat.getYaw(),
                        boat.getPitch(),
                        false
                    ));
                }
                lastY = currentY;
            }
        }
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (event.packet instanceof VehicleMoveS2CPacket && cancelServerPackets.get()) {
            event.cancel();
        }
    }
}
