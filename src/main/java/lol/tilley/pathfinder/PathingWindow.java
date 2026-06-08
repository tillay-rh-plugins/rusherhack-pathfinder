package lol.tilley.pathfinder;

import net.minecraft.network.chat.Component;
import org.rusherhack.client.api.feature.window.ResizeableWindow;
import org.rusherhack.client.api.ui.window.content.ComboContent;
import org.rusherhack.client.api.ui.window.content.component.ButtonComponent;
import org.rusherhack.client.api.ui.window.content.component.TextComponent;
import org.rusherhack.client.api.ui.window.content.component.TextFieldComponent;
import org.rusherhack.client.api.ui.window.view.RichTextView;
import org.rusherhack.client.api.ui.window.view.SimpleView;
import org.rusherhack.client.api.ui.window.view.TabbedView;
import org.rusherhack.client.api.ui.window.view.WindowView;

import java.util.List;

import static lol.tilley.pathfinder.CrunchData.*;

public class PathingWindow extends ResizeableWindow {
    private final TabbedView rootView;
    private final RichTextView directionsView;
    private final TextFieldComponent startXField, startZField, endXField, endZField;

    public PathingWindow() {
        super("Rusher Maps", 200, 100, 400, 300);
        this.setMinWidth(300);
        this.setMinHeight(200);

        final ComboContent startPosCombo = new ComboContent(this);
        startXField = new TextFieldComponent(this, "", 80, false);
        startZField = new TextFieldComponent(this, "", 80, false);
        startPosCombo.addContent(new TextComponent(this, "X:"));
        startPosCombo.addContent(startXField);
        startPosCombo.addContent(new TextComponent(this, " Z:"));
        startPosCombo.addContent(startZField);

        final ComboContent endPosCombo = new ComboContent(this);
        endXField = new TextFieldComponent(this, "", 80, false);
        endZField = new TextFieldComponent(this, "", 80, false);
        endPosCombo.addContent(new TextComponent(this, "X:"));
        endPosCombo.addContent(endXField);
        endPosCombo.addContent(new TextComponent(this, " Z:"));
        endPosCombo.addContent(endZField);

        final ComboContent buttonCombo = new ComboContent(this);
        ButtonComponent pathButton = new ButtonComponent(this, "Path", this::calculatePath);
        ButtonComponent copyButton = new ButtonComponent(this, "Copy Points", this::copyToClipboard);
        pathButton.setWidth(60);
        copyButton.setWidth(80);
        buttonCombo.addContent(pathButton);
        buttonCombo.addContent(copyButton);

        this.directionsView = new RichTextView("Directions", this);

        SimpleView mainView = new SimpleView("Navigation", this, List.of(
                new TextComponent(this, "Start (leave blank for current position):"),
                startPosCombo,
                new TextComponent(this, "Destination:"),
                endPosCombo,
                buttonCombo,
                directionsView
        ));

        this.rootView = new TabbedView(this, List.of(mainView));
    }

    private void calculatePath() {
        double sx = startXField.getValue().isEmpty() && mc.player != null ? mc.player.getX() : parseDistance(startXField.getValue());
        double sz = startZField.getValue().isEmpty() && mc.player != null ? mc.player.getZ() : parseDistance(startZField.getValue());
        double ex = endXField.getValue().isEmpty() && mc.player != null ? mc.player.getX() : parseDistance(endXField.getValue());
        double ez = endZField.getValue().isEmpty() && mc.player != null ? mc.player.getZ() : parseDistance(endZField.getValue());
        directionsView.clear();
        try {
            calculateRoute(sx, sz, ex, ez);
        } catch (Exception e) {
            directionsView.add(Component.literal("Error: " + e), -1);
            return;
        }
        for (String dir : getDirections()) directionsView.add(Component.literal(dir), -1);
        if (PathfinderHudElement.INSTANCE != null) PathfinderHudElement.INSTANCE.resetIndex();
    }

    private void copyToClipboard() {
        if (!getSteps().isEmpty()) mc.keyboardHandler.setClipboard(getClipboardString());
    }

    @Override
    public WindowView getRootView() {
        return this.rootView;
    }
}