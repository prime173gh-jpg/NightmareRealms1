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
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.entity.mob.ZombieEntity;
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
	private static int currentPhase = 0; // 1..3 Mobs, 4..7 Bosses
	private static int tickCounter = 0;
	private static int cooldownTimer = 0;
	private static BlockPos arenaCenter;
	private static final Random random = new Random();

	// Mobs and Boss references
	private static final List<net.minecraft.entity.mob.MobEntity> currentWaveMobs = new ArrayList<>();
	private static EndermanEntity phase1Boss;
	private static WitherSkeletonEntity phase2Boss;
	private static IronGolemEntity ironTitanBoss;
	private static WitherEntity phase3Boss;

	private static final int ARENA_RADIUS = 32;
	private static final int ARENA_HEIGHT = 12;

	@Override
	public void onInitialize() {
		bossBar = new ServerBossBar(
				Text.literal("Nightmare Realm").formatted(Formatting.DARK_PURPLE, Formatting.BOLD),
				BossBar.Color.PURPLE,
				BossBar.Style.NOTCHED_10
		);

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tickCounter++;

			if (cooldownTimer > 0) {
				cooldownTimer--;
				if (cooldownTimer % 20 == 0) {
					int secondsLeft = cooldownTimer / 20;
					String nextText = currentPhase == 6 ? "PREPARE FOR THE BIG BOSS IN: " : "Next Wave in: ";
					bossBar.setName(Text.literal(nextText + secondsLeft + "s").formatted(Formatting.YELLOW, Formatting.BOLD));
				}
				if (cooldownTimer == 0) {
					startNextPhaseActual();
				}
				return;
			}

			if (currentPhase >= 1 && currentPhase <= 3) {
				updateNormalMobWave();
			} else if (currentPhase == 4) {
				updateBossEnderman();
			} else if (currentPhase == 5) {
				updateBossWitherSkeleton();
			} else if (currentPhase == 6) {
				updateBossIronTitan();
			} else if (currentPhase == 7) {
				updateBossFinalWither();
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

		startRealmChallenge(source.getWorld(), player.getBlockPos(), player);
		return 1;
	}

	public static void startRealmChallenge(ServerWorld world, BlockPos pos, ServerPlayerEntity player) {
		currentPhase = 1;
		arenaCenter = pos;
		bossBar.addPlayer(player);
		bossBar.setVisible(true);

		player.requestTeleport(pos.getX() + 15.5, pos.getY() + 1.0, pos.getZ() + 0.5);
		startWave1(world, player);
	}

	// ==================== WAVES LOGIC ====================

	private static void startWave1(ServerWorld world, ServerPlayerEntity player) {
		buildColosseumArena(world, arenaCenter, ARENA_RADIUS, ARENA_HEIGHT, 1);
		showCinematicIntro(player, "⚔️ WAVE 1: ZOMBIE HORDE ⚔️", "Survive the Enraged Swarm!", Formatting.GREEN);

		bossBar.setName(Text.literal("Wave 1/7 - Enraged Zombies").formatted(Formatting.GREEN, Formatting.BOLD));
		bossBar.setColor(BossBar.Color.GREEN);

		currentWaveMobs.clear();
		for (int i = 0; i < 6; i++) {
			ZombieEntity zombie = new ZombieEntity(world);
			zombie.refreshPositionAndAngles(arenaCenter.getX() + random.nextInt(10) - 5, arenaCenter.getY() + 1, arenaCenter.getZ() + random.nextInt(10) - 5, 0, 0);
			zombie.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 99999, 1));
			zombie.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 99999, 1));
			zombie.setCustomName(Text.literal("Berserk Zombie").formatted(Formatting.DARK_GREEN));
			zombie.setCustomNameVisible(true);
			world.spawnEntity(zombie);
			currentWaveMobs.add(zombie);
		}
	}

	private static void startWave2(ServerWorld world, ServerPlayerEntity player) {
		buildColosseumArena(world, arenaCenter, ARENA_RADIUS, ARENA_HEIGHT, 1);
		showCinematicIntro(player, "🏹 WAVE 2: SKELETON SNIPERS 🏹", "Watch Out for Reinforced Arrows!", Formatting.GOLD);

		bossBar.setName(Text.literal("Wave 2/7 - Skeleton Snipers").formatted(Formatting.GOLD, Formatting.BOLD));
		bossBar.setColor(BossBar.Color.YELLOW);

		currentWaveMobs.clear();
		for (int i = 0; i < 6; i++) {
			SkeletonEntity skeleton = new SkeletonEntity(EntityType.SKELETON, world);
			skeleton.refreshPositionAndAngles(arenaCenter.getX() + random.nextInt(12) - 6, arenaCenter.getY() + 1, arenaCenter.getZ() + random.nextInt(12) - 6, 0, 0);
			skeleton.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 99999, 1));
			skeleton.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 99999, 1));
			skeleton.setCustomName(Text.literal("Armored Sniper").formatted(Formatting.GOLD));
			skeleton.setCustomNameVisible(true);
			world.spawnEntity(skeleton);
			currentWaveMobs.add(skeleton);
		}
	}

	private static void startWave3(ServerWorld world, ServerPlayerEntity player) {
		buildColosseumArena(world, arenaCenter, ARENA_RADIUS, ARENA_HEIGHT, 1);
		showCinematicIntro(player, "🕷️ WAVE 3: SHADOW ARACHNIDS 🕷️", "Final Mob Wave Before The Bosses!", Formatting.RED);

		bossBar.setName(Text.literal("Wave 3/7 - Venomous Arachnids").formatted(Formatting.RED, Formatting.BOLD));
		bossBar.setColor(BossBar.Color.RED);

		currentWaveMobs.clear();
		for (int i = 0; i < 6; i++) {
			SpiderEntity spider = new SpiderEntity(EntityType.SPIDER, world);
			spider.refreshPositionAndAngles(arenaCenter.getX() + random.nextInt(12) - 6, arenaCenter.getY() + 1, arenaCenter.getZ() + random.nextInt(12) - 6, 0, 0);
			spider.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 99999, 1));
			spider.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 99999, 1));
			spider.setCustomName(Text.literal("Venomous Stalker").formatted(Formatting.DARK_RED));
			spider.setCustomNameVisible(true);
			world.spawnEntity(spider);
			currentWaveMobs.add(spider);
		}
	}

	private static void updateNormalMobWave() {
		currentWaveMobs.removeIf(mob -> mob == null || !mob.isAlive() || mob.isRemoved());
		float remainingPercent = (float) currentWaveMobs.size() / 6.0f;
		bossBar.setPercent(Math.max(0.0f, remainingPercent));

		if (currentWaveMobs.isEmpty()) {
			ServerWorld world = (ServerWorld) bossBar.getPlayers().iterator().next().getEntityWorld();
			dropRandomizedLoot(world, arenaCenter, currentPhase);
			startCooldown(currentPhase, 300); // 15 ثواني
		}
	}

	// ==================== BOSSES LOGIC ====================

	private static void startBossEndermanActual() {
		currentPhase = 4;
		ServerWorld world = (ServerWorld) bossBar.getPlayers().iterator().next().getEntityWorld();
		ServerPlayerEntity player = bossBar.getPlayers().iterator().next();

		bossBar.setName(Text.literal("Shadow Sovereign - Phase IV").formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
		bossBar.setColor(BossBar.Color.PURPLE);

		player.requestTeleport(arenaCenter.getX() + 15.5, arenaCenter.getY() + 1.0, arenaCenter.getZ() + 0.5);
		buildColosseumArena(world, arenaCenter, ARENA_RADIUS, ARENA_HEIGHT, 2);

		showCinematicIntro(player, "⚡ SHADOW SOVEREIGN ⚡", "The First Boss Awakens!", Formatting.DARK_PURPLE);
		playEntranceEffects(world, arenaCenter, 1);

		phase1Boss = new EndermanEntity(EntityType.ENDERMAN, world);
		phase1Boss.refreshPositionAndAngles(arenaCenter.getX() + 0.5, arenaCenter.getY() + 1, arenaCenter.getZ() + 0.5, 0, 0);
		phase1Boss.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(300.0);
		phase1Boss.setHealth(300.0f);
		phase1Boss.setCustomName(Text.literal("⚡ SHADOW SOVEREIGN ⚡").formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
		phase1Boss.setCustomNameVisible(true);

		world.spawnEntity(phase1Boss);
	}

	private static void updateBossEnderman() {
		if (phase1Boss == null || !phase1Boss.isAlive() || phase1Boss.isRemoved()) {
			ServerWorld world = (ServerWorld) bossBar.getPlayers().iterator().next().getEntityWorld();
			dropRandomizedLoot(world, arenaCenter, 4);
			phase1Boss = null;
			startCooldown(4, 300); // 15 ثواني
			return;
		}

		spawnBossAura((ServerWorld) phase1Boss.getEntityWorld(), phase1Boss.getX(), phase1Boss.getY(), phase1Boss.getZ(), 1);
		bossBar.setPercent(phase1Boss.getHealth() / phase1Boss.getMaxHealth());

		if (phase1Boss.getBlockPos().getSquaredDistance(arenaCenter) > (ARENA_RADIUS - 3) * (ARENA_RADIUS - 3)) {
			phase1Boss.teleport(arenaCenter.getX() + 0.5, arenaCenter.getY() + 1, arenaCenter.getZ() + 0.5, true);
		}
	}

	private static void startBossWitherSkeletonActual() {
		currentPhase = 5;
		ServerWorld world = (ServerWorld) bossBar.getPlayers().iterator().next().getEntityWorld();
		ServerPlayerEntity player = bossBar.getPlayers().iterator().next();

		bossBar.setName(Text.literal("Dread Knight - Phase V").formatted(Formatting.DARK_RED, Formatting.BOLD));
		bossBar.setColor(BossBar.Color.RED);

		player.requestTeleport(arenaCenter.getX() + 15.5, arenaCenter.getY() + 1.0, arenaCenter.getZ() + 0.5);
		buildColosseumArena(world, arenaCenter, ARENA_RADIUS, ARENA_HEIGHT, 3);

		showCinematicIntro(player, "🔥 DREAD KNIGHT 🔥", "The Nether General Arrives!", Formatting.RED);
		playEntranceEffects(world, arenaCenter, 2);

		phase2Boss = new WitherSkeletonEntity(EntityType.WITHER_SKELETON, world);
		phase2Boss.refreshPositionAndAngles(arenaCenter.getX() + 0.5, arenaCenter.getY() + 1, arenaCenter.getZ() + 0.5, 0, 0);
		phase2Boss.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(450.0);
		phase2Boss.setHealth(450.0f);
		phase2Boss.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
		phase2Boss.setCustomName(Text.literal("🔥 DREAD KNIGHT 🔥").formatted(Formatting.RED, Formatting.BOLD));
		phase2Boss.setCustomNameVisible(true);

		world.spawnEntity(phase2Boss);
	}

	private static void updateBossWitherSkeleton() {
		if (phase2Boss == null || !phase2Boss.isAlive() || phase2Boss.isRemoved()) {
			ServerWorld world = (ServerWorld) bossBar.getPlayers().iterator().next().getEntityWorld();
			dropRandomizedLoot(world, arenaCenter, 5);
			phase2Boss = null;
			startCooldown(5, 300); // 15 ثواني
			return;
		}

		spawnBossAura((ServerWorld) phase2Boss.getEntityWorld(), phase2Boss.getX(), phase2Boss.getY(), phase2Boss.getZ(), 2);
		bossBar.setPercent(phase2Boss.getHealth() / phase2Boss.getMaxHealth());

		if (tickCounter % 80 == 0 && phase2Boss.getTarget() != null) {
			ServerWorld world = (ServerWorld) phase2Boss.getEntityWorld();
			Vec3d lookVec = phase2Boss.getRotationVec(1.0F);
			FireballEntity fireball = new FireballEntity(world, phase2Boss, lookVec, 1);
			fireball.setPosition(phase2Boss.getX() + lookVec.x, phase2Boss.getBodyY(0.5), phase2Boss.getZ() + lookVec.z);
			world.spawnEntity(fireball);
		}
	}

	private static void startBossIronTitanActual() {
		currentPhase = 6;
		ServerWorld world = (ServerWorld) bossBar.getPlayers().iterator().next().getEntityWorld();
		ServerPlayerEntity player = bossBar.getPlayers().iterator().next();

		bossBar.setName(Text.literal("IRON TITAN - Phase VI").formatted(Formatting.GOLD, Formatting.BOLD));
		bossBar.setColor(BossBar.Color.YELLOW);

		player.requestTeleport(arenaCenter.getX() + 15.5, arenaCenter.getY() + 1.0, arenaCenter.getZ() + 0.5);
		buildColosseumArena(world, arenaCenter, ARENA_RADIUS, ARENA_HEIGHT, 4); // Iron/Ancient Temple Theme

		showCinematicIntro(player, "🛡️ IRON TITAN 🛡️", "Unstoppable Ancient Defender!", Formatting.GOLD);
		playEntranceEffects(world, arenaCenter, 2);

		ironTitanBoss = new IronGolemEntity(EntityType.IRON_GOLEM, world);
		ironTitanBoss.refreshPositionAndAngles(arenaCenter.getX() + 0.5, arenaCenter.getY() + 1, arenaCenter.getZ() + 0.5, 0, 0);

		ironTitanBoss.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(600.0);
		ironTitanBoss.setHealth(600.0f);
		ironTitanBoss.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).setBaseValue(22.0);
		ironTitanBoss.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 99999, 1));
		ironTitanBoss.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 99999, 1));
		ironTitanBoss.setCustomName(Text.literal("🛡️ IRON TITAN 🛡️").formatted(Formatting.GOLD, Formatting.BOLD));
		ironTitanBoss.setCustomNameVisible(true);

		world.spawnEntity(ironTitanBoss);
	}

	private static void updateBossIronTitan() {
		if (ironTitanBoss == null || !ironTitanBoss.isAlive() || ironTitanBoss.isRemoved()) {
			ServerWorld world = (ServerWorld) bossBar.getPlayers().iterator().next().getEntityWorld();
			dropRandomizedLoot(world, arenaCenter, 6);
			ironTitanBoss = null;
			startCooldown(6, 400); // 20 ثانية راحة قبل البيج بوس الأخير!
			return;
		}

		spawnBossAura((ServerWorld) ironTitanBoss.getEntityWorld(), ironTitanBoss.getX(), ironTitanBoss.getY(), ironTitanBoss.getZ(), 2);
		bossBar.setPercent(ironTitanBoss.getHealth() / ironTitanBoss.getMaxHealth());

		// هزة أرضية وضربة زلزال لللاعبين كل بضعة ثواني
		if (tickCounter % 160 == 0 && ironTitanBoss.getTarget() instanceof ServerPlayerEntity player) {
			ServerWorld world = (ServerWorld) ironTitanBoss.getEntityWorld();
			player.damage(world, world.getDamageSources().generic(), 14.0f);
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 2));
			world.playSound(null, ironTitanBoss.getBlockPos(), SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.HOSTILE, 3.0f, 0.5f);
			player.sendMessage(Text.literal("The Iron Titan slammed the ground!").formatted(Formatting.GOLD, Formatting.BOLD), true);
		}
	}

	private static void startBossFinalWitherActual() {
		currentPhase = 7;
		ServerWorld world = (ServerWorld) bossBar.getPlayers().iterator().next().getEntityWorld();
		ServerPlayerEntity player = bossBar.getPlayers().iterator().next();

		bossBar.setName(Text.literal("NIGHTMARE WITHER - FINAL BIG BOSS").formatted(Formatting.DARK_GRAY, Formatting.BOLD));
		bossBar.setColor(BossBar.Color.WHITE);

		player.requestTeleport(arenaCenter.getX() + 15.5, arenaCenter.getY() + 1.0, arenaCenter.getZ() + 0.5);
		buildColosseumArena(world, arenaCenter, ARENA_RADIUS, ARENA_HEIGHT, 5); // Soul Death Theme

		showCinematicIntro(player, "☠️ NIGHTMARE WITHER ☠️", "THE BIG BOSS: FACE ABSOLUTE DEATH!", Formatting.DARK_GRAY);
		playEntranceEffects(world, arenaCenter, 3);

		phase3Boss = new WitherEntity(EntityType.WITHER, world);
		phase3Boss.refreshPositionAndAngles(arenaCenter.getX() + 0.5, arenaCenter.getY() + 2.0, arenaCenter.getZ() + 0.5, 0, 0);

		phase3Boss.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(500.0);
		phase3Boss.setHealth(500.0f);
		phase3Boss.setCustomName(Text.literal("☠️ NIGHTMARE WITHER ☠️").formatted(Formatting.DARK_GRAY, Formatting.BOLD));
		phase3Boss.setCustomNameVisible(true);

		if (!bossBar.getPlayers().isEmpty()) {
			phase3Boss.setTarget(bossBar.getPlayers().iterator().next());
		}

		world.spawnEntity(phase3Boss);
	}

	private static void updateBossFinalWither() {
		if (phase3Boss == null || !phase3Boss.isAlive() || phase3Boss.isRemoved()) {
			ServerWorld world = (ServerWorld) bossBar.getPlayers().iterator().next().getEntityWorld();

			dropRandomizedLoot(world, arenaCenter, 7);
			clearArena(world, arenaCenter, ARENA_RADIUS, ARENA_HEIGHT);

			currentPhase = 0;
			bossBar.setVisible(false);
			bossBar.clearPlayers();
			phase3Boss = null;
			return;
		}

		spawnBossAura((ServerWorld) phase3Boss.getEntityWorld(), phase3Boss.getX(), phase3Boss.getY(), phase3Boss.getZ(), 3);
		bossBar.setPercent(phase3Boss.getHealth() / phase3Boss.getMaxHealth());

		ServerWorld world = (ServerWorld) phase3Boss.getEntityWorld();

		if (!bossBar.getPlayers().isEmpty()) {
			ServerPlayerEntity player = bossBar.getPlayers().iterator().next();

			if (tickCounter % 200 == 0) {
				player.damage(world, world.getDamageSources().wither(), 12.0f);
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 100, 1));
			}

			if (tickCounter % 300 == 0) {
				LightningEntity lightning = new LightningEntity(EntityType.LIGHTNING_BOLT, world);
				lightning.refreshPositionAfterTeleport(player.getX(), player.getY(), player.getZ());
				world.spawnEntity(lightning);
			}
		}
	}

	// ==================== HELPER METHODS ====================

	private static void startNextPhaseActual() {
		ServerWorld world = (ServerWorld) bossBar.getPlayers().iterator().next().getEntityWorld();
		ServerPlayerEntity player = bossBar.getPlayers().iterator().next();

		if (currentPhase == 1) startWave2(world, player);
		else if (currentPhase == 2) startWave3(world, player);
		else if (currentPhase == 3) startBossEndermanActual();
		else if (currentPhase == 4) startBossWitherSkeletonActual();
		else if (currentPhase == 5) startBossIronTitanActual();
		else if (currentPhase == 6) startBossFinalWitherActual();
	}

	private static void startCooldown(int completedPhase, int ticks) {
		currentPhase = completedPhase;
		cooldownTimer = ticks; // 300 ticks = 15s, 400 ticks = 20s
		bossBar.setPercent(1.0f);
	}

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
		} else if (phase == 2) {
			world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 3.0f, 0.7f);
			world.spawnParticles(ParticleTypes.FLAME, x, y, z, 400, 3.0, 3.0, 3.0, 0.2);
		} else if (phase == 3) {
			LightningEntity lightning = new LightningEntity(EntityType.LIGHTNING_BOLT, world);
			lightning.refreshPositionAfterTeleport(x, y, z);
			world.spawnEntity(lightning);
			world.playSound(null, pos, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 3.0f, 0.5f);
		}
	}

	private static void spawnBossAura(ServerWorld world, double x, double y, double z, int phase) {
		if (phase == 1) {
			world.spawnParticles(ParticleTypes.PORTAL, x, y + 1.0, z, 8, 0.5, 1.0, 0.5, 0.05);
		} else if (phase == 2) {
			world.spawnParticles(ParticleTypes.FLAME, x, y + 1.0, z, 12, 0.6, 1.0, 0.6, 0.03);
		} else if (phase == 3) {
			world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y + 1.2, z, 15, 0.8, 1.0, 0.8, 0.04);
		}
	}

	private static void buildColosseumArena(ServerWorld world, BlockPos center, int radius, int height, int theme) {
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				double distance = Math.sqrt(x * x + z * z);

				if (distance <= radius) {
					BlockPos floorPos = center.add(x, -1, z);

					if (theme == 1) { // Normal Waves
						world.setBlockState(floorPos, Blocks.DEEPSLATE_BRICKS.getDefaultState());
					} else if (theme == 2) { // Enderman
						world.setBlockState(floorPos, (Math.abs(x) + Math.abs(z)) % 2 == 0 ? Blocks.CRYING_OBSIDIAN.getDefaultState() : Blocks.POLISHED_DEEPSLATE.getDefaultState());
					} else if (theme == 3) { // Nether Skeleton
						world.setBlockState(floorPos, (Math.abs(x) + Math.abs(z)) % 2 == 0 ? Blocks.NETHER_BRICKS.getDefaultState() : Blocks.MAGMA_BLOCK.getDefaultState());
					} else if (theme == 4) { // Iron Titan
						world.setBlockState(floorPos, (Math.abs(x) + Math.abs(z)) % 2 == 0 ? Blocks.MOSSY_DEEPSLATE_BRICKS.getDefaultState() : Blocks.COPPER_BLOCK.getDefaultState());
					} else { // Big Boss Wither
						world.setBlockState(floorPos, (Math.abs(x) + Math.abs(z)) % 2 == 0 ? Blocks.SCULK.getDefaultState() : Blocks.SOUL_SOIL.getDefaultState());
					}
				}

				for (int y = 0; y < height; y++) {
					BlockPos pos = center.add(x, y, z);
					if (distance >= radius - 1 && distance <= radius) {
						if (y == 0) world.setBlockState(pos, Blocks.POLISHED_BLACKSTONE_WALL.getDefaultState());
						else world.setBlockState(pos, Blocks.BARRIER.getDefaultState());
					} else if (distance < radius - 1 && y > 2) {
						world.setBlockState(pos, Blocks.AIR.getDefaultState());
					}
				}
			}
		}
	}

	private static void clearArena(ServerWorld world, BlockPos center, int radius, int height) {
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				for (int y = -1; y <= height; y++) {
					BlockPos pos = center.add(x, y, z);
					if (world.getBlockState(pos).isOf(Blocks.BARRIER) || 
						world.getBlockState(pos).isOf(Blocks.POLISHED_BLACKSTONE_WALL) || 
						world.getBlockState(pos).isOf(Blocks.DEEPSLATE_BRICKS) ||
						world.getBlockState(pos).isOf(Blocks.NETHER_BRICKS) ||
						world.getBlockState(pos).isOf(Blocks.MAGMA_BLOCK) ||
						world.getBlockState(pos).isOf(Blocks.SCULK) ||
						world.getBlockState(pos).isOf(Blocks.SOUL_SOIL) ||
						world.getBlockState(pos).isOf(Blocks.COPPER_BLOCK) ||
						world.getBlockState(pos).isOf(Blocks.MOSSY_DEEPSLATE_BRICKS) ||
						world.getBlockState(pos).isOf(Blocks.CRYING_OBSIDIAN) ||
						world.getBlockState(pos).isOf(Blocks.POLISHED_DEEPSLATE)) {
						world.setBlockState(pos, Blocks.AIR.getDefaultState());
					}
				}
			}
		}
	}

	private static RegistryEntry<Enchantment> getEnchantment(ServerWorld world, RegistryKey<Enchantment> key) {
		return world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOptional(key).orElse(null);
	}

	private static void dropRandomizedLoot(ServerWorld world, BlockPos pos, int phase) {
		ItemStack specialReward = ItemStack.EMPTY;
		RegistryEntry<Enchantment> sharpness = getEnchantment(world, Enchantments.SHARPNESS);
		RegistryEntry<Enchantment> protection = getEnchantment(world, Enchantments.PROTECTION);
		RegistryEntry<Enchantment> unbreaking = getEnchantment(world, Enchantments.UNBREAKING);

		if (phase == 4) {
			specialReward = new ItemStack(Items.NETHERITE_CHESTPLATE);
			specialReward.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Endermite Chestplate").formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
			if (protection != null) specialReward.addEnchantment(protection, 4);
		} else if (phase == 5) {
			specialReward = new ItemStack(Items.NETHERITE_SWORD);
			specialReward.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Dread Blade").formatted(Formatting.RED, Formatting.BOLD));
			if (sharpness != null) specialReward.addEnchantment(sharpness, 5);
		} else if (phase == 6) {
			specialReward = new ItemStack(Items.NETHERITE_AXE);
			specialReward.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Titan Cleaver").formatted(Formatting.GOLD, Formatting.BOLD));
			if (sharpness != null) specialReward.addEnchantment(sharpness, 5);
		} else if (phase == 7) {
			specialReward = new ItemStack(Items.NETHERITE_HELMET);
			specialReward.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Nightmare Crown").formatted(Formatting.GOLD, Formatting.BOLD));
			if (protection != null) specialReward.addEnchantment(protection, 5);
		}

		if (!specialReward.isEmpty()) {
			world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY() + 1.0, pos.getZ(), specialReward));
		}

		int numberOfItems = 3 + random.nextInt(phase + 1);
		for (int i = 0; i < numberOfItems; i++) {
			ItemStack lootItem = generateRandomItem(world, phase);
			if (!lootItem.isEmpty()) {
				world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY() + 1.0, pos.getZ(), lootItem));
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

		boolean isNetheriteAllowed = (phase >= 4) && random.nextBoolean();

		switch (category) {
			case 0 -> {
				Item weaponItem = isNetheriteAllowed ? Items.NETHERITE_SWORD : Items.DIAMOND_SWORD;
				ItemStack weapon = new ItemStack(weaponItem);
				if (sharpness != null) weapon.addEnchantment(sharpness, 2 + random.nextInt(3));
				if (unbreaking != null) weapon.addEnchantment(unbreaking, 1 + random.nextInt(3));
				return weapon;
			}
			case 1 -> {
				Item armorItem = isNetheriteAllowed ? Items.NETHERITE_CHESTPLATE : Items.DIAMOND_CHESTPLATE;
				ItemStack armor = new ItemStack(armorItem);
				if (protection != null) armor.addEnchantment(protection, 2 + random.nextInt(3));
				return armor;
			}
			case 2 -> {
				return new ItemStack(Items.GOLDEN_APPLE, 2 + random.nextInt(4));
			}
			default -> {
				return new ItemStack(Items.DIAMOND, 3 + random.nextInt(5));
			}
		}
	}
}
