package com.example.nightmarerealms;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.entity.mob.SkeletonEntity;
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
	private static int cooldownTimer = 0;
	private static BlockPos arenaCenter;

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

			// إدارة فترة الراحة (الـ 10 ثواني بين المراحل)
			if (cooldownTimer > 0) {
				cooldownTimer--;
				if (cooldownTimer % 20 == 0) {
					int secondsLeft = cooldownTimer / 20;
					bossBar.setName(Text.literal("استعد للمرحلة القادمة خلال: " + secondsLeft + " ثوانٍ").formatted(Formatting.YELLOW, Formatting.BOLD));
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
		source.sendFeedback(() -> Text.literal("حقبة الكوابيس قد بدأت! حلبة المغلقة استُدعيت!").formatted(Formatting.DARK_RED, Formatting.BOLD), true);
		return 1;
	}

	public static void startBossFight(ServerWorld world, BlockPos pos, ServerPlayerEntity player) {
		currentPhase = 1;
		arenaCenter = pos;
		bossBar.addPlayer(player);
		bossBar.setName(Text.literal("Shadow Sovereign - Phase I").formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
		bossBar.setColor(BossBar.Color.PURPLE);
		bossBar.setVisible(true);

		// حلبة مغلقة 15x15 مع سقف لحماية الفانتوم ومنع الهرب
		buildEnclosedArena(world, pos, Blocks.CRYING_OBSIDIAN, Blocks.TINTED_GLASS);

		phase1Boss = new EndermanEntity(EntityType.ENDERMAN, world);
		phase1Boss.refreshPositionAndAngles(pos.up(1), 0, 0);

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

		// إعطاء تأثير الطيران فقط لو الإندرمان يهاجم اللاعب المباشر ويكون قريب منه جداً
		if (phase1Boss.getTarget() instanceof ServerPlayerEntity player) {
			if (phase1Boss.squaredDistanceTo(player) < 9.0) { // مسافة ضرب قريبة جداً
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 30, 0));
			}
		}
	}

	private static void startCooldown(int completedPhase) {
		currentPhase = completedPhase;
		cooldownTimer = 200; // 10 ثواني (200 ticks)
		bossBar.setPercent(1.0f);
	}

	private static void startPhase2Actual() {
		currentPhase = 2;
		ServerWorld world = (ServerWorld) bossBar.getPlayers().iterator().next().getEntityWorld();

		bossBar.setName(Text.literal("Dread Knight - Phase II").formatted(Formatting.DARK_RED, Formatting.BOLD));
		bossBar.setColor(BossBar.Color.RED);

		buildEnclosedArena(world, arenaCenter, Blocks.NETHER_BRICKS, Blocks.RED_STAINED_GLASS);

		phase2Boss = new WitherSkeletonEntity(EntityType.WITHER_SKELETON, world);
		phase2Boss.refreshPositionAndAngles(arenaCenter.up(1), 0, 0);

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

		// 1. إطلاق فاير بول كل 4 ثواني
		if (tickCounter % 80 == 0 && phase2Boss.getTarget() != null) {
			ServerWorld world = (ServerWorld) phase2Boss.getEntityWorld();
			Vec3d lookVec = phase2Boss.getRotationVec(1.0F);
			FireballEntity fireball = new FireballEntity(world, phase2Boss, lookVec, 1);
			fireball.setPosition(phase2Boss.getX() + lookVec.x, phase2Boss.getBodyY(0.5), phase2Boss.getZ() + lookVec.z);
			world.spawnEntity(fireball);
		}

		// 2. استدعاء جنود (Minions) يساعدوه كل 10 ثواني
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

		bossBar.setName(Text.literal("Apex Phantom - Final Phase").formatted(Formatting.BLUE, Formatting.BOLD));
		bossBar.setColor(BossBar.Color.BLUE);

		phase3Boss = new PhantomEntity(EntityType.PHANTOM, world);
		phase3Boss.refreshPositionAndAngles(arenaCenter.up(4), 0, 0); // ارتفاع قريب تحت السقف

		phase3Boss.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(500.0);
		phase3Boss.setHealth(500.0f);
		phase3Boss.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).setBaseValue(20.0);
		phase3Boss.setCustomName(Text.literal("Apex Phantom").formatted(Formatting.BLUE, Formatting.BOLD));
		phase3Boss.setCustomNameVisible(true);

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

		// ضربات انفجارية سريعة
		if (tickCounter % 100 == 0 && phase3Boss.getTarget() instanceof ServerPlayerEntity player) {
			ServerWorld world = (ServerWorld) phase3Boss.getEntityWorld();
			world.createExplosion(phase3Boss, player.getX(), player.getY(), player.getZ(), 1.5f, ServerWorld.ExplosionSourceType.NONE);
		}
	}

	// بناء حلبة مغلقة 15x15 بارتفاع 8 بلوكات مع سقف مظلل
	private static void buildEnclosedArena(ServerWorld world, BlockPos center, net.minecraft.block.Block floorBlock, net.minecraft.block.Block wallBlock) {
		int radius = 7; // 15x15
		int height = 7;

		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				// الأرضية
				world.setBlockState(center.add(x, -1, z), floorBlock.getDefaultState());
				// السقف (محمي ومظلل)
				world.setBlockState(center.add(x, height, z), wallBlock.getDefaultState());

				// تفريغ الداخل
				for (int y = 0; y < height; y++) {
					if (Math.abs(x) == radius || Math.abs(z) == radius) {
						// الجدران
						world.setBlockState(center.add(x, y, z), wallBlock.getDefaultState());
					} else {
						// هواء بالداخل
						world.setBlockState(center.add(x, y, z), Blocks.AIR.getDefaultState());
					}
				}
			}
		}
	}

	// لوت المرحلة الأولى المساعد (تضبيط أكل وهيلث)
	private static void dropPhase1Loot(ServerWorld world, BlockPos pos) {
		world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.GOLDEN_APPLE, 6)));
		world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.ENDER_PEARL, 8)));
		world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.COOKED_BEEF, 16)));
	}

	// لوت المرحلة الثانية المساعد (استعداد للفانتوم النهائي)
	private static void dropPhase2Loot(ServerWorld world, BlockPos pos) {
		world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 2)));
		world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.BOW, 1)));
		world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.ARROW, 64)));
		world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.POTION, 2))); // Healing
	}

	// اللوت النهائي الأسطوري القوي جداً
	private static void dropFinalLoot(ServerWorld world, BlockPos pos) {
		ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
		sword.set(DataComponentTypes.CUSTOM_NAME, Text.literal("قاهر الكوابيس الأسطوري").formatted(Formatting.GOLD, Formatting.BOLD));

		ItemStack chest = new ItemStack(Items.NETHERITE_CHESTPLATE);
		ItemStack helmet = new ItemStack(Items.NETHERITE_HELMET);

		world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), sword));
		world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), chest));
		world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), helmet));
		world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.ELYTRA, 1)));
		world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.TOTEM_OF_UNDYING, 4)));
		world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.NETHERITE_INGOT, 8)));
		world.spawnEntity(new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.DIAMOND_BLOCK, 4)));
	}
}
