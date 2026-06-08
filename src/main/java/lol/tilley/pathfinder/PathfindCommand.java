package lol.tilley.pathfinder;

import org.rusherhack.client.api.feature.command.Command;
import org.rusherhack.client.api.utils.ChatUtils;
import org.rusherhack.core.command.annotations.CommandExecutor;

import static lol.tilley.pathfinder.CrunchData.*;

public class PathfindCommand extends Command {
	private double endX, endZ;

	public PathfindCommand() {
		super("map", "find optimal path around the hw system");
	}

	@CommandExecutor(subCommand = "goal")
	@CommandExecutor.Argument({"goal x", "goal z"})
	private String goalPoint(String x, String z) {
		endX = parseDistance(x);
		endZ = parseDistance(z);
		return "Set destination to " + endX + ", " + endZ + ".";
	}

	@CommandExecutor(subCommand = "path")
	private String calculatePath() {
		if (mc.player == null) return "You must be in a world!";
		try {
			calculateRoute(mc.player.getX(), mc.player.getZ(), endX, endZ);
		} catch (Exception e) {
			return "Unable to calculate path: " + e;
		}
		for (String dir : getDirections()) ChatUtils.print(dir);
		mc.keyboardHandler.setClipboard(getClipboardString());
		if (PathfinderHudElement.INSTANCE != null) PathfinderHudElement.INSTANCE.resetIndex();
		return "Done!";
	}
}
