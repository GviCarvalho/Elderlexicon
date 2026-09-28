package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.magic.lexicon.Traits;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How a source looks in the world, from what the lexicon says of it ({@link Traits}): the blocks and particles it names
 * by id, found in the game's registries. The spell code asks here instead of asking a source for its name, so a source
 * an addon brings is laid, shown and imagined by what it declares.
 */
final class SourceLooks {

    private static final String BLOCK_PARTICLE = "block:";
    private static final Map<String, Optional<BlockState>> BLOCKS = new ConcurrentHashMap<>();
    private static final Map<String, Optional<ParticleOptions>> PARTICLES = new ConcurrentHashMap<>();

    private SourceLooks() {
    }

    /** What the lexicon says of a source, by rune id; a word that is no source declares nothing. */
    static Traits traits(String runeId) {
        return Lexicons.get().traitsOf(runeId == null ? "" : runeId.toLowerCase(Locale.ROOT));
    }

    /** The block an id names ({@code minecraft:mud}), or empty when there is none. */
    static Optional<BlockState> block(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return BLOCKS.computeIfAbsent(id, key -> {
            ResourceLocation location = ResourceLocation.tryParse(key);
            Block block = location == null ? null : ForgeRegistries.BLOCKS.getValue(location);
            return block == null || block == Blocks.AIR ? Optional.<BlockState>empty()
                    : Optional.of(block.defaultBlockState());
        });
    }

    /**
     * The particle an id names ({@code minecraft:lava}, or {@code block:minecraft:mud} for the particles of a block), or
     * empty when there is none.
     */
    static Optional<ParticleOptions> particle(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return PARTICLES.computeIfAbsent(id, key -> {
            if (key.startsWith(BLOCK_PARTICLE)) {
                return block(key.substring(BLOCK_PARTICLE.length()))
                        .<ParticleOptions>map(state -> new BlockParticleOption(ParticleTypes.BLOCK, state));
            }
            ResourceLocation location = ResourceLocation.tryParse(key);
            ParticleType<?> type = location == null ? null : ForgeRegistries.PARTICLE_TYPES.getValue(location);
            return type instanceof SimpleParticleType simple ? Optional.<ParticleOptions>of(simple)
                    : Optional.<ParticleOptions>empty();
        });
    }
}
