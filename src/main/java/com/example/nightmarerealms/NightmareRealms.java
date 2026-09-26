package com.example.nightmarerealms;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class NightmareRealms implements ModInitializer {

	public static final String MOD_ID = "nightmarerealms";

	private static ServerBossBar bossBar;
	private static int currentPhase = 0;
	private static int tickCounter = 0;

	private static EndermanEntity phase1Boss;
	private static WitherSkeletonEntity phase2Boss;
	private static PhantomEntity phase3Boss;

	@Override
	public void onInitialize() {
		bossBar = new ServerBossBar(
				Text.literal("Nightmare Sovereign - Phase I").formatted(Formatting.DARK_PURPLE, Formatting.BOLD),
				BossBar.Color.PURPLE,
				BossBar.Style.NOTCHED_10
		);

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tickCounter++;
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

		if (currentPhase != 0) {
			source.sendError(Text.translatable("nightmarerealms.command.already_active"));
			return 0;
		}

		startBossFight(source.getWorld(), player.getBlockPos(), player);
		source.sendFeedback(() -> Text.literal("حقبة الكوابيس قد بدأت! استعد للقتال!").formatted(Formatting.DARK_RED, Formatting.BOLD), true);
		return 1;
	}

	public static void startBossFight(ServerWorld world, BlockPos pos, ServerPlayerEntity player) {
		currentPhase = 1;
		bossBar.addPlayer(player);
		bossBar.setName(Text.literal("Shadow Sovereign - Phase I").formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
		bossBar.setColor(BossBar.Color.PURPLE);
		bossBar.setVisible(true);

		buildArena(world, pos, Blocks.CRYING_OBSIDIAN, Blocks.OBSIDIAN);

		phase1Boss = new EndermanEntity(EntityType.ENDERMAN, world);
		phase1Boss.refreshPositionAndAngles(pos.up(1), 0, 0);

		phase1Boss.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(300.0);
		phase1Boss.setHealth(300.0f);
		phase1Boss.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).setBaseValue(12.0);
		phase1Boss.setCustomName(Text.literal("Shadow Sovereign").formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
		phase1Boss.setCustomNameVisible(true);

		world.spawnEntity(phase1Boss);
	}

	private static void updatePhase1() {
		if (!phase1Boss.isAlive()) {
			startPhase2((ServerWorld) phase1Boss.getEntityWorld(), phase1Boss.getBlockPos());
			return;
		}
		bossBar.setPercent(phase1Boss.getHealth() / phase1Boss.getMaxHealth());

		if (tickCounter % 100 == 0) {
			ServerWorld world = (ServerWorld) phase1Boss.getEntityWorld();
			LightningEntity lightning = new LightningEntity(EntityType.LIGHTNING_BOLT, world);
			lightning.refreshPositionAndAngles(phase1Boss.getBlockPos(), 0, 0);
			world.spawnEntity(lightning);

			if (phase1Boss.getTarget() instanceof ServerPlayerEntity player) {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 60, 1));
			}
		}
	}

	private static void startPhase2(ServerWorld world, BlockPos pos) {
		currentPhase = 2;
		phase1Boss = null;

		bossBar.setName(Text.literal("Dread Knight - Phase II").formatted(Formatting.DARK_RED, Formatting.BOLD));
		bossBar.setColor(BossBar.Color.RED);

		buildArena(world, pos, Blocks.NETHER_BRICKS, Blocks.MAGMA_BLOCK);

		phase2Boss = new WitherSkeletonEntity(EntityType.WITHER_SKELETON, world);
		phase2Boss.refreshPositionAndAngles(pos.up(1), 0, 0);

		phase2Boss.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(450.0);
		phase2Boss.setHealth(450.0f);
		phase2Boss.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).setBaseValue(18.0);

		phase2Boss.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
		phase2Boss.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
		phase2Boss.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.WITHER_SKELETON_SKULL));
		phase2Boss.setCustomName(Text.literal("Dread Knight").formatted(Formatting.RED, Formatting.BOLD));
		phase2Boss.setCustomNameVisible(true);

		world.spawnEntity(phase2Boss);
	}

	private static void updatePhase2() {
		if (!phase2Boss.isAlive()) {
			startPhase3((ServerWorld) phase2Boss.getEntityWorld(), phase2Boss.getBlockPos());
			return;
		}
		bossBar.setPercent(phase2Boss.getHealth() / phase2Boss.getMaxHealth());

		if (tickCounter % 80 == 0 && phase2Boss.getTarget() != null) {
			ServerWorld world = (ServerWorld) phase2Boss.getEntityWorld();
			Vec3d lookVec = phase2Boss.getRotationVec(1.0F);
			FireballEntity fireball = new FireballEntity(world, phase2Boss, lookVec, 1);
			fireball.setPosition(phase2Boss.getX() + lookVec.x, phase2Boss.getBodyY(0.5), phase2Boss.getZ() + lookVec.z);
			world.spawnEntity(fireball);
		}
	}

	private static void startPhase3(ServerWorld world, BlockPos pos) {
		currentPhase = 3;
		phase2Boss = null;

		bossBar.setName(Text.literal("Apex Phantom - Final Phase").formatted(Formatting.BLUE, Formatting.BOLD));
		bossBar.setColor(BossBar.Color.BLUE);

		phase3Boss = new PhantomEntity(EntityType.PHANTOM, world);
		phase3Boss.refreshPositionAndAngles(pos.up(8), 0, 0);

		phase3Boss.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(600.0);
		phase3Boss.setHealth(600.0f);
		phase3Boss.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).setBaseValue(25.0);
		phase3Boss.setCustomName(Text.literal("Apex Phantom").formatted(Formatting.BLUE, Formatting.BOLD));
		phase3Boss.setCustomNameVisible(true);

		world.spawnEntity(phase3Boss);
	}

	private static void updatePhase3() {
		if (!phase3Boss.isAlive()) {
			dropLegendaryLoot((ServerWorld) phase3Boss.getEntityWorld(), phase3Boss.getBlockPos());

			currentPhase = 0;
			bossBar.setVisible(false);
			bossBar.clearPlayers();
			phase3Boss = null;
			return;
		}
		bossBar.setPercent(phase3Boss.getHealth() / phase3Boss.getMaxHealth());

		if (tickCounter % 120 == 0 && phase3Boss.getTarget() instanceof ServerPlayerEntity player) {
			ServerWorld world = (ServerWorld) phase3Boss.getEntityWorld();
			world.createExplosion(phase3Boss, player.getX(), player.getY(), player.getZ(), 2.0f, ServerWorld.ExplosionSourceType.NONE);
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 80, 0));
		}
	}

	private static void buildArena(ServerWorld world, BlockPos center, net.minecraft.block.Block floorBlock, net.minecraft.block.Block wallBlock) {
		int radius = 6;
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				world.setBlockState(center.add(x, -1, z), floorBlock.getDefaultState());
				for (int y = 0; y <= 4; y++) {
					world.setBlockState(center.add(x, y, z), Blocks.AIR.getDefaultState());
				}
				if (Math.abs(x) == radius || Math.abs(z) == radius) {
					world.setBlockState(center.add(x, 0, z), wallBlock.getDefaultState());
					world.setBlockState(center.add(x, 1, z), wallBlock.getDefaultState());
				}
			}
		}
	}

	private static void dropLegendaryLoot(ServerWorld world, BlockPos pos) {
		ItemStack superSword = new ItemStack(Items.NETHERITE_SWORD);
		superSword.set(DataComponentTypes.CUSTOM_NAME, Text.literal("سيف قاهر الكوابيس").formatted(Formatting.GOLD, Formatting.BOLD));

		ItemStack totems = new ItemStack(Items.TOTEM_OF_UNDYING, 2);
		ItemStack diamonds = new ItemStack(Items.DIAMOND, 16);
		ItemStack netherite = new ItemStack(Items.NETHERITE_INGOT, 4);

		world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), superSword));
		world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), totems));
		world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), diamonds));
		world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), netherite));
	}
}
