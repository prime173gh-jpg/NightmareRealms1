package com.example.nightmarerealms;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

public class NightmareRealms implements ModInitializer {

	public static final String MOD_ID = "nightmarerealms";

	private static ServerBossBar bossBar;
	private static int currentPhase = 0;
	private static int tickCounter = 0;
	private static int cooldownTimer = 0;
	private static BlockPos arenaCenter;
	private static final Random random = new Random();

	private static EndermanEntity phase1Boss;
	private static WitherSkeletonEntity phase2Boss;
	private static WitherEntity phase3Boss;

	@Override
	public void onInitialize() {
		bossBar = new ServerBossBar(
				Text.literal("Nightmare Sovereign - Phase I").formatted(Formatting.DARK_PURPLE, Formatting.BOLD),
				BossBar.Color.PURPLE,
				BossBar.Style.NOTCHED_10
		);

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tickCounter++;

			if (cooldownTimer > 0) {
				cooldownTimer--;
				if (cooldownTimer % 20 == 0) {
					int secondsLeft = cooldownTimer / 20;
					bossBar.setName(Text.literal("Prepare for next phase in: " + secondsLeft + "s").formatted(Formatting.YELLOW, Formatting.BOLD));
				}
				if (cooldownTimer == 0) {
					if (currentPhase == 1) startPhase2Actual();
					else if (currentPhase == 2) startPhase3Actual();
				}
				return;
			}

			if (currentPhase == 1 && phase1Boss != null) {
				updatePhase1();
			} else if (currentPhase == 2 && phase2Boss != null) {
				updatePhase2();
			} else if (currentPhase == 3 && phase3Boss != null) {
				updatePhase3();
			}
		});

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

		if (currentPhase != 0 || cooldownTimer > 0) {
			source.sendError(Text.translatable("nightmarerealms.command.already_active"));
			return 0;
		}

		startBossFight(source.getWorld(), player.getBlockPos(), player);
		source.sendFeedback(() -> Text.literal("The Nightmare Realm has begun! Prepare yourself!").formatted(Formatting.DARK_RED, Formatting.BOLD), true);
		return 1;
	}

	public static void startBossFight(ServerWorld world, BlockPos pos, ServerPlayerEntity player) {
		currentPhase = 1;
		arenaCenter = pos;
		bossBar.addPlayer(player);
		bossBar.setName(Text.literal("Shadow Sovereign - Phase I").formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
		bossBar.setColor(BossBar.Color.PURPLE);
		bossBar.setVisible(true);

		// Arena built with Bedrock to prevent breaking
		buildEnclosedArena(world, pos, Blocks.BEDROCK, Blocks.BEDROCK, 7, 5);

		phase1Boss = new EndermanEntity(EntityType.ENDERMAN, world);
		phase1Boss.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 0, 0);

		phase1Boss.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(300.0);
		phase1Boss.setHealth(300.0f);
		phase1Boss.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).setBaseValue(10.0);
		phase1Boss.setCustomName(Text.literal("Shadow Sovereign").formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
		phase1Boss.setCustomNameVisible(true);

		world.spawnEntity(phase1Boss);
	}

	private static void updatePhase1() {
		if (!phase1Boss.isAlive()) {
			dropPhase1Loot((ServerWorld) phase1Boss.getEntityWorld(), phase1Boss.getBlockPos());
			startCooldown(1);
			return;
		}
		bossBar.setPercent(phase1Boss.getHealth() / phase1Boss.getMaxHealth());

		if (tickCounter % 300 == 0) {
			healBoss(phase1Boss, 300.0, 0.04);
		}

		if (tickCounter % 40 == 0 && phase1Boss.getTarget() != null) {
			ServerWorld world = (ServerWorld) phase1Boss.getEntityWorld();
			BlockPos targetPos = phase1Boss.getTarget().getBlockPos().add(world.random.nextInt(5) - 2, 0, world.random.nextInt(5) - 2);
			phase1Boss.teleport(targetPos.getX(), targetPos.getY(), targetPos.getZ(), true);
		}

		if (tickCounter % 260 == 0 && phase1Boss.getTarget() instanceof ServerPlayerEntity player) {
			ServerWorld world = (ServerWorld) phase1Boss.getEntityWorld();
			player.damage(world, world.getDamageSources().magic(), 12.0f);
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0));
			player.sendMessage(Text.literal("You were struck by Shadow Blast!").formatted(Formatting.DARK_PURPLE), true);
		}
	}

	private static void startCooldown(int completedPhase) {
		currentPhase = completedPhase;
		cooldownTimer = 200; // 10 seconds
		bossBar.setPercent(1.0f);
	}

	private static void startPhase2Actual() {
		currentPhase = 2;
		ServerWorld world = (ServerWorld) bossBar.getPlayers().iterator().next().getEntityWorld();

		bossBar.setName(Text.literal("Dread Knight - Phase II").formatted(Formatting.DARK_RED, Formatting.BOLD));
		bossBar.setColor(BossBar.Color.RED);

		buildEnclosedArena(world, arenaCenter, Blocks.BEDROCK, Blocks.BEDROCK, 7, 5);

		phase2Boss = new WitherSkeletonEntity(EntityType.WITHER_SKELETON, world);
		phase2Boss.refreshPositionAndAngles(arenaCenter.getX() + 0.5, arenaCenter.getY() + 1, arenaCenter.getZ() + 0.5, 0, 0);

		phase2Boss.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(450.0);
		phase2Boss.setHealth(450.0f);
		phase2Boss.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).setBaseValue(16.0);

		phase2Boss.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
		phase2Boss.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
		phase2Boss.setCustomName(Text.literal("Dread Knight").formatted(Formatting.RED, Formatting.BOLD));
		phase2Boss.setCustomNameVisible(true);

		world.spawnEntity(phase2Boss);
	}

	private static void updatePhase2() {
		if (!phase2Boss.isAlive()) {
			dropPhase2Loot((ServerWorld) phase2Boss.getEntityWorld(), phase2Boss.getBlockPos());
			startCooldown(2);
			return;
		}
		bossBar.setPercent(phase2Boss.getHealth() / phase2Boss.getMaxHealth());

		if (tickCounter % 300 == 0) {
			healBoss(phase2Boss, 450.0, 0.04);
		}

		if (tickCounter % 80 == 0 && phase2Boss.getTarget() != null) {
			ServerWorld world = (ServerWorld) phase2Boss.getEntityWorld();
			Vec3d lookVec = phase2Boss.getRotationVec(1.0F);
			FireballEntity fireball = new FireballEntity(world, phase2Boss, lookVec, 1);
			fireball.setPosition(phase2Boss.getX() + lookVec.x, phase2Boss.getBodyY(0.5), phase2Boss.getZ() + lookVec.z);
			world.spawnEntity(fireball);
		}

		if (tickCounter % 200 == 0) {
			ServerWorld world = (ServerWorld) phase2Boss.getEntityWorld();
			for (int i = 0; i < 2; i++) {
				SkeletonEntity minion = new SkeletonEntity(EntityType.SKELETON, world);
				minion.refreshPositionAndAngles(phase2Boss.getBlockPos().add(i - 1, 0, i - 1), 0, 0);
				world.spawnEntity(minion);
			}
		}
	}

	private static void startPhase3Actual() {
		currentPhase = 3;
		ServerWorld world = (ServerWorld) bossBar.getPlayers().iterator().next().getEntityWorld();

		bossBar.setName(Text.literal("Nightmare Wither - Final Phase").formatted(Formatting.DARK_GRAY, Formatting.BOLD));
		bossBar.setColor(BossBar.Color.WHITE);

		// Bedrock Arena with height 5 to prevent flying away
		buildEnclosedArena(world, arenaCenter, Blocks.BEDROCK, Blocks.BEDROCK, 7, 5);

		phase3Boss = new WitherEntity(EntityType.WITHER, world);
		phase3Boss.refreshPositionAndAngles(arenaCenter.getX() + 0.5, arenaCenter.getY() + 1.5, arenaCenter.getZ() + 0.5, 0, 0);

		phase3Boss.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(350.0);
		phase3Boss.setHealth(350.0f);
		phase3Boss.setCustomName(Text.literal("Nightmare Wither").formatted(Formatting.DARK_GRAY, Formatting.BOLD));
		phase3Boss.setCustomNameVisible(true);

		if (!bossBar.getPlayers().isEmpty()) {
			ServerPlayerEntity targetPlayer = bossBar.getPlayers().iterator().next();
			targetPlayer.requestTeleport(arenaCenter.getX() + 0.5, arenaCenter.getY() + 1, arenaCenter.getZ() + 3.0);
			phase3Boss.setTarget(targetPlayer);
		}

		world.spawnEntity(phase3Boss);
	}

	private static void updatePhase3() {
		if (!phase3Boss.isAlive()) {
			dropFinalLoot((ServerWorld) phase3Boss.getEntityWorld(), phase3Boss.getBlockPos());

			currentPhase = 0;
			bossBar.setVisible(false);
			bossBar.clearPlayers();
			phase3Boss = null;
			return;
		}
		bossBar.setPercent(phase3Boss.getHealth() / phase3Boss.getMaxHealth());

		ServerWorld world = (ServerWorld) phase3Boss.getEntityWorld();

		if (!bossBar.getPlayers().isEmpty()) {
			ServerPlayerEntity player = bossBar.getPlayers().iterator().next();

			// Attack 1: Dark Death (every 10s)
			if (tickCounter % 200 == 0) {
				player.damage(world, world.getDamageSources().wither(), 10.0f);
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 100, 1));
				player.sendMessage(Text.literal("Dark Death Strike hit you!").formatted(Formatting.DARK_GRAY, Formatting.BOLD), true);
			}

			// Attack 2: Nightmare Bolt (every 18s)
			if (tickCounter % 360 == 0) {
				LightningEntity lightning = new LightningEntity(EntityType.LIGHTNING_BOLT, world);
				lightning.refreshPositionAfterTeleport(player.getX(), player.getY(), player.getZ());
				world.spawnEntity(lightning);
				player.sendMessage(Text.literal("Nightmare Lightning struck you!").formatted(Formatting.GOLD, Formatting.BOLD), true);
			}

			// Attack 3: Sudden Blindness (every 25s)
			if (tickCounter % 500 == 0) {
				player.damage(world, world.getDamageSources().magic(), 12.0f);
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0));
				player.sendMessage(Text.literal("Blinded by Darkness!").formatted(Formatting.DARK_PURPLE, Formatting.BOLD), true);
			}
		}

		// Heal boss every 15s
		if (tickCounter % 300 == 0) {
			healBoss(phase3Boss, 350.0, 0.04);
		}

		// Spawn minions every 20s
		if (tickCounter % 400 == 0) {
			for (int i = 0; i < 2; i++) {
				WitherSkeletonEntity minion = new WitherSkeletonEntity(EntityType.WITHER_SKELETON, world);
				minion.refreshPositionAndAngles(phase3Boss.getBlockPos().add(i == 0 ? 1 : -1, 0, i == 0 ? 1 : -1), 0, 0);
				minion.setCustomName(Text.literal("Wither Minion").formatted(Formatting.BLACK));
				if (!bossBar.getPlayers().isEmpty()) {
					minion.setTarget(bossBar.getPlayers().iterator().next());
				}
				world.spawnEntity(minion);
			}
		}
	}

	private static void healBoss(net.minecraft.entity.mob.MobEntity boss, double maxHealth, double percentage) {
		float healAmount = (float) (maxHealth * percentage);
		boss.heal(healAmount);
	}

	private static void buildEnclosedArena(ServerWorld world, BlockPos center, net.minecraft.block.Block floorBlock, net.minecraft.block.Block wallBlock, int radius, int height) {
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				world.setBlockState(center.add(x, -1, z), floorBlock.getDefaultState());
				world.setBlockState(center.add(x, height, z), wallBlock.getDefaultState());

				for (int y = 0; y < height; y++) {
					if (Math.abs(x) == radius || Math.abs(z) == radius) {
						world.setBlockState(center.add(x, y, z), wallBlock.getDefaultState());
					} else {
						world.setBlockState(center.add(x, y, z), Blocks.AIR.getDefaultState());
					}
				}
			}
		}
	}

	private static RegistryEntry<Enchantment> getEnchantment(ServerWorld world, RegistryKey<Enchantment> key) {
		return world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOptional(key).orElse(null);
	}

	// Randomized Loot Phase 1
	private static void dropPhase1Loot(ServerWorld world, BlockPos pos) {
		world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.GOLDEN_APPLE, 4 + random.nextInt(5))));
		world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.ENDER_PEARL, 4 + random.nextInt(9))));
		
		net.minecraft.item.Item food = random.nextBoolean() ? Items.COOKED_BEEF : Items.GOLDEN_CARROT;
		world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(food, 16)));
	}

	// Randomized Loot Phase 2
	private static void dropPhase2Loot(ServerWorld world, BlockPos pos) {
		ItemStack weapon = random.nextBoolean() ? new ItemStack(Items.BOW) : new ItemStack(Items.CROSSBOW);
		RegistryEntry<Enchantment> power = getEnchantment(world, Enchantments.POWER);
		RegistryEntry<Enchantment> unbreaking = getEnchantment(world, Enchantments.UNBREAKING);

		if (power != null) weapon.addEnchantment(power, 3 + random.nextInt(2));
		if (unbreaking != null) weapon.addEnchantment(unbreaking, 3);

		world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), weapon));
		world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1 + random.nextInt(3))));
		world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.ARROW, 32 + random.nextInt(33))));
	}

	// Fully Randomized Loot Final Phase
	private static void dropFinalLoot(ServerWorld world, BlockPos pos) {
		RegistryEntry<Enchantment> sharpness = getEnchantment(world, Enchantments.SHARPNESS);
		RegistryEntry<Enchantment> unbreaking = getEnchantment(world, Enchantments.UNBREAKING);
		RegistryEntry<Enchantment> looting = getEnchantment(world, Enchantments.LOOTING);
		RegistryEntry<Enchantment> protection = getEnchantment(world, Enchantments.PROTECTION);
		RegistryEntry<Enchantment> mending = getEnchantment(world, Enchantments.MENDING);

		// 1. Random Weapon (Sword or Axe)
		ItemStack mainWeapon;
		if (random.nextBoolean()) {
			mainWeapon = new ItemStack(Items.NETHERITE_SWORD);
			mainWeapon.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Nightmare Slayer").formatted(Formatting.GOLD, Formatting.BOLD));
			if (sharpness != null) mainWeapon.addEnchantment(sharpness, 5);
		} else {
			mainWeapon = new ItemStack(Items.NETHERITE_AXE);
			mainWeapon.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Sovereign Executioner").formatted(Formatting.RED, Formatting.BOLD));
			if (sharpness != null) mainWeapon.addEnchantment(sharpness, 5);
		}
		if (unbreaking != null) mainWeapon.addEnchantment(unbreaking, 3);
		if (looting != null) mainWeapon.addEnchantment(looting, 3);
		world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), mainWeapon));

		// 2. Random Armor Piece (Chestplate, Helmet, Leggings, OR Elytra)
		ItemStack armorPiece;
		int armorType = random.nextInt(4);
		switch (armorType) {
			case 0 -> armorPiece = new ItemStack(Items.NETHERITE_CHESTPLATE);
			case 1 -> armorPiece = new ItemStack(Items.NETHERITE_HELMET);
			case 2 -> armorPiece = new ItemStack(Items.NETHERITE_LEGGINGS);
			default -> armorPiece = new ItemStack(Items.ELYTRA);
		}
		if (protection != null && armorType != 3) armorPiece.addEnchantment(protection, 4);
		if (unbreaking != null) armorPiece.addEnchantment(unbreaking, 3);
		if (mending != null) armorPiece.addEnchantment(mending, 1);
		world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), armorPiece));

		// 3. Random Consumables & Valuables
		world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.TOTEM_OF_UNDYING, 2 + random.nextInt(3))));
		world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.NETHERITE_INGOT, 4 + random.nextInt(9))));
		
		net.minecraft.item.Item rareBlock = random.nextBoolean() ? Items.DIAMOND_BLOCK : Items.NETHERITE_BLOCK;
		world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(rareBlock, 2 + random.nextInt(4))));
	}
}
