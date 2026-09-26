package com.example.nightmarerealms;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

/**
 * Nightmare Realms - a three-phase custom boss fight.
 *
 * Phase 1: Shadow Sovereign (Enderman form)
 * Phase 2: Dread Knight (Wither Skeleton form, netherite-equipped)
 * Phase 3: Apex Phantom (giant flying finale)
 *
 * Ported to Fabric for Minecraft 1.21.11 / Yarn mappings 1.21.11+build.4.
 * Notable changes from older (~1.20) Yarn code this was adapted from:
 *   - EntityAttributes.GENERIC_MAX_HEALTH / GENERIC_ATTACK_DAMAGE were renamed
 *     to EntityAttributes.MAX_HEALTH / EntityAttributes.ATTACK_DAMAGE (the
 *     "generic." prefix was dropped from vanilla attribute IDs).
 *   - Entity#getWorld() is deprecated in favor of Entity#getEntityWorld().
 *   - Bosses are now spawned with the entity's own (EntityType, World)
 *     constructor rather than EntityType#create(World), since the static
 *     factory method's parameters have changed across versions while the
 *     constructor has stayed stable.
 */
public class NightmareRealms implements ModInitializer {

	public static final String MOD_ID = "nightmarerealms";

	private static ServerBossBar bossBar;

	// The three stages of the boss fight: 0 = not started, 1 = Enderman,
	// 2 = Mini Boss, 3 = Final Boss.
	private static int currentPhase = 0;
	private static EndermanEntity phase1Boss;
	private static WitherSkeletonEntity phase2Boss;
	private static PhantomEntity phase3Boss;

	@Override
	public void onInitialize() {
		// Main health bar for the boss fight.
		bossBar = new ServerBossBar(
				Text.literal("Nightmare Sovereign - Phase I").formatted(Formatting.DARK_PURPLE, Formatting.BOLD),
				BossBar.Color.PURPLE,
				BossBar.Style.NOTCHED_10
		);

		// Drive the boss fight's phase transitions every server tick.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (currentPhase == 1 && phase1Boss != null) {
				updatePhase1();
			} else if (currentPhase == 2 && phase2Boss != null) {
				updatePhase2();
			} else if (currentPhase == 3 && phase3Boss != null) {
				updatePhase3();
			}
		});

		// /nightmarerealms start - begins the fight at the player's position.
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(CommandManager.literal("nightmarerealms")
						.then(CommandManager.literal("start")
								.requires(source -> true)
								.executes(NightmareRealms::executeStart))));
	}

	private static int executeStart(com.mojang.brigadier.context.CommandContext<ServerCommandSource> context) {
		ServerCommandSource source = context.getSource();
		ServerPlayerEntity player = source.getPlayer();

		if (player == null) {
			source.sendError(Text.translatable("nightmarerealms.command.no_player"));
			return 0;
		}

		if (currentPhase != 0) {
			source.sendError(Text.translatable("nightmarerealms.command.already_active"));
			return 0;
		}

		startBossFight(source.getWorld(), player.getBlockPos(), player);
		source.sendFeedback(() -> Text.translatable("nightmarerealms.command.start")
				.formatted(Formatting.DARK_PURPLE), true);
		return 1;
	}

	// Starts the fight (Phase 1: Enderman Boss).
	public static void startBossFight(ServerWorld world, BlockPos pos, ServerPlayerEntity player) {
		currentPhase = 1;
		bossBar.addPlayer(player);
		bossBar.setName(Text.literal("Nightmare Sovereign - Phase I (Shadow Form)").formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
		bossBar.setColor(BossBar.Color.PURPLE);
		bossBar.setVisible(true);

		phase1Boss = new EndermanEntity(EntityType.ENDERMAN, world);
		phase1Boss.refreshPositionAndAngles(pos, 0, 0);

		phase1Boss.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(300.0);
		phase1Boss.setHealth(300.0f);
		phase1Boss.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).setBaseValue(12.0);
		phase1Boss.setCustomName(Text.literal("Shadow Sovereign").formatted(Formatting.DARK_PURPLE));
		phase1Boss.setCustomNameVisible(true);

		world.spawnEntity(phase1Boss);
	}

	private static void updatePhase1() {
		if (!phase1Boss.isAlive()) {
			// Move on to Phase 2 once the Shadow Sovereign falls.
			startPhase2((net.minecraft.server.world.ServerWorld) phase1Boss.getEntityWorld(), phase1Boss.getBlockPos());
			return;
		}
		bossBar.setPercent(phase1Boss.getHealth() / phase1Boss.getMaxHealth());
	}

	// Phase 2: Wither Skeleton Boss (Mini Boss).
	private static void startPhase2(ServerWorld world, BlockPos pos) {
		currentPhase = 2;
		phase1Boss = null;

		bossBar.setName(Text.literal("Nightmare Sovereign - Phase II (Nether Dread)").formatted(Formatting.DARK_RED, Formatting.BOLD));
		bossBar.setColor(BossBar.Color.RED);

		phase2Boss = new WitherSkeletonEntity(EntityType.WITHER_SKELETON, world);
		phase2Boss.refreshPositionAndAngles(pos, 0, 0);

		phase2Boss.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(450.0);
		phase2Boss.setHealth(450.0f);
		phase2Boss.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).setBaseValue(18.0);

		// Give it strong gear.
		phase2Boss.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
		phase2Boss.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
		phase2Boss.setCustomName(Text.literal("Dread Knight").formatted(Formatting.RED));
		phase2Boss.setCustomNameVisible(true);

		world.spawnEntity(phase2Boss);
	}

	private static void updatePhase2() {
		if (!phase2Boss.isAlive()) {
			// Move on to the third and final phase.
			startPhase3((net.minecraft.server.world.ServerWorld) phase2Boss.getEntityWorld(), phase2Boss.getBlockPos());
			return;
		}
		bossBar.setPercent(phase2Boss.getHealth() / phase2Boss.getMaxHealth());
	}

	// Phase 3: Giant Phantom (Final Boss).
	private static void startPhase3(ServerWorld world, BlockPos pos) {
		currentPhase = 3;
		phase2Boss = null;

		bossBar.setName(Text.literal("Nightmare Sovereign - Final Form (Sky Terror)").formatted(Formatting.BLUE, Formatting.BOLD));
		bossBar.setColor(BossBar.Color.BLUE);

		phase3Boss = new PhantomEntity(EntityType.PHANTOM, world);
		phase3Boss.refreshPositionAndAngles(pos.up(5), 0, 0);

		phase3Boss.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(600.0);
		phase3Boss.setHealth(600.0f);
		phase3Boss.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).setBaseValue(25.0);
		phase3Boss.setCustomName(Text.literal("Apex Phantom").formatted(Formatting.BLUE));
		phase3Boss.setCustomNameVisible(true);

		world.spawnEntity(phase3Boss);
	}

	private static void updatePhase3() {
		if (!phase3Boss.isAlive()) {
			// The boss fight has been won.
			currentPhase = 0;
			bossBar.setVisible(false);
			bossBar.clearPlayers();
			phase3Boss = null;
			return;
		}
		bossBar.setPercent(phase3Boss.getHealth() / phase3Boss.getMaxHealth());
	}
}
