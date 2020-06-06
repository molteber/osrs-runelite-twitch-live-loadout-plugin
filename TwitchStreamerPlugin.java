/*
 * Copyright (c) 2020, Pepijn Verburg <pepijn.verburg@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.twitchstreamer;

import com.google.inject.Provides;
import net.runelite.api.*;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import com.google.gson.*;

import net.runelite.api.events.StatChanged;

import javax.inject.Inject;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@PluginDescriptor(
	name = "Twitch Streamer",
	description = "Send Real-time Equipment, Skills, Inventory, Bank and more to Twitch Extensions for additional viewer engagement.",
	enabledByDefault = false
)

/**
 * Manages polling and event listening mechanisms to synchronize the state
 * to the Twitch Configuration Service. All client data is fetched in this class
 * ad passed to a couple of other classes.
 */
public class TwitchStreamerPlugin extends Plugin
{
	@Inject
	private TwitchStreamerConfig twitchStreamerConfig;

	@Inject
	private Client client;

	@Inject
	ItemManager itemManager;

	/**
	 * Twitch Configuration Service state that can be mapped to a JSON.
	 */
	private ConfigurationServiceState configurationServiceState;

	/**
	 * Twitch Configuration Service API end-point helpers.
	 */
	private ConfigurationServiceApi configurationServiceApi;

	/**
	 * Initialize this plugin
	 * @throws Exception
	 */
	@Override
	protected void startUp() throws Exception
	{
		super.startUp();

		configurationServiceState = new ConfigurationServiceState(client);
		configurationServiceApi = new ConfigurationServiceApi();
	}

	/**
	 * Helper to get the current configuration.
	 * @param configManager
	 * @return
	 */
	@Provides
	TwitchStreamerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TwitchStreamerConfig.class);
	}

	/**
	 * Polling mechanism to update the state only when it has changed.
	 * This avoids data being pushed when any of part of the state changed
	 * and forces us to combine update requests in one.
	 */
	@Schedule(period = 2, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void syncState()
	{
		final boolean updateRequired = configurationServiceState.isChanged();

		// Guard: check if something has changed to avoid unnecessary updates.
		if (!updateRequired) {
			return;
		}

		final JsonObject state = configurationServiceState.getState();
		boolean setResult = configurationServiceApi.setBroadcasterState(state);

		// Guard: check if the update was successful.
		// If not this will automatically trigger a new attempt later.
		if (!setResult) {
			return;
		}

		configurationServiceState.acknowledgeChange();
	}

	/**
	 * Polling mechanism to update the player info
	 */
	@Schedule(period = 2, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void syncPlayerInfo()
	{

		// Guard: player info is not available when not loaded
		if (!playerIsLoaded())
		{
			return;
		}

		final String playerName = client.getLocalPlayer().getName();

		configurationServiceState.setPlayerName(playerName);
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		ItemContainer container = event.getItemContainer();
		final boolean isInventory = isItemContainer(container, InventoryID.INVENTORY);
		final boolean isEquipment = isItemContainer(container, InventoryID.EQUIPMENT);
		final boolean isBank = isItemContainer(container, InventoryID.BANK);
		final Item[] items = container.getItems();

		if (isInventory)
		{
			configurationServiceState.setInventoryItems(items);
		}
		else if (isEquipment)
		{
			configurationServiceState.setEquipmentItems(items);
		}
		else if (isBank)
		{
			Item[] highestPricedItems = getHighestPricedItems(items, twitchStreamerConfig.MAX_BANK_ITEMS);
			configurationServiceState.setBankItems(highestPricedItems);
		}

		// update the weight for specific containers
		if (isInventory || isEquipment)
		{
			final int weight = client.getWeight();
			configurationServiceState.setWeight(weight);
		}
	}

	public Item[] getHighestPricedItems(Item[] items, int maxAmount)
	{
		final List<PricedItem> pricedItems = new ArrayList();

		for (Item item : items) {
			final int itemId = item.getId();
			final int itemQuantity = item.getQuantity();
			final long itemPrice = ((long) itemManager.getItemPrice(itemId)) * itemQuantity;
			final PricedItem pricedItem = new PricedItem(item, itemPrice);
			System.out.println("--------");
			System.out.println(itemId);
			System.out.println(itemManager.getItemPrice(itemId));
			pricedItems.add(pricedItem);
		}

		Collections.sort(pricedItems);

		final List<PricedItem> highestPricedItems = pricedItems.subList(0, maxAmount);
		final Item[] selectedItems = new Item[highestPricedItems.size()];

		for (int pricedItemIndex = 0; pricedItemIndex < highestPricedItems.size(); pricedItemIndex++) {
			selectedItems[pricedItemIndex] = highestPricedItems.get(pricedItemIndex).getItem();
		}

		return selectedItems;
	}

	public boolean isItemContainer(ItemContainer container, InventoryID containerId)
	{
		return container == client.getItemContainer(containerId);
	}

	/**
	 * Skill and level information is synced through stat events.
	 * @param event
	 */
	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		final int[] skillExperiences = client.getSkillExperiences();
		final int[] boostedSkillLevels = client.getBoostedSkillLevels();

		configurationServiceState.setSkillExperiences(skillExperiences);
		configurationServiceState.setBoostedSkillLevels(boostedSkillLevels);
	}

	/**
	 * Check whether the player is currently logged in and loaded.
	 * This is used for several state polling mechanisms.
	 * @return true when logged in
	 */
	public boolean playerIsLoaded()
	{
		final boolean isLoggedIn = client.getGameState() == GameState.LOGGED_IN;

		// Guard: check if logged in.
		if (!isLoggedIn) {
			return false;
		}

		final Player player = client.getLocalPlayer();
		final PlayerComposition playerComposition = player.getPlayerComposition();
		final String playerName = player.getName();
		final boolean playerLoaded = (playerComposition != null && playerName != null);

		// Guard: check if player is loaded.
		if (!playerLoaded) {
			return false;
		}

		// TODO: more checks?

		return true;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged)
	{
		// A configuration change should be directly reflected?
	}
}
