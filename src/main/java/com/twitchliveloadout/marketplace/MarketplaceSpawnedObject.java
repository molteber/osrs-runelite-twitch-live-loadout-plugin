package com.twitchliveloadout.marketplace;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.ModelData;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

import java.time.Instant;

@Slf4j
public class MarketplaceSpawnedObject {

	@Getter
	private final Instant spawnedAt;

	@Getter
	private final Client client;

	@Getter
	private final RuneLiteObject object;

	private int currentAnimationId;
	private final int idleAnimationId;

	@Getter
	private final ModelData modelData;

	private double currentScale = -1;
	private double currentRotationDegrees = 0;
	private final int[] originalVerticesX;
	private final int[] originalVerticesY;
	private final int[] originalVerticesZ;

	@Getter
	private final MarketplaceSpawnPoint spawnPoint;

	@Getter
	private final MarketplaceProduct product;

	@Getter
	@Setter
	private boolean respawnRequired = false;

	public MarketplaceSpawnedObject(MarketplaceProduct product, Client client, RuneLiteObject object, ModelData modelData, MarketplaceSpawnPoint spawnPoint, int idleAnimationId)
	{
		this.spawnedAt = Instant.now();
		this.product = product;
		this.client = client;
		this.object = object;
		this.modelData = modelData;
		this.spawnPoint = spawnPoint;
		this.idleAnimationId = idleAnimationId;

		// cache all the original vertices to make it easier for the rotation to be reset
		// to its original state and always be able to rotate relative to the starting position
		int verticesCount = modelData.getVerticesCount();
		originalVerticesX = new int[verticesCount];
		originalVerticesY = new int[verticesCount];
		originalVerticesZ = new int[verticesCount];
		for(int verticesIndex = 0; verticesIndex < verticesCount; ++verticesIndex) {
			originalVerticesX[verticesIndex] = modelData.getVerticesX()[verticesIndex];
			originalVerticesY[verticesIndex] = modelData.getVerticesY()[verticesIndex];
			originalVerticesZ[verticesIndex] = modelData.getVerticesZ()[verticesIndex];
		}

		// set to initial spawn-point
		object.setLocation(spawnPoint.getLocalPoint(client), spawnPoint.getPlane());
	}

	public void rotateTowards(LocalPoint targetPoint)
	{
		LocalPoint sourcePoint = spawnPoint.getLocalPoint(client);
		int deltaX = sourcePoint.getX() - targetPoint.getX();
		int deltaY = sourcePoint.getY() - targetPoint.getY();
		double angleRadians = Math.atan2(deltaX, deltaY);
		double angleDegrees = Math.toDegrees(angleRadians);

		rotate(angleDegrees);
	}

	public void rotate(double angleDegrees)
	{

		// guard: skip rotation if already rotated like this for performance
		if (currentRotationDegrees == angleDegrees)
		{
			return;
		}

		currentRotationDegrees = angleDegrees;

		// make sure rotation is not negative
		if (angleDegrees < 0) {
			angleDegrees = 360 - (angleDegrees % 360);
		}

		// make sure there are no multiple rotations
		if (angleDegrees > 360) {
			angleDegrees = angleDegrees % 360;
		}

		int orientation = (int) (angleDegrees * MarketplaceConstants.RUNELITE_OBJECT_FULL_ROTATION / 360d);
		object.setOrientation(orientation);
		log.info("New rotation: " +angleDegrees+", "+orientation);

	}

	public void scale(double scale)
	{

		// guard: check if the scale is valid and changed
		if (scale < 0 || scale == currentScale)
		{
			return;
		}

		currentScale = scale;

		int radius = (int) (scale * MarketplaceConstants.RUNELITE_OBJECT_FULL_RADIUS);
		object.setRadius(radius);
	}

	public void resetTransformations()
	{
//		modelData.cloneVertices();
//
//		for(int verticesIndex = 0; verticesIndex < modelData.getVerticesCount(); ++verticesIndex) {
//			modelData.getVerticesX()[verticesIndex] = originalVerticesX[verticesIndex];
//			modelData.getVerticesY()[verticesIndex] = originalVerticesY[verticesIndex];
//			modelData.getVerticesZ()[verticesIndex] = originalVerticesZ[verticesIndex];
//		}
	}

	private void applyTransformations()
	{
//		// reset all the transformations
//		resetTransformations();
//
//		// rotate relative to its current position
//		MarketplaceModelUtilities.rotateModel(modelData, currentRotationDegrees);
//
//		// make sure the same scale is enforced on the new vertices!
//		MarketplaceModelUtilities.scaleModel(modelData, currentScale);
	}

	public void setAnimation(int animationId, boolean shouldLoop)
	{
		setAnimation(animationId, shouldLoop, false);
	}

	private void setAnimation(int animationId, boolean shouldLoop, boolean forceSet)
	{
		Animation animation = null;

		// guard: skip when the current animation
		if  (animationId == currentAnimationId && !forceSet)
		{
			return;
		}

		if (animationId >= 0)
		{
			animation = client.loadAnimation(animationId);
		}

		object.setShouldLoop(shouldLoop);
		object.setAnimation(animation);
		currentAnimationId = animationId;
	}

	public void resetAnimation()
	{

		// guard: set to no animation when there is no idle animation
		if (idleAnimationId < 0)  {
			setAnimation(-1, false);
			return;
		}

		setAnimation(idleAnimationId, true);
	}

	public void show()
	{
		object.setActive(true);
	}

	public void hide()
	{
		object.setActive(false);
	}

	public void render()
	{
		object.setModel(modelData.light());
	}

	public void respawn()
	{
		final int plane = spawnPoint.getPlane();
		final WorldPoint worldPoint = spawnPoint.getWorldPoint();
		final LocalPoint localPoint = spawnPoint.getLocalPoint(client);
		final boolean isInScene = worldPoint.isInScene(client);

		// guard: location cannot be set to local point if not in scene
		if (!isInScene)
		{
			return;
		}

		// move the object to the new relative local point as the scene offset might be changed
		object.setLocation(localPoint, plane);

		// de-activate and re-activate again to force re-render
		hide();
		show();
	}
}
