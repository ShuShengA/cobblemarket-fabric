package com.shusheng.cobblemarket.market

import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.pokemon.IVs
import com.shusheng.cobblemarket.CobbleMarket
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtList
import net.minecraft.registry.RegistryWrapper
import net.minecraft.server.MinecraftServer
import net.minecraft.world.PersistentState
import java.util.UUID

data class PokemonBlacklistEntry(
    val id: UUID,
    val speciesId: String,
    val ivHp: Int,
    val ivAtk: Int,
    val ivDef: Int,
    val ivSpAtk: Int,
    val ivSpDef: Int,
    val ivSpd: Int
) {
    fun matches(targetSpeciesId: String, ivs: IVs): Boolean {
        if (speciesId != targetSpeciesId) return false
        if (ivHp >= 0 && ivs[Stats.HP] != ivHp) return false
        if (ivAtk >= 0 && ivs[Stats.ATTACK] != ivAtk) return false
        if (ivDef >= 0 && ivs[Stats.DEFENCE] != ivDef) return false
        if (ivSpAtk >= 0 && ivs[Stats.SPECIAL_ATTACK] != ivSpAtk) return false
        if (ivSpDef >= 0 && ivs[Stats.SPECIAL_DEFENCE] != ivSpDef) return false
        if (ivSpd >= 0 && ivs[Stats.SPEED] != ivSpd) return false
        return true
    }
}

class PokemonBlacklistState private constructor() : PersistentState() {

    private val entries = mutableMapOf<UUID, PokemonBlacklistEntry>()

    fun add(entry: PokemonBlacklistEntry) {
        entries[entry.id] = entry
        markDirty()
    }

    fun remove(id: UUID): Boolean {
        val removed = entries.remove(id) != null
        if (removed) markDirty()
        return removed
    }

    fun getAll(): List<PokemonBlacklistEntry> = entries.values.toList()

    fun isBlacklisted(speciesId: String, ivs: IVs): Boolean =
        entries.values.any { it.matches(speciesId, ivs) }

    override fun writeNbt(nbt: NbtCompound, registryLookup: RegistryWrapper.WrapperLookup): NbtCompound {
        val list = NbtList()
        entries.values.forEach { e ->
            val c = NbtCompound()
            c.putUuid("id", e.id)
            c.putString("speciesId", e.speciesId)
            c.putInt("ivHp", e.ivHp)
            c.putInt("ivAtk", e.ivAtk)
            c.putInt("ivDef", e.ivDef)
            c.putInt("ivSpAtk", e.ivSpAtk)
            c.putInt("ivSpDef", e.ivSpDef)
            c.putInt("ivSpd", e.ivSpd)
            list.add(c)
        }
        nbt.put("entries", list)
        return nbt
    }

    companion object {
        private val TYPE = PersistentState.Type(
            { PokemonBlacklistState() },
            { nbt, _ ->
                PokemonBlacklistState().apply {
                    nbt.getList("entries", NbtList.COMPOUND_TYPE.toInt()).forEach { element ->
                        try {
                            val c = element as NbtCompound
                            val entry = PokemonBlacklistEntry(
                                id = c.getUuid("id"),
                                speciesId = c.getString("speciesId"),
                                ivHp = c.getInt("ivHp"),
                                ivAtk = c.getInt("ivAtk"),
                                ivDef = c.getInt("ivDef"),
                                ivSpAtk = c.getInt("ivSpAtk"),
                                ivSpDef = c.getInt("ivSpDef"),
                                ivSpd = c.getInt("ivSpd")
                            )
                            entries[entry.id] = entry
                        } catch (e: Exception) {
                            CobbleMarket.LOGGER.warn("Skipping corrupted pokemon blacklist entry: {}", e.message)
                        }
                    }
                }
            },
            null
        )

        fun get(server: MinecraftServer): PokemonBlacklistState =
            server.overworld.persistentStateManager.getOrCreate(TYPE, "${CobbleMarket.MOD_ID}_pokemon_blacklist")
    }
}
