package com.qx.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sends player inventory data to QMServer for display in QMWeb.
 */
public final class InventorySyncManager {

	private static final Logger LOGGER = LoggerFactory.getLogger("qxsync");
	private static final int TICKS_PER_SECOND = 20;
	private static int tickCounter = 0;

	private static final Gson GSON = new GsonBuilder().create();
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.build();
	private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "QXSync-InventorySync");
		t.setDaemon(true);
		return t;
	});

	private InventorySyncManager() {}

	public static void onServerTick(MinecraftServer server) {
		QxSyncConfig config = QxSyncConfig.get();
		if (!config.enabled || config.intervalMinutes <= 0) {
			return;
		}

		int intervalTicks = config.intervalMinutes * 60 * TICKS_PER_SECOND;
		tickCounter++;
		if (tickCounter < intervalTicks) {
			return;
		}
		tickCounter = 0;

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			syncPlayerInventory(player);
		}
	}

	public static void syncPlayerInventory(ServerPlayer player) {
		QxSyncConfig config = QxSyncConfig.get();
		if (!config.enabled) {
			return;
		}

		String serverId = config.serverId;
		if (serverId == null || serverId.isBlank()) {
			serverId = "default";
		}

		String apiUrl = config.apiUrl;
		if (apiUrl == null || apiUrl.isBlank()) {
			apiUrl = "https://api-mc.qx-dev.ru/api/v1/inventory";
		}

		String url = apiUrl;
		String finalServerId = serverId;
		String apiKey = config.apiKey != null ? config.apiKey : "";

		if (EXECUTOR.isShutdown()) {
			return;
		}
		EXECUTOR.submit(() -> {
			try {
				Map<String, Object> payload = buildInventoryPayload(player, finalServerId);
				String json = GSON.toJson(payload);

				HttpRequest.Builder builder = HttpRequest.newBuilder()
					.uri(URI.create(url))
					.timeout(Duration.ofSeconds(15))
					.header("Content-Type", "application/json");

				if (apiKey != null && !apiKey.isBlank()) {
					builder.header("Authorization", "Bearer " + apiKey);
					builder.header("X-API-Key", apiKey);
				}

				HttpRequest request = builder
					.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
					.build();

				HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

				if (response.statusCode() >= 200 && response.statusCode() < 300) {
					LOGGER.debug("Inventory sync for {} succeeded", player.getName().getString());
				} else {
					LOGGER.warn("Inventory sync for {} failed: HTTP {}", player.getName().getString(), response.statusCode());
				}
			} catch (Exception e) {
				LOGGER.error("Inventory sync failed for {}: {}", player.getName().getString(), e.getMessage());
			}
		});
	}

	private static Map<String, Object> buildInventoryPayload(ServerPlayer player, String serverId) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("serverId", serverId);
		payload.put("playerUuid", player.getUUID().toString());
		payload.put("playerName", player.getName().getString());
		payload.put("timestamp", System.currentTimeMillis());

		var registryAccess = player.serverLevel().registryAccess();

		var inventory = player.getInventory();
		List<Map<String, Object>> main = new ArrayList<>();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty()) {
				main.add(serializeItem(stack, i, registryAccess));
			}
		}
		payload.put("main", main);

		List<Map<String, Object>> armor = new ArrayList<>();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.getType() == EquipmentSlot.Type.HAND) continue;
			ItemStack stack = player.getItemBySlot(slot);
			if (!stack.isEmpty()) {
				armor.add(serializeItem(stack, slot.ordinal(), registryAccess));
			}
		}
		payload.put("armor", armor);

		ItemStack offhand = player.getOffhandItem();
		if (!offhand.isEmpty()) {
			payload.put("offhand", serializeItem(offhand, 0, registryAccess));
		}

		return payload;
	}

	private static Map<String, Object> serializeItem(ItemStack stack, int slot, net.minecraft.core.RegistryAccess registryAccess) {
		Map<String, Object> map = new HashMap<>();
		map.put("slot", slot);
		map.put("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		map.put("count", stack.getCount());

		try {
			var tag = stack.save(registryAccess);
			if (tag instanceof CompoundTag nbt && !nbt.isEmpty()) {
				map.put("nbt", nbt.getAsString());
			}
		} catch (Exception e) {
			LOGGER.debug("Could not serialize NBT for item: {}", e.getMessage());
		}

		return map;
	}

	public static void shutdown() {
		EXECUTOR.shutdown();
	}
}
