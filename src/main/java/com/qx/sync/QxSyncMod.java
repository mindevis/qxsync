package com.qx.sync;

/**
 * Server-side inventory sync to QMServer; NeoForge entrypoint calls {@link #init()}.
 */
public final class QxSyncMod {

	public static final String MOD_ID = "qxsync";

	private QxSyncMod() {}

	public static void init() {
		QxSyncConfig.bootstrap();
	}
}
