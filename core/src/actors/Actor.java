package actors;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Random;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;

import actions.Action;
import assetManager.Animation;
import assetManager.AssetManager;
import assetManager.SpriteAsset;
import ecu.se.DecalPicker;
import ecu.se.GameObject;
import ecu.se.Utils;
import ecu.se.map.Direction;
import ecu.se.map.Map;
import ecu.se.objects.Decal;
import stats.Stats;
import stats.TempStatModifier;

public abstract class Actor extends GameObject {

	protected String name;
	protected String spriteSheet;
	// protected Texture texture;
	protected Map map;
	protected float oldx = 0;
	protected float oldy = 0;
	protected Direction direction;

	protected Animation animation_body;
	protected Animation animation_feet;
	protected Animation animation_arms;
	protected Animation animation_head;

	protected int spriteWidth;// = 40;
	protected int spriteHeight;// = 48;
//	protected TextureRegion textureRegion;
	protected boolean awake;
	protected boolean invulnerable;
	protected boolean invisible;
	
	/**
	 * Used for movement modifiers like explosions.
	 */
	protected float externalForcesX;
	protected float externalForcesY;

	/**
	 * baseStats: All permanent stats. i.e. Allocated skill points from leveling
	 * up
	 */
	protected float[] baseStats = new float[Stats.values().length];

	/**
	 * modifierStats: Stats the change but do not require regular updates. i.e.
	 * Stat changes from equipped items.
	 */
	protected float[] modifierStats = new float[Stats.values().length];

	/**
	 * currentStats: The actual stats the game pulls from.
	 */
	protected float[] currentStats = new float[Stats.values().length];

	/**
	 * tempStatModifiers: Stats requiring regular updates. i.e. A poison debuff
	 * needs to damage the player every tic and likely has a limited life span
	 * that needs to be checked/updated every tic.
	 */
	protected LinkedList<TempStatModifier> tempStatModifiers;

	protected int healthbarWidth = 50;
	protected int healthbarHeight = 40;
	
	protected Action primaryAction;
	protected Action secondaryAction;
	protected ArrayList<Action> actions = new ArrayList<Action>();
	protected LinkedList<Action> activeActions = new LinkedList<Action>();
	protected LinkedList<Animation> animations = new LinkedList<Animation>();

	protected Vector2 currentSpeed;
	protected float currentHealth;
	protected float currentMana;

	// TDOD: Implement this level poop!
	protected int currentLevel;
	protected int currentXP;

	private Texture debugHealthBarTexture;
	protected float scaleX, scaleY;
	public Vector2 lookAt;

	public Actor(float x, float y, float z, Map map, String[] spriteSheets, int[] row) {
		super(x, y, z);
		this.map = map;
		
		for(int i = 0; i < Math.min(spriteSheets.length, row.length); i++) {
			Animation animation = new Animation(0, 0, 0, AssetManager.getSpriteSheet(spriteSheets[i]));
			animation.setRow(row[i]);
			animations.add(animation);
		}

		invisible = false;
		debugHealthBarTexture = AssetManager.getTexture("texture/misc/white.png").getTexture();
		bounds = Utils.getRectangleBounds(x, y, 40, 80, Utils.ALIGN_CENTERED);
		bounds = Utils.getEllipseBounds(x, y, 25, 25, 20);
		tempStatModifiers = new LinkedList<TempStatModifier>();
		setDefaults();
		updateStats();
		currentHealth = getStat(Stats.HEALTH);
		currentMana = getStat(Stats.MANA);
		Random random = new Random();
		currentHealth = random.nextInt(100);
		this.awake = false;
		lookAt = new Vector2(0, 0);
	}

	/**
	 * update: Handles low level functionality of Actors. Can be overridden if
	 * you're making a special Actor Ex. An Actor that teleports would need to
	 * override this function to replace the movement parts with specialized
	 * code.
	 * 
	 * @param deltaTime:
	 *            Time between each frame.
	 */
	public void update(float deltaTime) {
		invulnerable = false;
		updateStats(deltaTime);
		act(deltaTime);
		updateMovement(deltaTime);
		updateAnimations(deltaTime);
		updateActions(deltaTime);
		if (currentHealth <= 0) {
			this.kill();
			
			Map.getTile((int)x, (int)y).addObject(new Decal(x,y, "ass", AssetManager.getTexture(DecalPicker.getActorCorpse()).getTextureRegion()));
		}
	}

