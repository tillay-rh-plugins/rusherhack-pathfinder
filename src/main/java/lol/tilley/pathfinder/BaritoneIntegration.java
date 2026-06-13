package lol.tilley.pathfinder;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.client.api.utils.InventoryUtils;

public class BaritoneIntegration {

    private static int lx, lz, baritoneLaunchPhase = -1, baritonePhase = -1, baritoneTimer = 0;
    private static ToggleableModule rotationLockModule = (ToggleableModule) RusherHackAPI.getModuleManager().getFeature("RotationLock").get();
    private static ToggleableModule elytraFlyModule = (ToggleableModule) RusherHackAPI.getModuleManager().getFeature("ElytraFly").get();

    private static IBaritone baritone() {
        return BaritoneAPI.getProvider().getPrimaryBaritone();
    }

    public static void stop() {
        baritone().getPathingBehavior().cancelEverything();
    }

    private static void lookToward(double x, double y) {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        float yaw = (float) Math.toDegrees(Math.atan2(y - p.getZ(), x - p.getX())) - 90f;
        p.setYRot(yaw);
        p.setYHeadRot(yaw);
    }

    public static void engage(int x, int z) {
        lx = x; lz = z; baritoneLaunchPhase = 0; baritonePhase = 0; baritoneTimer = 0;
    }

    private static void next() { baritoneLaunchPhase++; baritoneTimer = 0; }

    public static void baritoneTick() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        switch (baritonePhase) {
            case 0 -> {
                baritoneTimer++;
                switch (baritoneLaunchPhase) {
                    case 0 -> {
                        if (mc.player.onGround()) {
                            var d = mc.player.getDeltaMovement();
                            mc.player.setDeltaMovement(d.x, 0.42, d.z);
                            mc.player.hasImpulse = true;
                        } else next();
                    }
                    case 1 -> {
                        if (mc.player.isFallFlying()) next();
                        else
                            mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
                    }
                    case 2 -> {
                        int slot = InventoryUtils.findItemHotbar(Items.FIREWORK_ROCKET);
                        if (slot == -1) return;
                        mc.player.setXRot(-35f);
                        InventoryUtils.setHotbarSlot(slot);
                        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                        next();
                    }
                    case 3 -> {
                        if (baritoneTimer > 10) {
                            BaritoneAPI.getSettings().elytraTermsAccepted.value = true;
                            baritone().getElytraProcess().pathTo(new BlockPos(lx, 120, lz));
                            next();
                        }
                    }
                    case 4 -> {
                        if (baritone().getElytraProcess().isActive() || baritoneTimer > 100) next();
                    }
                    case 5 -> {
                        if (!baritone().getElytraProcess().isActive()) {
                            BaritoneAPI.getSettings().allowBreak.value = true;
                            baritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(lx, 120, lz));
                            baritoneLaunchPhase = -1;
                        }
                    }
                }
            }
            case 1 -> {
                if (baritoneTimer > 100) next();
            }
        }
    }
}