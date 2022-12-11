package com.twitchliveloadout.marketplace;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.twitchliveloadout.TwitchLiveLoadoutConfig;
import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.marketplace.animations.AnimationManager;
import com.twitchliveloadout.marketplace.products.*;
import com.twitchliveloadout.marketplace.spawns.SpawnManager;
import com.twitchliveloadout.marketplace.transactions.TwitchTransaction;
import com.twitchliveloadout.marketplace.transmogs.TransmogManager;
import com.twitchliveloadout.twitch.TwitchApi;
import com.twitchliveloadout.twitch.TwitchSegmentType;
import com.twitchliveloadout.twitch.TwitchStateEntry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.PlayerChanged;
import okhttp3.Response;

import java.time.Instant;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class MarketplaceManager {

	@Getter
	private final TwitchLiveLoadoutPlugin plugin;
	private final TwitchApi twitchApi;

	@Getter
	private final Client client;

	@Getter
	private final TwitchLiveLoadoutConfig config;

	@Getter
	private final SpawnManager spawnManager;

	@Getter
	private final AnimationManager animationManager;

	@Getter
	private final TransmogManager transmogManager;

	/**
	 * List to keep track of all the active products
	 */
	private final CopyOnWriteArrayList<MarketplaceProduct> activeProducts = new CopyOnWriteArrayList();

	/**
	 * List of all streamer products from the Twitch configuration segment
	 */
	private CopyOnWriteArrayList<StreamerProduct> streamerProducts = new CopyOnWriteArrayList();

	/**
	 * List of all EBS products from Twitch
	 */
	private CopyOnWriteArrayList<EbsProduct> ebsProducts = new CopyOnWriteArrayList();

	/**
	 * List of all the possible product durations from Twitch
	 */
	private CopyOnWriteArrayList<EbsProductDuration> ebsProductDurations = new CopyOnWriteArrayList();

	/**
	 * List of all extension transactions that should be handled
	 */
	private CopyOnWriteArrayList<TwitchTransaction> queuedTransactions = new CopyOnWriteArrayList();
	private Instant transactionsLastCheckedAt = null;

	public MarketplaceManager(TwitchLiveLoadoutPlugin plugin, TwitchApi twitchApi, Client client, TwitchLiveLoadoutConfig config)
	{
		this.plugin = plugin;
		this.twitchApi = twitchApi;
		this.client = client;
		this.config = config;
		this.spawnManager = new SpawnManager(plugin, client);
		this.animationManager = new AnimationManager(plugin, client);
		this.transmogManager = new TransmogManager();
	}

	/**
	 * Get new Twitch transactions where the effects should be queued for.
	 */
	public void fetchNewTransactions()
	{
		try {
			Response response = twitchApi.getEbsTransactions(transactionsLastCheckedAt);
			JsonObject result = (new JsonParser()).parse(response.body().string()).getAsJsonObject();
			Boolean status = result.get("status").getAsBoolean();
			String message = result.get("message").getAsString();
			JsonArray newTransactions = result.getAsJsonArray("transactions");

			// guard: check if the status is valid
			if (!status)
			{
				log.warn("Could not fetch EBS transactions from Twitch as the status is invalid with message: "+ message);
				return;
			}

			newTransactions.forEach((element) -> {

				// TMP: spawn only one product!
				if (queuedTransactions.size() > 0 || activeProducts.size() > 0) {
					return;
				}

				TwitchTransaction twitchTransaction = new Gson().fromJson(element, TwitchTransaction.class);
				queuedTransactions.add(twitchTransaction);
				log.info("Queued a new Twitch transaction with ID: "+ twitchTransaction.id);
			});

			// only update the last checked at if everything is successful
			transactionsLastCheckedAt = Instant.now();
		} catch (Exception exception) {
			// empty
		}
	}

	/**
	 * Check for new products that should be applied. This process is a little bit more complex
	 * then you would expect at first, because we need to hook in to the Twitch product configuration and
	 * transactions. From the transaction we can fetch the Twitch product (by SKU). Then we can check
	 * whether the streamer really configured this product to have a specific effect (done in the configuration service).
	 * If yes, we have a Streamer product containing a reference to the Ebs Product, which contains the effect information.
	 * When applying new transactions we will check whether all of these steps are valid to prevent viewers
	 * triggering any effects that were never configured by the streamer.
	 */
	public void applyQueuedTransactions()
	{

		// guard: only apply the products when the player is logged in
		if (!plugin.isLoggedIn())
		{
			return;
		}

		Iterator iterator = queuedTransactions.iterator();

		while (iterator.hasNext())
		{
			TwitchTransaction transaction = (TwitchTransaction) iterator.next();
			TwitchProduct twitchProduct = transaction.product_data;

			// guard: make sure the twitch product is valid
			if (twitchProduct == null)
			{
				continue;
			}

			String twitchProductSku = twitchProduct.sku;
			StreamerProduct streamerProduct = getStreamerProductBySku(twitchProductSku);

			// guard: make sure a streamer product is configured for this SKU
			if (streamerProduct == null)
			{
				continue;
			}

			String ebsProductId = streamerProduct.ebsProductId;
			EbsProduct ebsProduct = getEbsProductById(ebsProductId);

			// guard: make sure an EBS product is configured for this streamer product
			if (ebsProduct == null || !ebsProduct.enabled)
			{
				continue;
			}

			log.info("Found a valid transaction that we can start: "+ transaction.id);
			log.info("Twitch product SKU: "+ twitchProduct.sku);
			log.info("Streamer product name: "+ streamerProduct.name);
			log.info("Ebs product ID: "+ ebsProduct.id);

			// remove the transaction, now it is going to be handled
			queuedTransactions.remove(transaction);

			// create a new marketplace product where all the other products
			// are merged together in one instance for reference
			MarketplaceProduct newProduct = new MarketplaceProduct(
				this,
				transaction,
				ebsProduct,
				streamerProduct,
				twitchProduct
			);

			// register this product to be active, which is needed to check
			// for any periodic effects that might need to trigger
			activeProducts.add(newProduct);
		}
	}

	/**
	 * Check to clean any existing products that are expired
	 */
	public void cleanExpiredProducts()
	{
		// TODO
//		activeProducts.remove(product);
//		product.stop();
	}

	/**
	 * Handle HEAVY periodic effects of the active products,
	 * such as spawning or random animations.
	 */
	public void updateActiveProducts()
	{

		// guard: don't do anything when not logged in
		if (!plugin.isLoggedIn())
		{
			return;
		}

		// respawn all spawned objects that require it
		// due to for example the reloading of a scene
		spawnManager.respawnRequested();

		// record a history of the player location that we can use
		// when spawning new objects that are relative in some way to the player
		spawnManager.recordPlayerLocation();

		// handle any new behaviours for all active products
		for (MarketplaceProduct product : activeProducts)
		{
			product.handleBehaviour();
		}
	}

	/**
	 * Update the products the streamer has configured in the Twitch Extension.
	 */
	public void updateStreamerProducts()
	{
		JsonObject segmentContent = twitchApi.getConfigurationSegmentContent(TwitchSegmentType.BROADCASTER);

		if (segmentContent == null)
		{
			return;
		}

		JsonArray rawStreamerProducts = segmentContent.getAsJsonArray(TwitchStateEntry.STREAMER_PRODUCTS.getKey());

		if (rawStreamerProducts == null)
		{
			return;
		}

		CopyOnWriteArrayList<StreamerProduct> newStreamerProducts = new CopyOnWriteArrayList();

		rawStreamerProducts.forEach((element) -> {
			try {
				JsonObject rawStreamerProduct = element.getAsJsonObject();
				StreamerProduct streamerProduct = new Gson().fromJson(rawStreamerProduct, StreamerProduct.class);
				newStreamerProducts.add(streamerProduct);
			} catch (Exception exception) {
				// empty
			}
		});

		streamerProducts = newStreamerProducts;
	}

	/**
	 * Update the available effects and their configuration from the Twitch EBS.
	 */
	public void updateEbsProducts()
	{
		try {
			Response response = twitchApi.getEbsProducts();
			JsonObject result = (new JsonParser()).parse(response.body().string()).getAsJsonObject();
			Boolean status = result.get("status").getAsBoolean();
			String message = result.get("message").getAsString();
			JsonArray products = result.getAsJsonArray("products");
			JsonArray durations = result.getAsJsonArray("durations");

			// guard: check if the status is valid
			// if not we want to keep the old products intact
			if (!status)
			{
				log.warn("Could not fetch EBS products from Twitch as the status is invalid with message: "+ message);
				return;
			}

			CopyOnWriteArrayList<EbsProduct> newEbsProducts = new CopyOnWriteArrayList();
			CopyOnWriteArrayList<EbsProductDuration> newEbsProductDurations = new CopyOnWriteArrayList();

			products.forEach((element) -> {
				EbsProduct ebsProduct = new Gson().fromJson(element, EbsProduct.class);
				newEbsProducts.add(ebsProduct);
			});
			durations.forEach((element) -> {
				EbsProductDuration ebsProductDuration = new Gson().fromJson(element, EbsProductDuration.class);
				newEbsProductDurations.add(ebsProductDuration);
			});

			ebsProducts = newEbsProducts;
			ebsProductDurations = newEbsProductDurations;
		} catch (Exception exception) {
			// empty
		}
	}

	/**
	 * Handle player changes to update current animations or equipment transmogs.
	 */
	public void onPlayerChanged(PlayerChanged playerChanged)
	{

		// guard: make sure we are logged in
		if (!plugin.isLoggedIn())
		{
			return;
		}

		// guard: only update the local player
		if (playerChanged.getPlayer() != client.getLocalPlayer())
		{
			return;
		}

		animationManager.recordOriginalAnimations();
		animationManager.updateEffectAnimations();
	}

	/**
	 * Handle game state changes to respawn all objects, because they are cleared
	 * when a new scene is being loaded.
	 */
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState newGameState = event.getGameState();

		// guard: only respawn on the loading event
		// this means all spawned objects are removed from the scene
		// and need to be queued for a respawn, this is done periodically
		if (newGameState == GameState.LOADING)
		{
			spawnManager.registerDespawn();
		}
	}

	/**
	 * Handle a client tick for all active products for changes
	 * that need to happen really fast and are lightweight.
	 */
	public void onClientTick()
	{

		// for all active products the tick should be triggered
		for (MarketplaceProduct product : activeProducts)
		{
			product.onClientTick();
		}
	}

	private StreamerProduct getStreamerProductBySku(String twitchProductSku)
	{
		Iterator iterator = streamerProducts.iterator();

		while(iterator.hasNext())
		{
			StreamerProduct candidateStreamerProduct = (StreamerProduct) iterator.next();

			// guard: check if a match is found
			if (twitchProductSku.equals(candidateStreamerProduct.twitchProductSku))
			{
				return candidateStreamerProduct;
			}
		}

		return null;
	}

	private EbsProduct getEbsProductById(String ebsProductId)
	{
		Iterator iterator = ebsProducts.iterator();

		while(iterator.hasNext())
		{
			EbsProduct candidateEbsProduct = (EbsProduct) iterator.next();

			// guard: check if a match is found
			if (ebsProductId.equals(candidateEbsProduct.id))
			{
				return candidateEbsProduct;
			}
		}

		return null;
	}

	/**
	 * Handle plugin shutdown / marketplace disable
	 */
	public void shutDown()
	{
		animationManager.revertAnimations();
		transmogManager.revertEquipment();
	}
}