	protected void updateStats(float deltaTime) {
		finalizeModifiers();
		for (TempStatModifier stat : tempStatModifiers) {
			stat.update(deltaTime);
		}
		setHealth(deltaTime * getStat(Stats.HEALTH_REGEN));
		setMana(deltaTime * getStat(Stats.MANA_REGEN));
	}

	protected void updateMovement(float deltaTime) {
		x += (currentSpeed.x);
		y += (currentSpeed.y);

		currentSpeed.x *= currentStats[Stats.MOVEMENT_DRAG.ordinal()] * deltaTime;
		currentSpeed.y *= currentStats[Stats.MOVEMENT_DRAG.ordinal()] * deltaTime;
		bounds.setPosition(x, y);
	}
	
	protected void updateAnimations(float deltaTime) {
		float angle = Direction.angleDeg(this.getPositionV2(), lookAt);
		
		if (!invisible) {
			for(Animation a : animations) {
				a.setRotation(angle);
				a.setIdle(idle);
				a.update(deltaTime);
				a.setXY((int) x, (int) y);
			}
		}
		bounds.setRotation((float) Math.toDegrees(angle));
		bounds.setRotation(angle);

//		animation_body.setRotation(angle);
////		animation_feet.setRotation(angle);
//		animation_arms.setRotation(angle);
//		animation_head.setRotation(angle);
//		
//		animation_body.setIdle(idle);
//		animation_body.update(deltaTime);
//		animation_body.setXY((int) x, (int) y);
//
////		animation_feet.setIdle(idle);
////		animation_feet.update(deltaTime);
////		animation_feet.setXY((int) x, (int) y);
//
//		animation_arms.setIdle(idle);
//		animation_arms.update(deltaTime);
//		animation_arms.setXY((int) x, (int) y);
//
//		animation_head.setIdle(idle);
//		animation_head.update(deltaTime);
//		animation_head.setXY((int) x, (int) y);
		idle = true;

	}

	protected void updateActions(float deltaTime) {
		for (Action a : activeActions) {
			if (a.isActive()) {
				a.update(deltaTime);
			} else {
				activeActions.remove(a);
			}
		}
	}

	/**
	 * How and Actor needs to behave. Overhead (i.e. movement, animation, etc
	 * updates) are already handled by the Actor.java.
	 * 
	 * @param deltaTime:
	 *            Time between each frame.
	 */
	public abstract void act(float deltaTime);

	private static final int borderWidth = 1;
	private static final int barHeight = 2;

	@Override
	public void render(SpriteBatch batch) {
//		animation_feet.render(batch);
//		animation_body.render(batch);
//		animation_arms.render(batch);
//		animation_head.render(batch);

		for(Animation a : animations) {
			a.render(batch);	
		}
		
		
		
		// Renders a healthbar
		batch.setColor(1.0f, 1.0f, 1.0f, 0.5f);
		batch.draw(debugHealthBarTexture, x - (int) (healthbarWidth * 0.5f) - borderWidth, y + healthbarHeight - borderWidth,
				healthbarWidth + borderWidth * 2, barHeight * 2 + borderWidth * 2);
		
		batch.setColor(1.0f, 0f, 0f, 0.5f);
		batch.draw(debugHealthBarTexture, x - (int) (healthbarWidth * 0.5f), y + healthbarHeight,
				healthbarWidth * (currentHealth / (currentStats[Stats.HEALTH.ordinal()] + 0.0f)), barHeight * 2);
		batch.setColor(Color.WHITE);
	}

	public float defend(Stats type, float damage) {
		currentHealth -= damage;
		return damage;
	}

	/**
	 * 
	 * @return the name of the Actor.
	 */
	// TODO: Add a name generator for Actors
	public String getName() {
		return name;
	}

	/**
	 * Sets the name of this Actor.
	 * 
	 * @param name
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * 
	 * @return the current health of this Actor.
	 */
	public float getHealth() {
		return currentHealth;
	}

	public int getHealthRounded() {
		return (int) currentHealth;
	}

	/**
	 * Sets the HP of this Actor Caps the max based on the Actors Health stat.
	 * 
	 * @param currentHealth
	 */
	public void setHealth(float currentHealth) {
		if (invulnerable)
			return;
		this.currentHealth += currentHealth;
		this.currentHealth = Utils.clamp(0, getStat(Stats.HEALTH), this.currentHealth);
	}

	public float getMana() {
		return currentMana;
	}

