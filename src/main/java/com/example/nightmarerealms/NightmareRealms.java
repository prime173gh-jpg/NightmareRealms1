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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class NightmareRealms implements ModInitializer {

	public static final String MOD_ID = "nightmarerealms";

	// ==================== تسجيل المواد المخصصة الجديدة ====================
	
	// 1. درع الأندرميت (ينزل بعد Phase 1)
	public static final Item ENDERMITE_CHESTPLATE = Registry.register(
		Registries.ITEM,
		Identifier.of(MOD_ID, "endermite_chestplate"),
		new Item(new Item.Settings().maxDamage(500))
	);

	// 2. سيف الكوابيس (ينزل بعد Phase 2)
	public static final Item DREAD_BLADE = Registry.register(
		Registries.ITEM,
		Identifier.of(MOD_ID, "dread_blade"),
		new Item(new Item.Settings().maxDamage(2031))
	);

	// 3. تاج الكوابيس (ينزل بعد Phase 3)
	public static final Item NIGHTMARE_CROWN = Registry.register(
		Registries.ITEM,
		Identifier.of(MOD_ID, "nightmare_crown"),
		new Item(new Item.Settings().maxDamage(600))
	);

	// =======================================================================

	private static ServerBossBar bossBar;
	private static int currentPhase = 0;
	private static int tickCounter = 0;
	private static int cooldownTimer = 0;
	private static BlockPos arenaCenter;
	private static final Random random = new Random();

	private static EndermanEntity phase1Boss;
	private static WitherSkeletonEntity phase2Boss;
	private static WitherEntity phase3Boss;

	// نصف قطر 15 يجعل الحلبة بمساحة 30x30
	private static final int ARENA_RADIUS = 15;
	private static final int ARENA_HEIGHT = 7;

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

		buildEnclosedArena(world, pos, ARENA_RADIUS, ARENA_HEIGHT);

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
			dropRandomizedLoot((ServerWorld) phase1Boss.getEntityWorld(), phase1Boss.getBlockPos(), 1);
			startCooldown(1);
			return;
		}
		bossBar.setPercent(phase1Boss.getHealth() / phase1Boss.getMaxHealth());

		// هيل 15% كل 13 ثانية (260 تيك)
		if (tickCounter % 260 == 0) {
			healBoss(phase1Boss, 300.0, 0.15);
		}

		if (tickCounter % 40 == 0 && phase1Boss.getTarget() != null) {
			ServerWorld world = (ServerWorld) phase1Boss.getEntityWorld();
			BlockPos targetPos = phase1Boss.getTarget().getBlockPos().add(world.random.nextInt(7) - 3, 0, world.random.nextInt(7) - 3);
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
		cooldownTimer = 200;
		bossBar.setPercent(1.0f);
	}

	private static void startPhase2Actual() {
		currentPhase = 2;
		ServerWorld world = (ServerWorld) bossBar.getPlayers().iterator().next().getEntityWorld();

		bossBar.setName(Text.literal("Dread Knight - Phase II").formatted(Formatting.DARK_RED, Formatting.BOLD));
		bossBar.setColor(BossBar.Color.RED);

		buildEnclosedArena(world, arenaCenter, ARENA_RADIUS, ARENA_HEIGHT);

		phase2Boss = new WitherSkeletonEntity(EntityType.WITHER_SKELETON, world);
		phase2Boss.refreshPositionAndAngles(arenaCenter.getX() + 0.5, arenaCenter.getY() + 1, arenaCenter.getZ() + 0.5, 0, 0);

		phase2Boss.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(450.0);
		phase2Boss.setHealth(450.0f);
		phase2Boss.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).setBaseValue(16.0);

		phase2Boss.equipStack(EquipmentSlot.MAINHAND, new ItemStack(random.nextBoolean() ? Items.DIAMOND_SWORD : Items.NETHERITE_SWORD));
		phase2Boss.equipStack(EquipmentSlot.CHEST, new ItemStack(random.nextBoolean() ? Items.DIAMOND_CHESTPLATE : Items.NETHERITE_CHESTPLATE));
		phase2Boss.setCustomName(Text.literal("Dread Knight").formatted(Formatting.RED, Formatting.BOLD));
		phase2Boss.setCustomNameVisible(true);

		world.spawnEntity(phase2Boss);
	}

	private static void updatePhase2() {
		if (!phase2Boss.isAlive()) {
			dropRandomizedLoot((ServerWorld) phase2Boss.getEntityWorld(), phase2Boss.getBlockPos(), 2);
			startCooldown(2);
			return;
		}
		bossBar.setPercent(phase2Boss.getHealth() / phase2Boss.getMaxHealth());

		// هيل 15% كل 13 ثانية (260 تيك)
		if (tickCounter % 260 == 0) {
			healBoss(phase2Boss, 450.0, 0.15);
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

		buildEnclosedArena(world, arenaCenter, ARENA_RADIUS, ARENA_HEIGHT);

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
			ServerWorld world = (ServerWorld) phase3Boss.getEntityWorld();

			dropRandomizedLoot(world, phase3Boss.getBlockPos(), 3);
			clearArena(world, arenaCenter, ARENA_RADIUS, ARENA_HEIGHT);

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

			if (tickCounter % 200 == 0) {
				player.damage(world, world.getDamageSources().wither(), 10.0f);
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 100, 1));
				player.sendMessage(Text.literal("Dark Death Strike hit you!").formatted(Formatting.DARK_GRAY, Formatting.BOLD), true);
			}

			if (tickCounter % 360 == 0) {
				LightningEntity lightning = new LightningEntity(EntityType.LIGHTNING_BOLT, world);
				lightning.refreshPositionAfterTeleport(player.getX(), player.getY(), player.getZ());
				world.spawnEntity(lightning);
				player.sendMessage(Text.literal("Nightmare Lightning struck you!").formatted(Formatting.GOLD, Formatting.BOLD), true);
			}

			if (tickCounter % 500 == 0) {
				player.damage(world, world.getDamageSources().magic(), 12.0f);
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0));
				player.sendMessage(Text.literal("Blinded by Darkness!").formatted(Formatting.DARK_PURPLE, Formatting.BOLD), true);
			}
		}

		// هيل 15% كل 13 ثانية (260 تيك)
		if (tickCounter % 260 == 0) {
			healBoss(phase3Boss, 350.0, 0.15);
		}

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

	// بناء الحلبة 30x30 وتأمين إضاءتها بـ Sea Lanterns
	private static void buildEnclosedArena(ServerWorld world, BlockPos center, int radius, int height) {
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				// الأرضية والسقف بيدروك
				world.setBlockState(center.add(x, -1, z), Blocks.BEDROCK.getDefaultState());
				world.setBlockState(center.add(x, height, z), Blocks.BEDROCK.getDefaultState());

				for (int y = 0; y < height; y++) {
					if (Math.abs(x) == radius || Math.abs(z) == radius) {
						// إضاءة أركان الجدران
						if ((Math.abs(x) == radius - 3 || Math.abs(x) == radius) && (Math.abs(z) == radius - 3 || Math.abs(z) == radius) && y == 3) {
							world.setBlockState(center.add(x, y, z), Blocks.SEA_LANTERN.getDefaultState());
						} else {
							world.setBlockState(center.add(x, y, z), Blocks.BEDROCK.getDefaultState());
						}
					} else {
						// إضاءة ملفتة وموزعة في السقف
						if (y == height - 1 && (Math.abs(x) % 6 == 0 && Math.abs(z) % 6 == 0)) {
							world.setBlockState(center.add(x, y, z), Blocks.SEA_LANTERN.getDefaultState());
						} else {
							world.setBlockState(center.add(x, y, z), Blocks.AIR.getDefaultState());
						}
					}
				}
			}
		}
	}

	// إزالة الحلبة بالكامل بعد القتال
	private static void clearArena(ServerWorld world, BlockPos center, int radius, int height) {
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				for (int y = -1; y <= height; y++) {
					BlockPos pos = center.add(x, y, z);
					if (world.getBlockState(pos).isOf(Blocks.BEDROCK) || world.getBlockState(pos).isOf(Blocks.SEA_LANTERN)) {
						world.setBlockState(pos, Blocks.AIR.getDefaultState());
					}
				}
			}
		}
	}

	private static RegistryEntry<Enchantment> getEnchantment(ServerWorld world, RegistryKey<Enchantment> key) {
		return world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOptional(key).orElse(null);
	}

	// ==================== نظام اللوت العشوائي والتسقيط المخصص ====================

	private static void dropRandomizedLoot(ServerWorld world, BlockPos pos, int phase) {
		// 1. تسقيط الأيتم الخاص بالمرحلة بشكل مضمون
		ItemStack guaranteedCustomLoot = ItemStack.EMPTY;
		if (phase == 1) {
			guaranteedCustomLoot = new ItemStack(ENDERMITE_CHESTPLATE);
		} else if (phase == 2) {
			guaranteedCustomLoot = new ItemStack(DREAD_BLADE);
		} else if (phase == 3) {
			guaranteedCustomLoot = new ItemStack(NIGHTMARE_CROWN);
		}

		if (!guaranteedCustomLoot.isEmpty()) {
			world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), guaranteedCustomLoot));
		}

		// 2. تسقيط بقية المكافآت العشوائية
		int numberOfItems = 3 + random.nextInt(phase + 2);
		for (int i = 0; i < numberOfItems; i++) {
			ItemStack lootItem = generateRandomItem(world, phase);
			if (!lootItem.isEmpty()) {
				world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), lootItem));
			}
		}
	}

	private static ItemStack generateRandomItem(ServerWorld world, int phase) {
		int category = random.nextInt(4);

		RegistryEntry<Enchantment> sharpness = getEnchantment(world, Enchantments.SHARPNESS);
		RegistryEntry<Enchantment> power = getEnchantment(world, Enchantments.POWER);
		RegistryEntry<Enchantment> unbreaking = getEnchantment(world, Enchantments.UNBREAKING);
		RegistryEntry<Enchantment> protection = getEnchantment(world, Enchantments.PROTECTION);
		RegistryEntry<Enchantment> mending = getEnchantment(world, Enchantments.MENDING);

		boolean isNetheriteAllowed = (phase >= 2) && random.nextBoolean();

		switch (category) {
			case 0 -> {
				Item weaponItem;
				int roll = random.nextInt(4);
				if (roll == 0) weaponItem = isNetheriteAllowed ? Items.NETHERITE_SWORD : Items.DIAMOND_SWORD;
				else if (roll == 1) weaponItem = isNetheriteAllowed ? Items.NETHERITE_AXE : Items.DIAMOND_AXE;
				else if (roll == 2) weaponItem = Items.BOW;
				else weaponItem = Items.CROSSBOW;

				ItemStack weapon = new ItemStack(weaponItem);
				if (weaponItem == Items.BOW || weaponItem == Items.CROSSBOW) {
					if (power != null) weapon.addEnchantment(power, 2 + random.nextInt(4));
				} else {
					if (sharpness != null) weapon.addEnchantment(sharpness, 2 + random.nextInt(4));
				}
				if (unbreaking != null) weapon.addEnchantment(unbreaking, 1 + random.nextInt(3));
				if (phase == 3 && mending != null && random.nextBoolean()) weapon.addEnchantment(mending, 1);

				if (phase == 3) {
					weapon.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Nightmare Conqueror").formatted(Formatting.GOLD, Formatting.BOLD));
				}
				return weapon;
			}
			case 1 -> {
				if (phase == 3 && random.nextInt(4) == 0) {
					ItemStack elytra = new ItemStack(Items.ELYTRA);
					if (unbreaking != null) elytra.addEnchantment(unbreaking, 3);
					if (mending != null) elytra.addEnchantment(mending, 1);
					return elytra;
				}

				Item armorItem;
				int armorPiece = random.nextInt(4);
				if (armorPiece == 0) armorItem = isNetheriteAllowed ? Items.NETHERITE_HELMET : Items.DIAMOND_HELMET;
				else if (armorPiece == 1) armorItem = isNetheriteAllowed ? Items.NETHERITE_CHESTPLATE : Items.DIAMOND_CHESTPLATE;
				else if (armorPiece == 2) armorItem = isNetheriteAllowed ? Items.NETHERITE_LEGGINGS : Items.DIAMOND_LEGGINGS;
				else armorItem = isNetheriteAllowed ? Items.NETHERITE_BOOTS : Items.DIAMOND_BOOTS;

				ItemStack armor = new ItemStack(armorItem);
				if (protection != null) armor.addEnchantment(protection, 2 + random.nextInt(3));
				if (unbreaking != null) armor.addEnchantment(unbreaking, 1 + random.nextInt(3));
				return armor;
			}
			case 2 -> {
				List<Item> consumables = new ArrayList<>();
				consumables.add(Items.GOLDEN_APPLE);
				consumables.add(Items.ENCHANTED_GOLDEN_APPLE);
				consumables.add(Items.GOLDEN_CARROT);
				consumables.add(Items.ENDER_PEARL);
				consumables.add(Items.EXPERIENCE_BOTTLE);

				Item selectedFood = consumables.get(random.nextInt(consumables.size()));
				int amount = selectedFood == Items.ENCHANTED_GOLDEN_APPLE ? 1 + random.nextInt(2) : 4 + random.nextInt(12);
				return new ItemStack(selectedFood, amount);
			}
			default -> {
				if (phase == 1) {
					return new ItemStack(random.nextBoolean() ? Items.GOLD_INGOT : Items.DIAMOND, 4 + random.nextInt(6));
				} else if (phase == 2) {
					Item mat = random.nextBoolean() ? Items.DIAMOND_BLOCK : Items.NETHERITE_INGOT;
					return new ItemStack(mat, 2 + random.nextInt(5));
				} else {
					int rareType = random.nextInt(3);
					if (rareType == 0) return new ItemStack(Items.TOTEM_OF_UNDYING, 1 + random.nextInt(3));
					if (rareType == 1) return new ItemStack(Items.NETHERITE_BLOCK, 1 + random.nextInt(3));
					return new ItemStack(Items.DIAMOND_BLOCK, 3 + random.nextInt(6));
				}
			}
		}
	}
}
