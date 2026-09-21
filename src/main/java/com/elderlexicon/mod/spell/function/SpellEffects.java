package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.vita.VitaElement;
import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SculkChargeParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

final class SpellEffects {
   private static final ParticleOptions SMALL_GUST_PARTICLE = findSmallGustParticle();
   private static final Map<VitaElement, ParticleOptions> PARTICLES = createParticleMap();
   private static final Map<String, ParticleOptions> RUNE_PARTICLES = createRuneParticleMap();
   private static final int IGNI_FURNACE_BURN_TICKS = 200;
   private static final int FUSUS_FURNACE_BURN_TICKS = 400;
   private static final int FULMEN_FURNACE_BURN_TICKS = 100;
   private static final Field FURNACE_LIT_TIME = findFurnaceField("litTime");
   private static final Field FURNACE_LIT_DURATION = findFurnaceField("litDuration");

   private SpellEffects() {
   }

   static void schedule(ServerLevel level, int delayTicks, Runnable action) {
      if (level != null && action != null) {
         MinecraftServer server = level.getServer();
         if (server != null && !server.isStopped()) {
            if (delayTicks <= 0) {
               server.execute(action);
            } else {
               server.tell(new TickTask(server.getTickCount() + delayTicks, action));
            }

         }
      }
   }

   static boolean isPlayerValid(ServerPlayer player) {
      return player != null && !player.isRemoved() && player.isAlive();
   }

   static void spawnProjectile(ServerPlayer player, VitaElement element, String elementRuneId) {
      Optional<ParticleOptions> particle = resolveParticle(element, elementRuneId);
      if (!particle.isEmpty()) {
         ServerLevel level = player.serverLevel();
         Vec3 eyePosition = player.getEyePosition();
         Vec3 lookVector = player.getLookAngle().normalize();
         double step = 0.5D;
         int segments = 16;
         double offsetScale = 0.08D;

         for(int i = 1; i <= segments; ++i) {
            Vec3 point = eyePosition.add(lookVector.scale((double)i * step));
            level.sendParticles((ParticleOptions)particle.get(), point.x, point.y, point.z, 3, lookVector.x * offsetScale, lookVector.y * offsetScale, lookVector.z * offsetScale, 0.01D);
         }

      }
   }

   static void spawnSummonEffect(ServerPlayer player, VitaElement element, String elementRuneId, SpellEffects.SpellImpact impact) {
      if (impact.blockPos() != null) {
         Optional<ParticleOptions> particle = resolveParticle(element, elementRuneId);
         if (!particle.isEmpty()) {
            ServerLevel level = player.serverLevel();
            Vec3 target = Vec3.atCenterOf(impact.blockPos()).add(0.0D, 0.5D, 0.0D);
            level.sendParticles((ParticleOptions)particle.get(), target.x, target.y, target.z, 12, 0.25D, 0.15D, 0.25D, 0.02D);
         }
      }
   }

