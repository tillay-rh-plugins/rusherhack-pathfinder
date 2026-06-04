package lol.tilley.pathfinder;

import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.plugin.Plugin;

public class PluginMain extends Plugin {
	@Override
	public void onLoad() {
		this.getLogger().info("Plugin rusherhack-pathfinder loaded");
		var pathfinderHudElement = new PathfinderHudElement();
		RusherHackAPI.getHudManager().registerFeature(pathfinderHudElement);
		RusherHackAPI.getCommandManager().registerFeature(new PathfindCommand(pathfinderHudElement));
	}

	@Override
	public void onUnload() {
		this.getLogger().info("Plugin rusherhack-pathfinder unloaded!");
	}
}