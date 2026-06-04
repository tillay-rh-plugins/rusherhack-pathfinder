package lol.tilley.pathfinder;

import net.minecraft.client.Minecraft;
import org.rusherhack.client.api.events.render.EventRender3D;
import org.rusherhack.client.api.feature.hud.TextHudElement;
import org.rusherhack.client.api.render.IRenderer3D;
import org.rusherhack.core.event.subscribe.Subscribe;

import java.awt.*;
import java.util.*;
import java.util.List;

import static lol.tilley.pathfinder.CrunchData.*;

public class PathfinderHudElement extends TextHudElement {
    private final List<double[]> steps = new ArrayList<>();
    private final List<String> roadNames = new ArrayList<>();
    private int currentIndex = 0;

    public PathfinderHudElement() {
        super("Pathfinder");
        this.setDescription("Highway navigation HUD");
    }

    public void setPath(List<Road> path) {
        steps.clear();
        roadNames.clear();
        currentIndex = 0;
        for (var road : path)
            for (var seg : road.segments()) {
                steps.add(new double[]{seg.p1X(), seg.p1Z()});
                roadNames.add(road.name());
            }
        if (!path.isEmpty()) {
            var last = path.get(path.size() - 1);
            var seg = last.segments().get(last.segments().size() - 1);
            steps.add(new double[]{seg.p2X(), seg.p2Z()});
            roadNames.add("destination");
        }
    }

    @Override
    public String getText() {
        if (steps.isEmpty() || currentIndex >= steps.size()) return "Arrived";
        var player = Minecraft.getInstance().player;
        if (player == null) return "No route";
        double px = player.getX(), pz = player.getZ();
        while (currentIndex < steps.size() && Math.hypot(px - steps.get(currentIndex)[0], pz - steps.get(currentIndex)[1]) < 10)
            currentIndex++;
        if (currentIndex >= steps.size()) return "Arrived!";
        var next = steps.get(currentIndex);
        double distToNext = Math.hypot(px - next[0], pz - next[1]);
        double total = distToNext;
        for (int i = currentIndex; i < steps.size() - 1; i++)
            total += Math.hypot(steps.get(i + 1)[0] - steps.get(i)[0], steps.get(i + 1)[1] - steps.get(i)[1]);
        String turn = turnAt(currentIndex);
        return String.format("In %.1f km %s %s | %.1f km remaining", distToNext / 1000.0, turn.isEmpty() ? "continue to" : "turn " + turn, roadNames.get(currentIndex), total / 1000.0);
    }

    private String turnAt(int index) {
        if (index <= 0 || index >= steps.size() - 1) return "";
        double[] prev = steps.get(index - 1), cur = steps.get(index), next = steps.get(index + 1);
        double dx1 = cur[0] - prev[0], dz1 = cur[1] - prev[1];
        double dx2 = next[0] - cur[0], dz2 = next[1] - cur[1];
        double cross = dx1 * dz2 - dz1 * dx2;
        double dot = dx1 * dx2 + dz1 * dz2;
        double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot / (Math.hypot(dx1, dz1) * Math.hypot(dx2, dz2))))));
        if (angle < 30) return "you don't need to turn";
        String side = cross < 0 ? "left on to the" : "right on to the";
        return (angle < 60 ? "slight " : angle > 120 ? "sharp " : "") + side;
    }

    @Subscribe
    private void onRender3D(EventRender3D event) {
        if (mc.player == null || mc.level == null || steps.isEmpty()) return;
        IRenderer3D renderer = event.getRenderer();
        renderer.setLineWidth(20f);
        renderer.begin(event.getMatrixStack());
        var fullSteps = PathfindCommand.getFullSteps();
        for (int i = 0; i < fullSteps.size()-1; i++) {
            renderer.drawLine(fullSteps.get(i)[0], 120d, fullSteps.get(i)[1], fullSteps.get(i+1)[0], 120d, fullSteps.get(i+1)[1], Color.BLUE.getRGB());
        }
        renderer.end();
    }
}