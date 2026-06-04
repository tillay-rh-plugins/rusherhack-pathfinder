package lol.tilley.pathfinder;

import org.rusherhack.client.api.feature.command.Command;
import org.rusherhack.client.api.utils.ChatUtils;
import org.rusherhack.core.command.annotations.CommandExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static lol.tilley.pathfinder.CrunchData.*;

public class PathfindCommand extends Command {
	private double endX, endZ;
	public List<Road> network = null;
	public static List<double[]> steps = new ArrayList<>();
	private final PathfinderHudElement pathfinderHudElement;

	public PathfindCommand(PathfinderHudElement pathfinderHudElement) {
		super("map", "find optimal path around the hw system");
		this.pathfinderHudElement = pathfinderHudElement;
	}

	@CommandExecutor(subCommand = "goal")
	@CommandExecutor.Argument({"goal x", "goal z"})
	private String goalPoint(String x, String z) {
		endX = parseDistance(x);
		endZ = parseDistance(z);
		return "Set destination to " + endX + ", " + endZ + ".";
	}

	public static List<double[]> getFullSteps() {
		return steps;
	}

	@CommandExecutor(subCommand = "path")
	private String calculatePath() {
		steps.clear();
		if (network == null) {
			try {
				var roads = fetchRoads();
				network = snapToGrid(renameRoads(subdivideSegments(roads)));
			} catch (Exception e) {
				return "Unable to download map data: " + e;
			}
		}
		if (mc.player == null) {
			return "You must be in a world to use this command!";
		}
		var path = mergeCollinear(findPath(mc.player.getX(), mc.player.getZ(), endX, endZ, network));
		for (int i = 0; i < path.size(); i++) {
			var road = path.get(i);
			for (var seg : road.segments())
				steps.add(new double[]{seg.p1X(), seg.p1Z()});
			if (i == path.size() - 1) {
				var last = road.segments().get(road.segments().size() - 1);
				steps.add(new double[]{last.p2X(), last.p2Z()});
			}
			double dist = road.segments().stream().mapToDouble(s -> Math.hypot(s.p2X() - s.p1X(), s.p2Z() - s.p1Z())).sum();
			if (road.name().equals("open nether"))
				ChatUtils.print("Fly " + Math.round(dist) + " blocks " + heading(road) + " through open nether");
			else
				ChatUtils.print("Continue " + Math.round(dist) + " blocks " + heading(road) + " on the " + road.name());
			if (i < path.size() - 1) {
				String turn = describeTurn(road, path.get(i + 1));
				if (turn != null) ChatUtils.print(turn);
			}
		}

		// copy points to clipboard to paste easily into desmos
		mc.keyboardHandler.setClipboard(steps.stream().map(p -> "(" + Math.round(p[0]) + "," + Math.round(p[1]) + ")").collect(Collectors.joining(", ")));
		this.pathfinderHudElement.setPath(path);
		return "Done!";
	}

	public static double parseDistance(String input) {
		input = input.trim().toLowerCase();
		double mul = 1;
		if (input.endsWith("k")) { mul = 1_000; input = input.substring(0, input.length() - 1); }
		else if (input.endsWith("m")) { mul = 1_000_000; input = input.substring(0, input.length() - 1); }
		else if (input.endsWith("r")) { input = String.valueOf((Math.random() * 2 - 1) * parseDistance(input.substring(0, input.length() - 1))); }
		return Double.parseDouble(input) * mul;
	}
	
}
