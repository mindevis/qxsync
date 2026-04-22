package com.qx.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Configuration for inventory sync to QMServer ({@code POST /api/v1/inventory}).
 */
public final class QxSyncConfig {

	private static final Logger LOGGER = LoggerFactory.getLogger("qxsync");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static QxSyncConfig instance = new QxSyncConfig();

	public boolean enabled = false;
	public String enabled_msg = "Enable inventory sync to QMWeb API";

	public String apiUrl = "https://api-mc.qx-dev.ru/api/v1/inventory";
	public String apiUrl_msg = "Full URL for POST (QMServer /api/v1/inventory)";

	public String serverId = "default";
	public String serverId_msg = "Server identifier in API payload";

	public String apiKey = "";
	public String apiKey_msg = "API key from QMAdmin inventory sync settings";

	public int intervalMinutes = 5;
	public String intervalMinutes_msg = "Periodic sync interval in minutes (0 = only on disconnect)";

	private QxSyncConfig() {}

	public static QxSyncConfig get() {
		return instance;
	}

	public static void bootstrap() {
		Path configFile = FMLPaths.CONFIGDIR.get().resolve("qxsync.json");
		try {
			if (Files.exists(configFile)) {
				String json = Files.readString(configFile);
				QxSyncConfig loaded = GSON.fromJson(json, QxSyncConfig.class);
				if (loaded != null) {
					instance = mergeWithDefaults(loaded);
					save();
					LOGGER.info("Configuration loaded from {}", configFile);
				}
			} else {
				instance = new QxSyncConfig();
				save();
				LOGGER.info("Default configuration created: {}", configFile);
			}
		} catch (Exception e) {
			LOGGER.error("Error loading configuration, using defaults: {}", e.getMessage());
			instance = new QxSyncConfig();
		}
	}

	public static void save() {
		try {
			Path dir = FMLPaths.CONFIGDIR.get();
			Files.createDirectories(dir);
			Path configFile = dir.resolve("qxsync.json");
			Files.writeString(configFile, GSON.toJson(instance), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		} catch (IOException e) {
			LOGGER.error("Error saving configuration: {}", e.getMessage());
		}
	}

	private static QxSyncConfig mergeWithDefaults(QxSyncConfig loaded) {
		QxSyncConfig d = new QxSyncConfig();
		QxSyncConfig m = new QxSyncConfig();
		m.enabled = loaded.enabled;
		m.apiUrl = loaded.apiUrl != null && !loaded.apiUrl.isBlank() ? loaded.apiUrl : d.apiUrl;
		m.serverId = loaded.serverId != null && !loaded.serverId.isBlank() ? loaded.serverId : d.serverId;
		m.apiKey = loaded.apiKey != null ? loaded.apiKey : d.apiKey;
		m.intervalMinutes = loaded.intervalMinutes > 0 ? loaded.intervalMinutes : d.intervalMinutes;
		copyMsgFields(loaded, m);
		return m;
	}

	private static void copyMsgFields(QxSyncConfig from, QxSyncConfig to) {
		if (from.enabled_msg != null) to.enabled_msg = from.enabled_msg;
		if (from.apiUrl_msg != null) to.apiUrl_msg = from.apiUrl_msg;
		if (from.serverId_msg != null) to.serverId_msg = from.serverId_msg;
		if (from.apiKey_msg != null) to.apiKey_msg = from.apiKey_msg;
		if (from.intervalMinutes_msg != null) to.intervalMinutes_msg = from.intervalMinutes_msg;
	}
}
