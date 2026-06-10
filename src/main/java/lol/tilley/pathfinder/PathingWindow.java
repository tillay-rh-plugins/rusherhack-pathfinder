package lol.tilley.pathfinder;

import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.rusherhack.client.api.feature.window.ResizeableWindow;
import org.rusherhack.client.api.ui.window.content.ComboContent;
import org.rusherhack.client.api.ui.window.content.PaddingContent;
import org.rusherhack.client.api.ui.window.content.component.ButtonComponent;
import org.rusherhack.client.api.ui.window.content.component.ComboBoxComponent;
import org.rusherhack.client.api.ui.window.content.component.TextComponent;
import org.rusherhack.client.api.ui.window.content.component.TextFieldComponent;
import org.rusherhack.client.api.ui.window.view.RichTextView;
import org.rusherhack.client.api.ui.window.view.SimpleView;
import org.rusherhack.client.api.ui.window.view.TabbedView;
import org.rusherhack.client.api.ui.window.view.WindowView;
import org.rusherhack.core.utils.Timer;

import java.util.List;
import java.util.function.Supplier;

import static lol.tilley.pathfinder.CrunchData.*;

public class PathingWindow extends ResizeableWindow {
    private final TabbedView rootView;
    private final RichTextView directionsView;
    private final TextFieldComponent startXField, startZField, endXField, endZField;

    public PathingWindow() {
        super("Rusher Maps", 200, 100, 400, 300);
        this.setMinWidth(300);
        this.setMinHeight(200);

        TextFieldComponent[] f = new TextFieldComponent[4];
        f[0] = makeTabField(() -> f[1], () -> f[3]);
        f[1] = makeTabField(() -> f[2], () -> f[0]);
        f[2] = makeTabField(() -> f[3], () -> f[1]);
        f[3] = makeTabField(() -> f[0], () -> f[2]);
        startXField = f[0]; startZField = f[1]; endXField = f[2]; endZField = f[3];

        final ComboContent startPosCombo = new ComboContent(this);
        startPosCombo.addContent(new TextComponent(this, "X:"));
        startPosCombo.addContent(startXField);
        startPosCombo.addContent(new TextComponent(this, " Z:"));
        startPosCombo.addContent(startZField);

        final ComboContent endPosCombo = new ComboContent(this);
        endPosCombo.addContent(new TextComponent(this, "X:"));
        endPosCombo.addContent(endXField);
        endPosCombo.addContent(new TextComponent(this, " Z:"));
        endPosCombo.addContent(endZField);


        final ComboContent endPosLabelCombo = new ComboContent(this);
        endPosLabelCombo.addContent(new TextComponent(this, "Destination: "));
        String[] names = XaerosIntegration.getWaypointNames(true);
        ComboBoxComponent waypointDropdown = new ComboBoxComponent(this, names, 0, (selected) -> {
            if (selected.equals("None")) {
                endXField.setValue("");
                endZField.setValue("");
                return;
            }
            int[] coords = XaerosIntegration.getWaypointXZ(selected);
            if (coords != null) {
                endXField.setValue(String.valueOf(coords[0]));
                endZField.setValue(String.valueOf(coords[1]));
            }
        });
        endPosLabelCombo.addContent(new PaddingContent(this,12,1), ComboContent.AnchorSide.RIGHT);
        endPosLabelCombo.addContent(waypointDropdown, ComboContent.AnchorSide.RIGHT);
        endPosLabelCombo.addContent(new TextComponent(this, "Choose waypoint: "), ComboContent.AnchorSide.RIGHT);

        final ComboContent buttonCombo = new ComboContent(this);
        ButtonComponent pathButton = new ButtonComponent(this, "Path", this::calculatePath);
        ButtonComponent copyButton = new ButtonComponent(this, "Copy Points", this::copyToClipboard);
        ButtonComponent resetButton = new ButtonComponent(this, "Reset", this::resetPath);
        pathButton.setWidth(60);
        copyButton.setWidth(60);
        resetButton.setWidth(60);
        buttonCombo.addContent(pathButton);
        buttonCombo.addContent(copyButton);
        buttonCombo.addContent(resetButton);

        this.directionsView = new RichTextView("Directions", this) {
            @Override
            protected boolean shouldAutoJumpToBottom() { return false; }
        };

        SimpleView mainView = new SimpleView("Navigation", this, List.of(
                new TextComponent(this, "Start (leave blank for current position): "),
                startPosCombo,
                endPosLabelCombo,
                endPosCombo,
                buttonCombo,
                directionsView
        ));

        this.rootView = new TabbedView(this, List.of(mainView));
    }

    private TextFieldComponent makeTabField(Supplier<TextFieldComponent> forward, Supplier<TextFieldComponent> backward) {
        return new TextFieldComponent(this, "", 80, false) {
            private final Timer focusTimer = new Timer();
            @Override public void setFocused(boolean focused) { super.setFocused(focused); if (focused) focusTimer.reset(); }
            @Override
            public boolean keyTyped(int key, int scanCode, int modifiers) {
                if (key == GLFW.GLFW_KEY_TAB && isFocused() && focusTimer.passed(10)) {
                    setFocused(false);
                    ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? backward.get() : forward.get()).setFocused(true);
                    return true;
                }
                return super.keyTyped(key, scanCode, modifiers);
            }
        };
    }

    private void calculatePath() {
        double sx = startXField.getValue().isEmpty() && mc.player != null ? mc.player.getX() : parseDistance(startXField.getValue());
        double sz = startZField.getValue().isEmpty() && mc.player != null ? mc.player.getZ() : parseDistance(startZField.getValue());
        directionsView.clear();

        if (endXField.getValue().isEmpty() || endZField.getValue().isEmpty()) {
            directionsView.add(Component.literal("Error: Please add destination coordinates"), -1);
            return;
        }
        double ex = parseDistance(endXField.getValue());
        double ez = parseDistance(endZField.getValue());
        try {
            calculateRoute(sx, sz, ex, ez);
        } catch (Exception e) {
            directionsView.add(Component.literal("Error: " + e), -1);
            return;
        }
        for (String dir : getDirections()) directionsView.add(Component.literal(dir), -1);
        XaerosIntegration.register();
        if (PathfinderHudElement.INSTANCE != null) PathfinderHudElement.INSTANCE.resetIndex();
    }

    private void copyToClipboard() {
        if (!getSteps().isEmpty()) mc.keyboardHandler.setClipboard(getClipboardString());
    }

    private void resetPath() {
        directionsView.clear();
        XaerosIntegration.unregister();
        clearSteps();
    }

    @Override
    public WindowView getRootView() {
        return this.rootView;
    }
}