   static SpellEffects.SpellImpact findImpact(ServerPlayer player, double range) {
      ServerLevel level = player.serverLevel();
      Vec3 eyePosition = player.getEyePosition();
      Vec3 look = player.getLookAngle();
      if (look.lengthSqr() == 0.0D) {
         return new SpellEffects.SpellImpact(eyePosition, (Entity)null, (BlockPos)null, (Direction)null);
      } else {
         Vec3 end = eyePosition.add(look.scale(range));
         AABB searchBox = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0D);
         Predicate<Entity> predicate = (entity) -> !entity.isSpectator() && entity.isPickable() && entity != player;
         EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, player, eyePosition, end, searchBox, predicate);
         if (entityHit != null) {
            return new SpellEffects.SpellImpact(entityHit.getLocation(), entityHit.getEntity(), (BlockPos)null, (Direction)null);
         } else {
            HitResult hitResult = player.pick(range, 0.0F, false);
            if (hitResult instanceof BlockHitResult) {
               BlockHitResult blockHit = (BlockHitResult)hitResult;
               if (hitResult.getType() == Type.BLOCK) {
                  Vec3 location = blockHit.getLocation();
                  return new SpellEffects.SpellImpact(location, (Entity)null, blockHit.getBlockPos(), blockHit.getDirection());
               }
            }

            return new SpellEffects.SpellImpact(end, (Entity)null, (BlockPos)null, (Direction)null);
         }
      }
   }

   static void applyElementEffect(ServerPlayer player, VitaElement element, String elementRuneId, SpellEffects.SpellImpact impact) {
      if (impact != null) {
         switch (element) {
            case IGNI:
               applyIgniEffect(player, elementRuneId, impact);
               break;
            case FIRMO:
               applyFirmoEffect(player, impact);
               break;
            case AURA:
               applyAuraEffect(player, impact);
               break;
            case AQUA:
               applyAquaEffect(player, impact);
            case BALANCED:
         }

      }
   }

   private static void applyIgniEffect(ServerPlayer player, String elementRuneId, SpellEffects.SpellImpact impact) {
      Entity entity = impact.entity();
      if (entity instanceof LivingEntity living) {
         living.setSecondsOnFire(4);
         living.hurt(player.damageSources().playerAttack(player), 4.0F);
      } else {
         if (impact.blockPos() != null) {
            ServerLevel level = player.serverLevel();
            if (applyIgniBlockEffect(level, impact.blockPos(), furnaceBurnTicks(elementRuneId))) {
               return;
            }

            BlockPos firePos = firePlacementPos(impact);
            if (firePos != null && level.isLoaded(firePos) && level.isEmptyBlock(firePos)) {
               level.setBlock(firePos, Blocks.FIRE.defaultBlockState(), 3);
            }
         }

      }
   }

   private static void applyFirmoEffect(ServerPlayer player, SpellEffects.SpellImpact impact) {
      ServerLevel level = player.serverLevel();
      Entity entity = impact.entity();
      Vec3 look = player.getLookAngle().normalize();
      if (entity != null) {
         entity.push(look.x * 0.6D, 0.3D, look.z * 0.6D);
         entity.hurt(player.damageSources().playerAttack(player), 4.0F);
         placeDirtBlock(level, entity.blockPosition());
      } else {
         BlockPos targetPos = impact.blockPos();
         if (targetPos != null) {
            BlockPos placePos = impact.face() != null ? targetPos.relative(impact.face()) : targetPos;
            placeDirtBlock(level, placePos);
         }

      }
   }

   private static void applyAuraEffect(ServerPlayer player, SpellEffects.SpellImpact impact) {
      Entity entity = impact.entity();
      if (entity != null) {
         Vec3 look = player.getLookAngle().normalize();
         entity.hurt(player.damageSources().playerAttack(player), 3.0F);
         entity.push(look.x * 0.5D, 0.2D, look.z * 0.5D);
      }
   }

   private static void applyAquaEffect(ServerPlayer player, SpellEffects.SpellImpact impact) {
      Entity entity = impact.entity();
      if (entity instanceof LivingEntity living) {
         living.hurt(player.damageSources().drown(), 4.0F);
         living.setAirSupply(Math.min(living.getAirSupply(), 20));
      } else {
         if (impact.blockPos() != null) {
            applyAquaBlockEffect(player.serverLevel(), impact.blockPos());
         }

      }
   }

   private static boolean applyIgniBlockEffect(ServerLevel level, BlockPos pos, int furnaceBurnTicks) {
      if (level != null && pos != null && level.isLoaded(pos)) {
         BlockState state = level.getBlockState(pos);
         Block block = state.getBlock();
         if (block instanceof CampfireBlock && state.hasProperty(CampfireBlock.LIT) && !state.getValue(CampfireBlock.LIT)) {
            level.setBlock(pos, (BlockState)state.setValue(CampfireBlock.LIT, true), 3);
            return true;
         } else if (block instanceof AbstractCandleBlock && state.hasProperty(AbstractCandleBlock.LIT) && !state.getValue(AbstractCandleBlock.LIT)) {
            level.setBlock(pos, (BlockState)state.setValue(AbstractCandleBlock.LIT, true), 3);
            return true;
         } else {
            return block instanceof AbstractFurnaceBlock && igniteFurnace(level, pos, state, furnaceBurnTicks);
         }
      } else {
         return false;
      }
   }

   private static boolean igniteFurnace(ServerLevel level, BlockPos pos, BlockState state, int burnTicks) {
      if (burnTicks <= 0) {
         return false;
      } else {
         BlockEntity blockEntity = level.getBlockEntity(pos);
         if (blockEntity instanceof AbstractFurnaceBlockEntity) {
            AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity)blockEntity;
            int applied = Math.max(burnTicks, currentBurnTime(furnace));
            if (!setFurnaceBurnFields(furnace, applied)) {
               CompoundTag tag = furnace.saveWithFullMetadata();
               tag.putShort("BurnTime", (short)applied);
               furnace.load(tag);
            }

            furnace.setChanged();
            if (state.hasProperty(AbstractFurnaceBlock.LIT) && !state.getValue(AbstractFurnaceBlock.LIT)) {
               level.setBlock(pos, (BlockState)state.setValue(AbstractFurnaceBlock.LIT, true), 3);
            } else {
               level.sendBlockUpdated(pos, state, state, 3);
            }

            return true;
         } else {
            return false;
         }
      }
   }

   private static int currentBurnTime(AbstractFurnaceBlockEntity furnace) {
      if (FURNACE_LIT_TIME == null) {
         return furnace.saveWithFullMetadata().getShort("BurnTime");
      } else {
         try {
            return Math.max(0, FURNACE_LIT_TIME.getInt(furnace));
         } catch (IllegalAccessException var2) {
            return furnace.saveWithFullMetadata().getShort("BurnTime");
         }
      }
   }

   private static boolean setFurnaceBurnFields(AbstractFurnaceBlockEntity furnace, int burnTicks) {
      if (FURNACE_LIT_TIME == null) {
         return false;
      } else {
         try {
            FURNACE_LIT_TIME.setInt(furnace, burnTicks);
            if (FURNACE_LIT_DURATION != null) {
               FURNACE_LIT_DURATION.setInt(furnace, burnTicks);
            }

            return true;
         } catch (IllegalAccessException var3) {
            return false;
         }
      }
   }

   private static int furnaceBurnTicks(String elementRuneId) {
      if (elementRuneId != null && !elementRuneId.isBlank()) {
         short var10000;
         switch (elementRuneId.toLowerCase(Locale.ROOT)) {
            case "fusus":
               var10000 = 400;
               break;
            case "fulmen":
               var10000 = 100;
               break;
            default:
               var10000 = 200;
         }

         return var10000;
      } else {
         return 200;
      }
   }

   private static Field findFurnaceField(String name) {
      try {
         Field field = AbstractFurnaceBlockEntity.class.getDeclaredField(name);
         field.setAccessible(true);
         return field;
      } catch (ReflectiveOperationException var2) {
         return null;
      }
   }

   private static boolean applyAquaBlockEffect(ServerLevel level, BlockPos pos) {
      if (level != null && pos != null && level.isLoaded(pos)) {
         BlockState state = level.getBlockState(pos);
         Block block = state.getBlock();
         if (!state.is(Blocks.FIRE) && !state.is(Blocks.SOUL_FIRE)) {
            if (block instanceof CampfireBlock && state.hasProperty(CampfireBlock.LIT) && state.getValue(CampfireBlock.LIT)) {
               level.setBlock(pos, (BlockState)state.setValue(CampfireBlock.LIT, false), 3);
               return true;
            } else if (state.is(Blocks.CAULDRON)) {
               level.setBlock(pos, (BlockState)Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3), 3);
               return true;
            } else {
               if (state.is(Blocks.WATER_CAULDRON) && state.hasProperty(LayeredCauldronBlock.LEVEL)) {
                  int levelValue = state.getValue(LayeredCauldronBlock.LEVEL);
                  if (levelValue < 3) {
                     level.setBlock(pos, (BlockState)state.setValue(LayeredCauldronBlock.LEVEL, levelValue + 1), 3);
                     return true;
                  }
               }

               if (state.is(Blocks.FARMLAND) && state.hasProperty(FarmBlock.MOISTURE)) {
                  int moisture = state.getValue(FarmBlock.MOISTURE);
                  if (moisture < 7) {
                     level.setBlock(pos, (BlockState)state.setValue(FarmBlock.MOISTURE, 7), 3);
                     return true;
                  }
               }

               return false;
            }
         } else {
            level.removeBlock(pos, false);
            return true;
         }
      } else {
         return false;
      }
   }

   private static BlockPos firePlacementPos(SpellEffects.SpellImpact impact) {
      BlockPos target = impact.blockPos();
      if (target == null) {
         return null;
      } else {
         Direction face = impact.face();
         return face != null && face != Direction.DOWN ? target.relative(face) : target.above();
      }
   }

   private static void placeDirtBlock(ServerLevel level, BlockPos pos) {
      if (pos != null) {
         if (level.isLoaded(pos) && level.isEmptyBlock(pos)) {
            level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
         }
      }
   }

   static Optional<ParticleOptions> resolveParticle(VitaElement element, String elementRuneId) {
      if (elementRuneId != null && !elementRuneId.isBlank()) {
         ParticleOptions specific = (ParticleOptions)RUNE_PARTICLES.get(elementRuneId.toLowerCase(Locale.ROOT));
         if (specific != null) {
            return Optional.of(specific);
         }
      }

      return Optional.ofNullable((ParticleOptions)PARTICLES.get(element));
   }

   private static Map<VitaElement, ParticleOptions> createParticleMap() {
      EnumMap<VitaElement, ParticleOptions> map = new EnumMap(VitaElement.class);
      map.put(VitaElement.IGNI, ParticleTypes.FLAME);
      map.put(VitaElement.AQUA, ParticleTypes.FISHING);
      map.put(VitaElement.FIRMO, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()));
      map.put(VitaElement.AURA, SMALL_GUST_PARTICLE);
      map.put(VitaElement.BALANCED, new SculkChargeParticleOptions(1.0F));
      return map;
   }

   private static Map<String, ParticleOptions> createRuneParticleMap() {
      Map<String, ParticleOptions> map = new HashMap();
      map.put("fusus", ParticleTypes.LAVA);
      map.put("caligo", ParticleTypes.CAMPFIRE_COSY_SMOKE);
      map.put("lutum", new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState()));
      map.put("pulvis", ParticleTypes.ASH);
      map.put("nebula", ParticleTypes.SNOWFLAKE);
      map.put("fulmen", ParticleTypes.ELECTRIC_SPARK);
      return map;
   }

   private static ParticleOptions findSmallGustParticle() {
      try {
         Object value = ParticleTypes.class.getField("SMALL_GUST").get((Object)null);
         if (value instanceof ParticleOptions) {
            return (ParticleOptions)value;
         }
      } catch (ReflectiveOperationException var2) {
      }

      return ParticleTypes.CLOUD;
   }

   static void spawnDrainParticles(ServerPlayer player, VitaElement element, String elementRuneId, BlockPos source, double amount) {
      if (player != null && element != null && source != null) {
         Optional<ParticleOptions> particle = resolveParticle(element, elementRuneId);
         if (!particle.isEmpty()) {
            ServerLevel level = player.serverLevel();
            Vec3 start = Vec3.atCenterOf(source);
            Vec3 end = player.getEyePosition();
            Vec3 delta = end.subtract(start);
            int steps = Math.max(4, (int)Math.ceil(amount * 12.0D));

            for(int i = 0; i < steps; ++i) {
               double t = (double)i / (double)steps;
               Vec3 point = start.add(delta.scale(t));
               level.sendParticles((ParticleOptions)particle.get(), point.x, point.y, point.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
            }

         }
      }
   }

   static void spawnImpediuntPulse(ServerPlayer player, VitaElement element, String elementRuneId) {
      if (player != null) {
         ServerLevel level = player.serverLevel();
         Vec3 origin = player.position().add(0.0D, 0.9D, 0.0D);
         ParticleOptions accent = (ParticleOptions)resolveParticle(element, elementRuneId).orElse(ParticleTypes.CLOUD);
         int segments = 16;
         double radius = 0.8D;

         for(int i = 0; i < segments; ++i) {
            double angle = 6.283185307179586D * (double)i / (double)segments;
            double dx = Math.cos(angle) * radius;
            double dz = Math.sin(angle) * radius;
            level.sendParticles(ParticleTypes.CLOUD, origin.x + dx, origin.y, origin.z + dz, 1, 0.02D, 0.02D, 0.02D, 0.0D);
            level.sendParticles(accent, origin.x + dx * 1.1D, origin.y + 0.05D, origin.z + dz * 1.1D, 1, 0.01D, 0.01D, 0.01D, 0.0D);
         }

      }
   }

   static void playImpediuntSound(ServerPlayer player) {
      if (player != null) {
         ServerLevel level = player.serverLevel();
         float pitch = 0.85F + player.getRandom().nextFloat() * 0.3F;
         level.playSound((Player)null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.9F, pitch);
      }
   }

   static void spawnSourceDrift(ServerPlayer player, VitaElement element, String elementRuneId, Vec3 location) {
      if (player != null && element != null && !element.isBalanced() && location != null) {
         Optional<ParticleOptions> particle = resolveParticle(element, elementRuneId);
         if (!particle.isEmpty()) {
            ServerLevel level = player.serverLevel();
            Vec3 delta = location.subtract(player.position()).normalize();
            level.sendParticles((ParticleOptions)particle.get(), location.x, location.y, location.z, 4, delta.x * 0.08D, 0.05D, delta.z * 0.08D, 0.01D);
         }
      }
   }

   static final class SpellImpact {
      private final Vec3 location;
      private final Entity entity;
      private final BlockPos blockPos;
      private final Direction face;

      SpellImpact(Vec3 location, Entity entity, BlockPos blockPos, Direction face) {
         this.location = location;
         this.entity = entity;
         this.blockPos = blockPos;
         this.face = face;
      }

      Vec3 location() {
         return this.location;
      }

      Entity entity() {
         return this.entity;
      }

      BlockPos blockPos() {
         return this.blockPos;
      }

      Direction face() {
         return this.face;
      }
   }
}
