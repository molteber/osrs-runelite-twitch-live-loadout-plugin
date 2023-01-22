package com.twitchliveloadout.marketplace.notifications;

import com.google.common.collect.EvictingQueue;
import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.marketplace.products.EbsNotification;
import com.twitchliveloadout.marketplace.products.MarketplaceProduct;
import com.twitchliveloadout.marketplace.products.TwitchProduct;
import com.twitchliveloadout.marketplace.transactions.TwitchTransaction;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;

import java.time.Instant;
import java.util.ArrayList;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.*;

public class NotificationManager {
	private final TwitchLiveLoadoutPlugin plugin;
	private final ChatMessageManager chatMessageManager;
	private final Client client;
	private Instant notificationsLockedUntil;

	/**
	 * Queue of all the notifications that should be shown to the player
	 * Note that they can be queued per group of notifications that should trigger at the same time
	 * This is mainly used for triggering different types of notifications.
	 */
	private EvictingQueue<ArrayList<Notification>> notificationGroupQueue = EvictingQueue.create(NOTIFICATION_QUEUE_MAX_SIZE);

	public NotificationManager(TwitchLiveLoadoutPlugin plugin, ChatMessageManager chatMessageManager, Client client)
	{
		this.plugin = plugin;
		this.chatMessageManager = chatMessageManager;
		this.client = client;
	}

	public void onGameTick()
	{
		handleNotificationsQueue();
	}

	public void handleEbsNotifications(MarketplaceProduct marketplaceProduct, ArrayList<EbsNotification> ebsNotifications)
	{
		if (ebsNotifications == null)
		{
			return;
		}

		ArrayList<Notification> notificationGroup = new ArrayList();

		for (EbsNotification ebsNotification : ebsNotifications)
		{
			Notification notification = new Notification(marketplaceProduct, ebsNotification);

			// guard: check if this is a notification that should be send immediately
			if (NOW_NOTIFICATION_TIMING_TYPE.equals(ebsNotification.timingType))
			{
				sendNotification(notification);
				return;
			}

			// otherwise add to the queued group
			notificationGroup.add(notification);
		}

		// guard: only add if the group is valid
		if (notificationGroup.size() <= 0)
		{
			return;
		}

		notificationGroupQueue.add(notificationGroup);
	}

	private void handleNotificationsQueue()
	{

		// guard: check if we can send a new notification
		if (!canSendNotification())
		{
			return;
		}

		// get the first group from the queue
		ArrayList<Notification> notificationGroup = notificationGroupQueue.poll();

		// guard: make sure we have a valid notification group
		if (notificationGroup == null)
		{
			return;
		}

		// handle all notifications
		for (Notification notification: notificationGroup)
		{
			sendNotification(notification);
		}
	}

	private void sendNotification(Notification notification)
	{
		EbsNotification ebsNotification = notification.ebsNotification;
		MarketplaceProduct marketplaceProduct = notification.marketplaceProduct;
		String messageType = ebsNotification.messageType;

		// guard: skip the notification is the marketplace product is not valid anymore
		if (marketplaceProduct.isExpired(2000) || !marketplaceProduct.isActive())
		{
			return;
		}

		if (CHAT_NOTIFICATION_MESSAGE_TYPE.equals(messageType))
		{
			sendChatNotification(notification);
		}
		else if (OVERHEAD_NOTIFICATION_MESSAGE_TYPE.equals(messageType))
		{
			sendOverheadNotification(notification);
		}
	}

	private void sendChatNotification(Notification notification)
	{
		String message = notification.ebsNotification.message;

		if (message == null)
		{
			message = getDefaultMessage(notification);
		}

		final ChatMessageBuilder chatMessage = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append(message)
			.append(ChatColorType.NORMAL);

		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(chatMessage.build())
			.build());

		lockNotificationsUntil(CHAT_NOTIFICATION_LOCKED_MS);
	}

	private void sendOverheadNotification(Notification notification)
	{
		Player player = client.getLocalPlayer();
		String message = notification.ebsNotification.message;

		if (message == null)
		{
			message = getDefaultMessage(notification);
		}

		// guard: skip on invalid player
		if (player == null)
		{
			return;
		}

		String finalMessage = message;
		plugin.runOnClientThread(() -> {
			player.setOverheadText(finalMessage);
		});
		plugin.scheduleOnClientThread(() -> {
			player.setOverheadText("");
		}, OVERHEAD_NOTIFICATION_DURATION_MS);
		lockNotificationsUntil(OVERHEAD_NOTIFICATION_LOCKED_MS);
	}

	private String getDefaultMessage(Notification notification)
	{
		final MarketplaceProduct marketplaceProduct = notification.marketplaceProduct;

		// guard: make sure the product is valid
		if (marketplaceProduct == null)
		{
			return "Thanks for the donation!";
		}

		final TwitchTransaction transaction = marketplaceProduct.getTransaction();
		final TwitchProduct twitchProduct = marketplaceProduct.getTwitchProduct();
		String username = "viewer";
		String donationName = "your donation";

		if (transaction != null)
		{
			username = transaction.user_name;
		}

		if (twitchProduct != null)
		{
			final int costAmount = twitchProduct.cost.amount;
			final String costType = twitchProduct.cost.type;
			donationName = "donating "+ costAmount +" "+ costType;
		}

		String message = "Thank you "+ username +" for "+ donationName +"!";

		return message;
	}

	private boolean canSendNotification()
	{
		return notificationsLockedUntil == null || Instant.now().isAfter(notificationsLockedUntil);
	}

	private void lockNotificationsUntil(int durationMs)
	{
		Instant newLockedUntil = Instant.now().plusMillis(durationMs);

		// guard: skip new locked when not after old lock
		if (notificationsLockedUntil != null && newLockedUntil.isBefore(notificationsLockedUntil))
		{
			return;
		}

		notificationsLockedUntil = newLockedUntil;
	}
}
