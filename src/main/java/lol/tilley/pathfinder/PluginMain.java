package lol.tilley.pathfinder;

import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.client.api.plugin.Plugin;

public class PluginMain extends Plugin {
	@Override
	public void onLoad() {
		this.getLogger().info("Plugin rusherhack-pathfinder loaded");
		RusherHackAPI.getHudManager().registerFeature(new PathfinderHudElement());
		RusherHackAPI.getWindowManager().registerFeature(new PathingWindow());
	}

	@Override
	public void onUnload() {
		this.getLogger().info("Plugin rusherhack-pathfinder unloaded!");
	}
}