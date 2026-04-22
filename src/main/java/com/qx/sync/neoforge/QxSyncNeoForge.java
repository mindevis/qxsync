package com.qx.sync.neoforge;

import com.qx.sync.InventorySyncManager;
import com.qx.sync.QxSyncMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(QxSyncMod.MOD_ID)
public final class QxSyncNeoForge {

	public QxSyncNeoForge(IEventBus modBus) {
		QxSyncMod.init();

		var bus = NeoForge.EVENT_BUS;
		bus.addListener((ServerStoppedEvent e) -> InventorySyncManager.shutdown());
		bus.addListener((ServerTickEvent.Post e) -> InventorySyncManager.onServerTick(e.getServer()));
		bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
			if (e.getEntity() instanceof ServerPlayer sp) {
				InventorySyncManager.syncPlayerInventory(sp);
			}
		});
	}
}
