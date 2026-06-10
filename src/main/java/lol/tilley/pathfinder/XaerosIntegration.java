package lol.tilley.pathfinder;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaeroplus.Globals;
import xaeroplus.feature.render.DrawFeature;
import xaeroplus.feature.render.DrawFeatureFactory;
import xaeroplus.feature.render.line.Line;
import xaeroplus.feature.waypoint.WaypointAPI;
import xaeroplus.mixin.client.AccessorWaypointSet;
import xaeroplus.util.ColorHelper;

import java.util.ArrayList;
import java.util.List;

public class XaerosIntegration {
    private static final String TRAVELED_ID = "PathfinderTraveled";
    private static final String REMAINING_ID = "PathfinderRemaining";
    private static boolean registered = false;
    private static DrawFeature traveledDark, traveledLight, remainingDark, remainingLight;

    private static int splitSeg;
    private static double splitT;

    private static void updateSplit() {
        var steps = CrunchData.getSteps();
        var player = Minecraft.getInstance().player;
        if (player == null || steps.size() < 2) { splitSeg = 0; splitT = 0; return; }
        double px = player.getX(), pz = player.getZ();
        double best = Double.MAX_VALUE;
        for (int i = 0; i < steps.size() - 1; i++) {
            double ax = steps.get(i)[0], az = steps.get(i)[1];
            double dx = steps.get(i + 1)[0] - ax, dz = steps.get(i + 1)[1] - az;
            double len2 = dx * dx + dz * dz;
            double t = len2 < 1e-20 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / len2));
            double d = Math.hypot(px - (ax + t * dx), pz - (az + t * dz));
            if (d < best) { best = d; splitSeg = i; splitT = t; }
        }
    }

    private static List<Line> buildLines(List<double[]> steps, int scale, boolean traveled) {
        var lines = new ArrayList<Line>();
        double ax = steps.get(splitSeg)[0], az = steps.get(splitSeg)[1];
        double bx = steps.get(splitSeg + 1)[0], bz = steps.get(splitSeg + 1)[1];
        int mx = (int) (ax + splitT * (bx - ax)), mz = (int) (az + splitT * (bz - az));
        int start = traveled ? 0 : splitSeg + 1, end = traveled ? splitSeg : steps.size() - 1;
        if (!traveled) lines.add(new Line(mx * scale, mz * scale, (int) steps.get(splitSeg + 1)[0] * scale, (int) steps.get(splitSeg + 1)[1] * scale));
        for (int i = start; i < end; i++)
            lines.add(new Line((int) steps.get(i)[0] * scale, (int) steps.get(i)[1] * scale, (int) steps.get(i + 1)[0] * scale, (int) steps.get(i + 1)[1] * scale));
        if (traveled) lines.add(new Line((int) steps.get(splitSeg)[0] * scale, (int) steps.get(splitSeg)[1] * scale, mx * scale, mz * scale));
        return lines;
    }

    private static DrawFeature makeFeature(String id, boolean traveled, int color, float width) {
        return DrawFeatureFactory.lines(
                id,
                (wx, wz, size, dim) -> {
                    var steps = CrunchData.getSteps();
                    if (steps.size() < 2) return List.of();
                    updateSplit();
                    return buildLines(steps, Level.NETHER.equals(dim) ? 1 : 8, traveled);
                },
                () -> color,
                () -> width,
                200
        );
    }

    public static void register() {
        if (registered) return;
        traveledDark    = makeFeature(TRAVELED_ID  + "Dark",  true,  ColorHelper.getColor(80,  80,  80,  255), 1.25f);
        traveledLight   = makeFeature(TRAVELED_ID  + "Light", true,  ColorHelper.getColor(160, 160, 160, 255), 1f);
        remainingDark   = makeFeature(REMAINING_ID + "Dark",  false, ColorHelper.getColor(70,   104,  209, 255), 1.25f);
        remainingLight  = makeFeature(REMAINING_ID + "Light", false, ColorHelper.getColor(75, 148, 248, 255), 1f);
        Globals.drawManager.registry().register(traveledDark);
        Globals.drawManager.registry().register(traveledLight);
        Globals.drawManager.registry().register(remainingDark);
        Globals.drawManager.registry().register(remainingLight);
        registered = true;
    }

    public static void unregister() {
        if (!registered) return;
        Globals.drawManager.registry().unregister(TRAVELED_ID  + "Dark");
        Globals.drawManager.registry().unregister(TRAVELED_ID  + "Light");
        Globals.drawManager.registry().unregister(REMAINING_ID + "Dark");
        Globals.drawManager.registry().unregister(REMAINING_ID + "Light");
        traveledDark = traveledLight = remainingDark = remainingLight = null;
        registered = false;
    }

    public static String[] getWaypointNames(boolean includeDummy) {
        WaypointSet set = WaypointAPI.getCurrentWaypointSet();
        if (set == null) return new String[]{"No waypoints"};

        String[] names = ((AccessorWaypointSet) set).getList()
                .stream()
                .map(Waypoint::getName)
                .toArray(String[]::new);
        if (includeDummy) {
            String[] withNone = new String[names.length + 1];
            withNone[0] = "None";
            System.arraycopy(names, 0, withNone, 1, names.length);
            return withNone;
        }
        return names.length == 0 ? new String[]{"No waypoints"} : names;
    }
    public static List<Waypoint> getWaypointList() {
        WaypointSet set = WaypointAPI.getCurrentWaypointSet();
        if (set == null) return new ArrayList<>();
        return ((AccessorWaypointSet) set).getList();
    }

    public static int[] getWaypointXZ(String name) {
        List<Waypoint> list = getWaypointList();
        for (Waypoint wp : list) {
            if (wp.getName().equals(name)) return new int[]{wp.getX(), wp.getZ()};
        }
        return null;
    }
}