	/**
	 * Adds cm to currentMana. Does NOT directly set mana.
	 * 
	 * @param currentMana
	 */
	public void setMana(float cm) {
		currentMana += cm;
		currentMana = Utils.clamp(0, getStat(Stats.MANA), currentMana);
	}

	public float[] getStats() {
		return currentStats;
	}

	public float getStat(Stats stat) {
		return currentStats[stat.ordinal()];
	}

	public void setIdle(boolean idle) {
		this.idle = idle;
	}

	public void push(float x, float y) {
		currentSpeed.x += x;
		currentSpeed.y += y;
	}

	public void move(float deltaTime, Direction direction, boolean updateDirection) {
		currentSpeed.x += (currentStats[Stats.MOVEMENT_ACCELERATION.ordinal()] * deltaTime) * direction.x;
		currentSpeed.x = Utils.clamp(-currentStats[Stats.MOVEMENT_SPEED.ordinal()],
				currentStats[Stats.MOVEMENT_SPEED.ordinal()], currentSpeed.x);

		currentSpeed.y += (currentStats[Stats.MOVEMENT_ACCELERATION.ordinal()] * deltaTime) * direction.y;
		currentSpeed.y = Utils.clamp(-currentStats[Stats.MOVEMENT_SPEED.ordinal()],
				currentStats[Stats.MOVEMENT_SPEED.ordinal()], currentSpeed.y);

		// if (updateDirection)
		// animation.rowSelect(Direction.valueOf(direction.name()).ordinal());
		
		setIdle(false);
	}

	// TODO: Add different defaults for different classes (Could be done by
	// subclasses)
	public void setDefaults() {
		for (int i = 0; i < Stats.values().length; i++) {
			baseStats[i] = 1;
			modifierStats[i] = 0;
		}

		baseStats[Stats.HEALTH.ordinal()] = 100;
		baseStats[Stats.MANA.ordinal()] = 100;
		baseStats[Stats.MOVEMENT_DRAG.ordinal()] = 0.3f;
		baseStats[Stats.MOVEMENT_SPEED.ordinal()] = 50f;
		baseStats[Stats.MOVEMENT_ACCELERATION.ordinal()] = 200f;
	}

	// TODO: Calculate stats based on equipped items / other modifiers
	public void updateStats() {
		for (int i = 0; i < Stats.values().length; i++) {
			currentStats[i] = baseStats[i] + modifierStats[i];
		}
	}

	public LinkedList<TempStatModifier> modifierChanges = new LinkedList<TempStatModifier>();

	public void removeTempStat(TempStatModifier stat) {
		stat.remove = true;
		modifierChanges.add(stat);
	}

	public void addTempStat(TempStatModifier stat) {
		stat.remove = false;
		modifierChanges.add(stat);
	}

	public void finalizeModifiers() {
		for (TempStatModifier stat : modifierChanges) {
			if (stat.remove)
				tempStatModifiers.remove(stat);
			else
				tempStatModifiers.add(stat);
		}
		modifierChanges.removeAll(modifierChanges);
	}

	/**
	 * Mouse Left action
	 * 
	 * @param x
	 * @param y
	 */
	public void primaryAction(int x, int y) {
		if (primaryAction != null) {
			primaryAction.act(x, y);
		}
	}

	/**
	 * Mouse Right Action
	 * 
	 * @param x
	 * @param y
	 */
	public void secondaryAction(int x, int y) {
		if (secondaryAction != null) {
			secondaryAction.act(x, y);
		}
	}

	/**
	 * 
	 * @param Hotkey/Toolbar
	 *            Actions
	 * @param x
	 *            - Mouse cursor X world coordinate.
	 * @param y
	 *            - Mouse cursor Y world coordinate.
	 */
	public void doAction(int action, int x, int y) {
		if (actions.get(action) != null) {
			actions.get(action).act(x, y);
		}
	}

	public void addActiveAction(Action action) {
		activeActions.add(action);
	}

	public void addAction(Action action) {
		actions.add(action);
	}

	// TODO: Place corpse texture (needs to fade eventually)
	// Drop Loot.
	public void die() {
		// Drop loot
		// Set Boolean to dead

		// Change update() to do the following
		// Subtract deltaTime from timer.
		// Upon timer running out, add self to remove list in ObjectManager

		// Change render() to do the following
		// Render corpse instead of alive actor.
	}

	@Override
	public void dispose() {

	}
}
