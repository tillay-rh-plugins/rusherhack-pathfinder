package lol.tilley.pathfinder;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.system.MemoryUtil;
import org.rusherhack.client.api.events.render.EventRender3D;
import org.rusherhack.client.api.feature.hud.ResizeableHudElement;
import org.rusherhack.client.api.render.IRenderer2D;
import org.rusherhack.client.api.render.IRenderer3D;
import org.rusherhack.client.api.render.RenderContext;
import org.rusherhack.client.api.render.font.IFontRenderer;
import org.rusherhack.core.event.subscribe.Subscribe;
import org.rusherhack.core.setting.BooleanSetting;
import org.rusherhack.client.api.events.client.EventUpdate;


import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.EnumMap;

import static lol.tilley.pathfinder.CrunchData.*;

public class PathfinderHudElement extends ResizeableHudElement {
    private int currentIndex = 0;
    public static PathfinderHudElement INSTANCE;

    private enum Arrow { SHARP_LEFT, LEFT, SLIGHT_LEFT, OFF_LEFT, SHARP_RIGHT, RIGHT, SLIGHT_RIGHT, OFF_RIGHT, DEST }
    private final java.util.ArrayDeque<Double> speedSamples = new java.util.ArrayDeque<>();
    private final EnumMap<Arrow, DynamicTexture> arrows = new EnumMap<>(Arrow.class);
    private boolean arrived = false;
    private boolean finished = false;
    private final java.util.ArrayDeque<Double> destDistSamples = new java.util.ArrayDeque<>();

    private static final int SIGN_BG = new Color(7, 99, 48, 255).getRGB();
    private static final int SIGN_HEIGHT = 52;
    private static final int INFO_HEIGHT = 32;


    private final BooleanSetting renderTracer = new BooleanSetting("RenderTracer", "Render a tracer on the highway outlining the route", true);

    public PathfinderHudElement() {
        super("Pathfinder");
        this.setDescription("Highway navigation HUD");
        INSTANCE = this;
        for (Arrow a : Arrow.values()) {
            try {
                DynamicTexture tex = loadTexture("/arrows/" + a.name().toLowerCase().replace('_', '-') + ".png");
                if (tex != null) arrows.put(a, tex);
            } catch (IOException e) {
                this.getLogger().error("Failed to load arrow texture: " + a.name());
            }
        }
    }

