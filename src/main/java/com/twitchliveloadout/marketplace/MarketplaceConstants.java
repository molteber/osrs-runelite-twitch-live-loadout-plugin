package com.twitchliveloadout.marketplace;

public class MarketplaceConstants {
	public static final int MAX_MARKETPLACE_PRODUCT_AMOUNT_IN_MEMORY = 50;
	public static final int MAX_TRANSACTION_AMOUNT_IN_MEMORY = 50;

	public static final int UPDATE_ACTIVE_PRODUCTS_DELAY_MS = 200;
	public static final int TRANSACTION_CHECKED_AT_OFFSET_MS = 10 * 1000;
	public static final int TRANSACTION_DELAY_CORRECTION_MS = 1 * 200;

	public static final String CURRENT_TILE_LOCATION_TYPE = "current-tile";
	public static final String PREVIOUS_TILE_LOCATION_TYPE = "previous-tile";

	public static final String NONE_FOLLOW_TYPE = "none";
	public static final String IN_RADIUS_FOLLOW_TYPE = "in-radius";
	public static final String PREVIOUS_TILE_FOLLOW_TYPE = "behind-player";

	public static final int DEFAULT_MIN_RADIUS = 1;
	public static final int DEFAULT_MAX_RADIUS = 15;
	public static final String DEFAULT_RADIUS_TYPE = "radius";
	public static final String OUTWARD_RADIUS_TYPE = "outward-radius";

	public static final String RANDOM_ROTATION_TYPE = "random";
	public static final String PLAYER_ROTATION_TYPE = "player";
	public static final String INTERACTING_ROTATION_TYPE = "interacting";

	public static final double RUNELITE_OBJECT_RADIUS_PER_TILE = 60d;
	public static final double RUNELITE_OBJECT_FULL_ROTATION = 2047d;

	public static final int PLAYER_TILE_HISTORY_SIZE = 10;

	public static final int NOTIFICATION_QUEUE_MAX_SIZE = 200;
	public static final int END_NOTIFICATION_GRACE_PERIOD_MS = 7000; // keep it high due to internal delays
	public static final String NONE_NOTIFICATION_MESSAGE_TYPE = "none";
	public static final String CHAT_NOTIFICATION_MESSAGE_TYPE = "chat";
	public static final String OVERHEAD_NOTIFICATION_MESSAGE_TYPE = "overhead";
	public static final String TILE_MARKER_NOTIFICATION_MESSAGE_TYPE = "tile-marker";

	public static final String START_NOTIFICATION_TIMING_TYPE = "start";
	public static final String END_NOTIFICATION_TIMING_TYPE = "end";

	public static final int WIDGET_EFFECT_MAX_SIZE = 100;
	public static final int MENU_EFFECT_MAX_SIZE = 100;
	public static final String DISABLE_MENU_OPTION_TYPE = "disable";
	public static final String DISABLE_INTERFACE_WIDGET_TYPE = "disable";
	public static final String ALTER_INTERFACE_WIDGET_TYPE = "alter";
	public static final String OVERLAY_INTERFACE_WIDGET_TYPE = "overlay";

	public static final int MOVEMENT_EFFECT_MAX_SIZE = 100;
	public static final int TRANSMOG_EFFECT_MAX_SIZE = 100;

	public static final int CHAT_NOTIFICATION_LOCKED_MS = 1 * 1000;
	public static final int OVERHEAD_NOTIFICATION_LOCKED_MS = 3 * 1000;
	public static final int OVERHEAD_NOTIFICATION_DURATION_MS = OVERHEAD_NOTIFICATION_LOCKED_MS - 1 * 1000;
	public static final int TILE_MARKER_NOTIFICATION_DURATION_MS = 0 * 1000;

	public static final int GLOBAL_PLAY_SOUND_THROTTLE_MS = 0;
	public static final int UNIQUE_PLAY_SOUND_THROTTLE_MS = 250;
}
