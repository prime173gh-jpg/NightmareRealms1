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
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
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

	// حلبة ضخمة 64×64 (نصف القطر 32)
	private static final int ARENA_RADIUS = 32;
	private static final int ARENA_HEIGHT = 12;

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

			if (currentPhase == 1 && phase1Boss != null && phase1Boss.isAlive()) {
				updatePhase1();
				spawnBossAura((ServerWorld) phase1Boss.getEntityWorld(), phase1Boss.getX(), phase1Boss.getY(), phase1Boss.getZ(), 1);
			} else if (currentPhase == 2 && phase2Boss != null && phase2Boss.isAlive()) {
				updatePhase2();
				spawnBossAura((ServerWorld) phase2Boss.getEntityWorld(), phase2Boss.getX(), phase2Boss.getY(), phase2Boss.getZ(), 2);
			} else if (currentPhase == 3 && phase3Boss != null && phase3Boss.isAlive()) {
				updatePhase3();
				spawnBossAura((ServerWorld) phase3Boss.getEntityWorld(), phase3Boss.getX(), phase3Boss.getY(), phase3Boss.getZ(), 3);
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
		return 1;
	}

	public static void startBossFight(ServerWorld world, BlockPos pos, ServerPlayerEntity player) {
		currentPhase = 1;
		arenaCenter = pos;
		bossBar.addPlayer(player);
		bossBar.setName(Text.literal("Shadow Sovereign - Phase I").formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
		bossBar.setColor(BossBar.Color.PURPLE);
		bossBar.setVisible(true);

		// 1. نقل اللاعب بعيداً عن السنتر بـ 15 بلوكة حتى لا يرسبن فوق البوس
		player.requestTeleport(pos.getX() + 15.5, pos.getY() + 1.0, pos.getZ() + 0.5);

		// 2. بناء الحلبة وتحديد ديكور Phase 1
		buildColosseumArena(world, pos, ARENA_RADIUS, ARENA_HEIGHT, 1);

		// 3. انترو سينمائي ملحمي
		showCinematicIntro(player, "⚡ SHADOW SOVEREIGN ⚡", "Phase I: The Darkness Awakens", Formatting.DARK_PURPLE);
		playEntranceEffects(world, pos, 1);

		// 4. ترسبن البوس في السنتر تماماً
		phase1Boss = new EndermanEntity(EntityType.ENDERMAN, world);
		phase1Boss.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 0, 0);

		phase1Boss.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(300.0);
		phase1Boss.setHealth(300.0f);
		phase1Boss.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).setBaseValue(10.0);
		phase1Boss.setCustomName(Text.literal("⚡ SHADOW SOVEREIGN ⚡").formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
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

		if (phase1Boss.getBlockPos().getSquaredDistance(arenaCenter) > (ARENA_RADIUS - 3) * (ARENA_RADIUS - 3)) {
			phase1Boss.teleport(arenaCenter.getX() + 0.5, arenaCenter.getY() + 1, arenaCenter.getZ() + 0.5, true);
		}

		if (tickCounter % 260 == 0) {
			healBoss(phase1Boss, 300.0, 0.15);
		}

		if (tickCounter % 50 == 0 && phase1Boss.getTarget() != null) {
			ServerWorld world = (ServerWorld) phase1Boss.getEntityWorld();
			BlockPos targetPos = phase1Boss.getTarget().getBlockPos().add(world.random.nextInt(11) - 5, 0, world.random.nextInt(11) - 5);
			
			if (targetPos.getSquaredDistance(arenaCenter) < (ARENA_RADIUS - 4) * (ARENA_RADIUS - 4)) {
				phase1Boss.teleport(targetPos.getX(), targetPos.getY(), targetPos.getZ(), true);
				world.spawnParticles(ParticleTypes.PORTAL, phase1Boss.getX(), phase1Boss.getY() + 1, phase1Boss.getZ(), 40, 0.5, 1.0, 0.5, 0.1);
			}
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
		ServerPlayerEntity player = bossBar.getPlayers().iterator().next();

		bossBar.setName(Text.literal("Dread Knight - Phase II").formatted(Formatting.DARK_RED, Formatting.BOLD));
		bossBar.setColor(BossBar.Color.RED);

		// تحديث ديكور الحلبة لتناسب Phase 2 (Fire & Lava Theme)
		buildColosseumArena(world, arenaCenter, ARENA_RADIUS, ARENA_HEIGHT, 2);

		player.requestTeleport(arenaCenter.getX() + 15.5, arenaCenter.getY() + 1.0, arenaCenter.getZ() + 0.5);

		showCinematicIntro(player, "🔥 DREAD KNIGHT 🔥", "Phase II: The Nether's Vengeance", Formatting.RED);
		playEntranceEffects(world, arenaCenter, 2);

		phase2Boss = new WitherSkeletonEntity(EntityType.WITHER_SKELETON, world);
		phase2Boss.refreshPositionAndAngles(arenaCenter.getX() + 0.5, arenaCenter.getY() + 1, arenaCenter.getZ() + 0.5, 0, 0);

		phase2Boss.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(450.0);
		phase2Boss.setHealth(450.0f);
		phase2Boss.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).setBaseValue(16.0);

		phase2Boss.equipStack(EquipmentSlot.MAINHAND, new ItemStack(random.nextBoolean() ? Items.DIAMOND_SWORD : Items.NETHERITE_SWORD));
		phase2Boss.equipStack(EquipmentSlot.CHEST, new ItemStack(random.nextBoolean() ? Items.DIAMOND_CHESTPLATE : Items.NETHERITE_CHESTPLATE));
		phase2Boss.setCustomName(Text.literal("🔥 DREAD KNIGHT 🔥").formatted(Formatting.RED, Formatting.BOLD));
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
		ServerPlayerEntity player = bossBar.getPlayers().iterator().next();

		bossBar.setName(Text.literal("Nightmare Wither - Final Phase").formatted(Formatting.DARK_GRAY, Formatting.BOLD));
		bossBar.setColor(BossBar.Color.WHITE);

		// تحديث ديكور الحلبة لتناسب Phase 3 (Soul & Death Theme)
		buildColosseumArena(world, arenaCenter, ARENA_RADIUS, ARENA_HEIGHT, 3);

		player.requestTeleport(arenaCenter.getX() + 15.5, arenaCenter.getY() + 1.0, arenaCenter.getZ() + 0.5);

		showCinematicIntro(player, "☠️ NIGHTMARE WITHER ☠️", "Final Phase: Face Absolute Death", Formatting.DARK_GRAY);
		playEntranceEffects(world, arenaCenter, 3);

		phase3Boss = new WitherEntity(EntityType.WITHER, world);
		phase3Boss.refreshPositionAndAngles(arenaCenter.getX() + 0.5, arenaCenter.getY() + 2.0, arenaCenter.getZ() + 0.5, 0, 0);

		phase3Boss.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(350.0);
		phase3Boss.setHealth(350.0f);
		phase3Boss.setCustomName(Text.literal("☠️ NIGHTMARE WITHER ☠️").formatted(Formatting.DARK_GRAY, Formatting.BOLD));
		phase3Boss.setCustomNameVisible(true);

		if (!bossBar.getPlayers().isEmpty()) {
			ServerPlayerEntity targetPlayer = bossBar.getPlayers().iterator().next();
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

	// ==================== سينمائيات الانترو والمؤثرات ====================

	private static void showCinematicIntro(ServerPlayerEntity player, String titleText, String subtitleText, Formatting color) {
		player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(titleText).formatted(color, Formatting.BOLD)));
		player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(subtitleText).formatted(Formatting.GRAY, Formatting.ITALIC)));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 30, 0, false, false));
	}

	private static void playEntranceEffects(ServerWorld world, BlockPos pos, int phase) {
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 1.0;
		double z = pos.getZ() + 0.5;

		if (phase == 1) {
			world.playSound(null, pos, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 3.0f, 0.5f);
			world.spawnParticles(ParticleTypes.WITCH, x, y, z, 300, 2.0, 3.0, 2.0, 0.1);
			world.spawnParticles(ParticleTypes.PORTAL, x, y, z, 200, 3.0, 3.0, 3.0, 0.1);
		} else if (phase == 2) {
			world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 3