    private DynamicTexture loadTexture(String path) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) return null;
            byte[] bytes = is.readAllBytes();
            ByteBuffer buf = MemoryUtil.memAlloc(bytes.length);
            try {
                buf.put(bytes).rewind();
                DynamicTexture tex = new DynamicTexture(NativeImage.read(buf));
                tex.upload();
                tex.setFilter(true, true);
                return tex;
            } finally {
                MemoryUtil.memFree(buf);
            }
        }
    }

    private void drawCentered(PoseStack stack, IFontRenderer fr, String text, double colX, double colW, double y, float scale, int color) {
        double x = colX + (colW - fr.getStringWidth(text) * scale) / 2;
        stack.pushPose();
        stack.translate(x, y, 0);
        stack.scale(scale, scale, 1);
        fr.drawString(text, 0, 0, color);
        stack.popPose();
    }

    public void resetIndex() {
        currentIndex = 0;
        arrived = false;
        finished = false;
        destDistSamples.clear();
    }

    @Subscribe
    private void onUpdate(EventUpdate event) {
        var steps = getSteps();
        if (steps.isEmpty() || mc.player == null) return;
        double px = mc.player.getX(), pz = mc.player.getZ();
        double[] cs = CrunchData.closestSegment(steps, px, pz);
        currentIndex = (int) cs[0] + 1;
        double spd = Math.hypot(mc.player.getDeltaMovement().x, mc.player.getDeltaMovement().z) * 20;
        speedSamples.addLast(spd);
        if (speedSamples.size() > 16 * 20) speedSamples.pollFirst();
        double[] dest = steps.get(steps.size() - 1);
        double dd = Math.hypot(px - dest[0], pz - dest[1]);
        if (dd < 20) {
            destDistSamples.addLast(dd);
            if (destDistSamples.size() > 20) {
                double prev = destDistSamples.pollFirst();
                if (dd > prev) arrived = true;
            }
        } else {
            destDistSamples.clear();
            if (arrived) finished = true;
        }
        BaritoneIntegration.baritoneTick();
    }

    @Override
    public void renderContent(RenderContext context, double mouseX, double mouseY) {

        var steps = getSteps();
        var names = getRoadNames();
        if (steps.isEmpty() || mc.player == null || finished) return;

        IRenderer2D renderer = this.getRenderer();
        IFontRenderer fr = this.getFontRenderer();
        PoseStack stack = context.pose();

        renderer.drawRoundedRectangle(0, 0, getWidth(), getHeight(), 5, true, false, 0, Color.BLACK.getRGB(), 0);
        renderer.drawRoundedRectangle(0, 0, getWidth(), SIGN_HEIGHT, 5, true, false, 0, SIGN_BG, 0);

        double px = mc.player.getX(), pz = mc.player.getZ();
        double distNext = Math.hypot(px - steps.get(currentIndex)[0], pz - steps.get(currentIndex)[1]);
        double total = distNext;
        for (int i = currentIndex; i < steps.size() - 1; i++)
            total += Math.hypot(steps.get(i + 1)[0] - steps.get(i)[0], steps.get(i + 1)[1] - steps.get(i)[1]);

        Arrow arrow = resolveArrow(currentIndex);
        int pad = 6, iconSize = 26, textX = pad;
        if (arrow != null) {
            DynamicTexture tex = arrows.get(arrow);
            if (tex != null) {
                renderer.drawTextureRectangle(tex.getId(), iconSize, iconSize, pad, (SIGN_HEIGHT - iconSize) / 2.0, iconSize, iconSize, 0);
                textX = pad + iconSize + 4;
            }
        }

        String dist = distNext >= 1000 ? String.format("%.1f km", distNext / 1000) : String.format("%.0f m", distNext);
        String road = names.get(currentIndex);
        road = road.isEmpty() ? road : Character.toUpperCase(road.charAt(0)) + road.substring(1);

        if (arrived) {
            dist = "Arrived";
            double[] d = steps.get(steps.size() - 1);
            road = "(" + (int) d[0] + ", " + (int) d[1] + ")";
        }

        double distH = fr.getFontHeight() * 1.5, roadH = fr.getFontHeight();
        double signTextY = (SIGN_HEIGHT - distH - 3 - roadH) / 2.0;

        stack.pushPose();
        stack.translate(textX, signTextY, 0);
        stack.scale(1.5f, 1.5f, 1);
        fr.drawString(dist, 0, 0, -1);
        stack.popPose();
        fr.drawString(road, textX, signTextY + distH + 3, new Color(210, 210, 210).getRGB());

        double speed = speedSamples.isEmpty() ? 0 : speedSamples.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double etaSecs = speed > 0.5 ? total / speed : -1;

        String[] nums  = {
                etaSecs > 0 ? java.time.LocalTime.now().plusSeconds((long) etaSecs).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) : "--:--",
                etaSecs > 0 ? (etaSecs < 3600 ? String.valueOf((int)(etaSecs / 60)) : String.format("%.0f", etaSecs / 3600)) : "--",
                total >= 1000 ? String.format("%.1f", total / 1000) : String.format("%.0f", total)
        };
        String[] units = { "arrival", etaSecs > 0 && etaSecs < 3600 ? "min" : "hr", total >= 1000 ? "km" : "m" };
        int green = new Color(0, 220, 90).getRGB(), gray = Color.LIGHT_GRAY.getRGB();
        int[] numColors  = { -1, green, -1 };
        int[] unitColors = { gray, green, gray };

        double colW = getWidth() / 3.0;
        double numH = fr.getFontHeight() * 1.2, lblH = fr.getFontHeight() * 0.75;
        double infoTextY = SIGN_HEIGHT + (INFO_HEIGHT - numH - 2 - lblH) / 2.0;

        for (int i = 0; i < 3; i++) {
            drawCentered(stack, fr, nums[i],  colW * i, colW, infoTextY,1.2f,  numColors[i]);
            drawCentered(stack, fr, units[i], colW * i, colW, infoTextY + numH + 2, 0.75f, unitColors[i]);
        }
    }

    private Arrow resolveArrow(int index) {
        var steps = getSteps();
        var names = getRoadNames();
        if (names.get(index).equals("destination")) return Arrow.DEST;
        if (names.get(index).equals("open nether")) {
            if (index <= 0) return Arrow.OFF_RIGHT;
            double[] prev = steps.get(index - 1), cur = steps.get(index);
            double[] nxt = index < steps.size() - 1 ? steps.get(index + 1) : cur;
            double cross = (cur[0] - prev[0]) * (nxt[1] - cur[1]) - (cur[1] - prev[1]) * (nxt[0] - cur[0]);
            return cross < 0 ? Arrow.OFF_LEFT : Arrow.OFF_RIGHT;
        }
        if (index <= 0 || index >= steps.size() - 1) return null;
        double[] prev = steps.get(index - 1), cur = steps.get(index), nxt = steps.get(index + 1);
        double dx1 = cur[0] - prev[0], dz1 = cur[1] - prev[1];
        double dx2 = nxt[0] - cur[0], dz2 = nxt[1] - cur[1];
        double cross = dx1 * dz2 - dz1 * dx2;
        double dot = dx1 * dx2 + dz1 * dz2;
        double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot / (Math.hypot(dx1, dz1) * Math.hypot(dx2, dz2))))));
        if (angle < 30) return null;
        if (cross < 0) return angle < 60 ? Arrow.SLIGHT_LEFT : angle > 120 ? Arrow.SHARP_LEFT : Arrow.LEFT;
        return angle < 60 ? Arrow.SLIGHT_RIGHT : angle > 120 ? Arrow.SHARP_RIGHT : Arrow.RIGHT;
    }

    @Override
    public double getWidth() { return 180; }

    @Override
    public double getHeight() { return SIGN_HEIGHT + INFO_HEIGHT; }

    @Override
    public boolean shouldDrawBackground() { return false; }

    @Subscribe
    private void onRender3D(EventRender3D event) {
        var steps = getSteps();
        if (steps.isEmpty() || mc.player == null || finished || !renderTracer.getValue()) {
            XaerosIntegration.unregister();
            return;
        }
        IRenderer3D renderer = event.getRenderer();
        renderer.begin(event.getMatrixStack());
        for (int i = Math.max(0, currentIndex - 1); i < steps.size() - 1; i++) {
            double[] a = steps.get(i), b = steps.get(i + 1);
            double dist = Math.hypot(b[0] - a[0], b[1] - a[1]);
            if (dist <= 100) {
                renderer.drawLine(a[0], 120d, a[1], b[0], 120d, b[1], Color.BLUE.getRGB());
            } else {
                int chunks = (int) Math.ceil(dist / 100);
                for (int j = 0; j < chunks; j++) {
                    double t1 = (double) j / chunks, t2 = (double) (j + 1) / chunks;
                    renderer.drawLine(
                            lerp(a[0], b[0], t1), 120d, lerp(a[1], b[1], t1),
                            lerp(a[0], b[0], t2), 120d, lerp(a[1], b[1], t2),
                            Color.BLUE.getRGB()
                    );
                }
            }
        }
        renderer.end();
    }
}