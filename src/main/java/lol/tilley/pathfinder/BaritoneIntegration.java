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

import static lol.tilley.pathfinder.CrunchData.getRoadNames;
import static lol.tilley.pathfinder.CrunchData.getSteps;

public class BaritoneIntegration {

    private static int lx, lz, baritoneLaunchPhase = -1, baritonePhase = -1, baritoneTimer = 0;
    private static int highwayIndex = -1, highwaySub = 0;
    private static boolean firstHighwayNode = false;

    private static final ToggleableModule rotationLockModule = (ToggleableModule) RusherHackAPI.getModuleManager().getFeature("RotationLock").get();
    private static final ToggleableModule elytraFlyModule = (ToggleableModule) RusherHackAPI.getModuleManager().getFeature("ElytraFly").get();

    private static IBaritone baritone() {
        return BaritoneAPI.getProvider().getPrimaryBaritone();
    }

    public static void reset() {
        baritonePhase = -1;
        baritoneLaunchPhase = -1;
        highwayIndex = -1;
        highwaySub = 0;
        baritone().getPathingBehavior().cancelEverything();
        rotationLockModule.setToggled(false);
        elytraFlyModule.setToggled(false);
        firstHighwayNode = false;
    }

    private static void lookToward(double x, double y) {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        float yaw = (float) Math.toDegrees(Math.atan2(y - p.getZ(), x - p.getX())) - 90f;
        p.setYRot(yaw);
        p.setYHeadRot(yaw);
    }

    public static void engage() {
        double[] entry = getSteps().get(0);
        for (int i = 0; i < getSteps().size(); i++) {
            if (!getRoadNames().get(i).equals("open nether")) { entry = getSteps().get(i); break; }
        }
        lx = (int) entry[0]; lz =  (int) entry[1]; baritoneLaunchPhase = 0; baritonePhase = 0; baritoneTimer = 0;

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
                        } else {
                            next();
                        }
                    }
                    case 1 -> {
                        if (mc.player.isFallFlying()) {
                            next();
                        } else
                            mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
                    }
                    case 2 -> {
                        int slot = InventoryUtils.findItemHotbar(Items.FIREWORK_ROCKET);
                        if (slot == -1) {
                            return;
                        }
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
                        if (baritone().getElytraProcess().isActive() || baritoneTimer > 100) {
                            next();
                        }
                    }
                    case 5 -> {
                        if (!baritone().getElytraProcess().isActive()) {
                            BaritoneAPI.getSettings().allowBreak.value = true;
                            BaritoneAPI.getSettings().allowPlace.value = true;
                            baritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(lx, 120, lz));
                            next();
                        }
                    }
                    case 6 -> {
                        if (!baritone().getCustomGoalProcess().isActive()) {
                            baritone().getPathingBehavior().cancelEverything();
                            var steps = CrunchData.getSteps();
                            highwayIndex = (int) CrunchData.closestSegment(steps, mc.player.getX(), mc.player.getZ())[0] + 1;
                            highwaySub = 0;
                            firstHighwayNode = true;
                            baritonePhase = 1;
                            baritoneLaunchPhase = -1;
                        }
                    }
                }
            }
            case 1 -> {
                var steps = CrunchData.getSteps();
                var names = CrunchData.getRoadNames();
                var p = mc.player;
                if (highwayIndex >= steps.size()) {
                    baritonePhase = -1;
                    return;
                }

                switch (highwaySub) {
                    case 0 -> {
                        rotationLockModule.setToggled(false);
                        elytraFlyModule.setToggled(false);
                        double[] t = steps.get(highwayIndex);
                        lookToward(t[0], t[1]);
                        rotationLockModule.setToggled(true);
                        elytraFlyModule.setToggled(true);
                        highwaySub = 1;
                    }
                    case 1 -> {
                        double[] target = steps.get(highwayIndex);
                        if (Math.hypot(p.getX() - target[0], p.getZ() - target[1]) < 20) {
                            boolean lastNode = highwayIndex >= steps.size() - 1 || names.get(highwayIndex).equals("open nether");
                            if (lastNode) {
                                rotationLockModule.setToggled(false);
                                elytraFlyModule.setToggled(false);
                                baritonePhase = -1;
                                return;
                            }
                            boolean nextIsExit = names.get(highwayIndex + 1).equals("open nether") || names.get(highwayIndex + 1).equals("destination");
                            if (firstHighwayNode || nextIsExit) {
                                firstHighwayNode = false;
                                highwayIndex++;
                                highwaySub = 0;
                                return;
                            }
                            rotationLockModule.setToggled(false);
                            elytraFlyModule.setToggled(false);
                            double[] cur = steps.get(highwayIndex), nxt = steps.get(highwayIndex + 1);
                            double dx = nxt[0] - cur[0], dz = nxt[1] - cur[1];
                            double len = Math.hypot(dx, dz), f = Math.min(20, len);
                            int wx = (int) (cur[0] + dx / len * f), wz = (int) (cur[1] + dz / len * f);
                            BaritoneAPI.getSettings().allowBreak.value = false;
                            baritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(wx, 120, wz));
                            highwaySub = 2;
                        }
                    }
                    case 2 -> {
                        if (!baritone().getCustomGoalProcess().isActive()) {
                            baritone().getPathingBehavior().cancelEverything();
                            double[] nxt = steps.get(highwayIndex + 1);
                            lookToward(nxt[0], nxt[1]);
                            rotationLockModule.setToggled(true);
                            elytraFlyModule.setToggled(true);
                            highwayIndex++;
                            highwaySub = 1;
                        }
                    }
                }
            }
        }
    }
}