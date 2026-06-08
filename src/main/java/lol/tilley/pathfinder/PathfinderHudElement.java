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
    private final EnumMap<Arrow, DynamicTexture> arrows = new EnumMap<>(Arrow.class);
    private static final int SIGN_BG = new Color(7, 99, 48, 255).getRGB();

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

    public void resetIndex() { currentIndex = 0; }

    @Override
    public void tick() {
        var steps = getSteps();
        if (steps.isEmpty() || mc.player == null) return;
        double px = mc.player.getX(), pz = mc.player.getZ();
        while (currentIndex < steps.size() && Math.hypot(px - steps.get(currentIndex)[0], pz - steps.get(currentIndex)[1]) < 10)
            currentIndex++;
    }

    @Override
    public void renderContent(RenderContext context, double mouseX, double mouseY) {
        IRenderer2D renderer = this.getRenderer();
        IFontRenderer fr = this.getFontRenderer();
        PoseStack stack = context.pose();
        var steps = getSteps();
        var names = getRoadNames();

        renderer.drawRoundedRectangle(0, 0, getWidth(), getHeight(), 5, true, false, 0, SIGN_BG, 0);

        if (steps.isEmpty() || mc.player == null) {
            fr.drawString("No route", 8, getHeight() / 2 - fr.getFontHeight() / 2, -1);
            return;
        }
        if (currentIndex >= steps.size()) {
            fr.drawString("Arrived!", 8, getHeight() / 2 - fr.getFontHeight() / 2, -1);
            return;
        }

        double px = mc.player.getX(), pz = mc.player.getZ();
        var next = steps.get(currentIndex);
        double distNext = Math.hypot(px - next[0], pz - next[1]);
        double total = distNext;
        for (int i = currentIndex; i < steps.size() - 1; i++)
            total += Math.hypot(steps.get(i + 1)[0] - steps.get(i)[0], steps.get(i + 1)[1] - steps.get(i)[1]);

        Arrow arrow = resolveArrow(currentIndex);
        String dist = distNext >= 1000 ? String.format("%.1f km", distNext / 1000) : String.format("%.0f m", distNext);
        String road = names.get(currentIndex);
        road = road.isEmpty() ? road : road.substring(0, 1).toUpperCase() + road.substring(1);

        int iconSize = 26;
        double pad = 6;
        double textX = pad;

        if (arrow != null) {
            DynamicTexture tex = arrows.get(arrow);
            if (tex != null) {
                renderer.drawTextureRectangle(tex.getId(), iconSize, iconSize, pad, pad, iconSize, iconSize, 0);
                textX = pad + iconSize + 4;
            }
        }

        stack.pushPose();
        stack.translate(textX, pad, 0);
        stack.scale(1.5f, 1.5f, 1);
        fr.drawString(dist, 0, 0, -1);
        stack.popPose();

        fr.drawString(road, textX, pad + fr.getFontHeight() * 1.5f + 4, new Color(210, 210, 210).getRGB());

        String remaining = total >= 1000 ? String.format("%.1f km remaining", total / 1000) : String.format("%.0f m remaining", total);
        stack.pushPose();
        stack.translate(pad, getHeight() - fr.getFontHeight() * 0.7f - 3, 0);
        stack.scale(0.7f, 0.7f, 1);
        fr.drawString(remaining, 0, 0, Color.LIGHT_GRAY.getRGB());
        stack.popPose();
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
    public double getHeight() { return 55; }

    @Override
    public boolean shouldDrawBackground() { return false; }

    @Subscribe
    private void onRender3D(EventRender3D event) {
        var steps = getSteps();
        if (mc.player == null || mc.level == null || steps.isEmpty()) return;
        IRenderer3D renderer = event.getRenderer();
        renderer.begin(event.getMatrixStack());
        renderer.setLineWidth(5f);
        for (int i = 0; i < steps.size() - 1; i++) {
            renderer.drawLine(steps.get(i)[0], 120d, steps.get(i)[1], steps.get(i + 1)[0], 120d, steps.get(i + 1)[1], Color.BLUE.getRGB());
        }
        renderer.end();
    }
}