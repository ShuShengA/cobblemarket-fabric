package com.shusheng.cobblemarket.market

import com.cobblemon.mod.common.Cobblemon
import com.shusheng.cobblemarket.CobbleMarket
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtList
import net.minecraft.registry.RegistryWrapper
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.world.PersistentState
import java.util.UUID

class MarketState private constructor() : PersistentState() {

    private val listings = mutableMapOf<UUID, MarketListing>()
    private val pendingBalances = mutableMapOf<UUID, Int>()
    private val pendingReturns = mutableMapOf<UUID, MutableList<MarketListing>>()

    fun addListing(listing: MarketListing) {
        listings[listing.id] = listing
        markDirty()
    }

    fun removeListing(id: UUID): MarketListing? {
        val removed = listings.remove(id)
        if (removed != null) markDirty()
        return removed
    }

    fun getListing(id: UUID): MarketListing? = listings[id]
    fun getAllListings(): List<MarketListing> = listings.values.toList()
    fun getActiveListings(): List<MarketListing> = listings.values.filter { it.isActive() }

    fun getListingsBySeller(sellerUuid: UUID): List<MarketListing> =
        listings.values.filter { it.sellerUuid == sellerUuid }

    fun search(
        species: String? = null,
        shiny: Boolean? = null,
        minLevel: Int? = null,
        maxLevel: Int? = null,
        minPrice: Int? = null,
        maxPrice: Int? = null,
        sortBy: SortMode = SortMode.PRICE_ASC,
        gender: String? = null,
        typeFilter: String? = null,
        minIvs: Map<String, Int> = emptyMap()
    ): List<MarketListing> {
        var results = getActiveListings()
        species?.let { s -> results = results.filter {
            it.species.contains(s, ignoreCase = true) || (it.extraData["speciesName"]?.contains(s, ignoreCase = true) == true)
        } }
        shiny?.let { s -> results = results.filter { it.shiny == s } }
        minLevel?.let { l -> results = results.filter { it.level >= l } }
        maxLevel?.let { l -> results = results.filter { it.level <= l } }
        minPrice?.let { p -> results = results.filter { it.price >= p } }
        maxPrice?.let { p -> results = results.filter { it.price <= p } }
        gender?.let { g -> results = results.filter { it.extraData["gender"]?.equals(g, ignoreCase = true) == true } }
        typeFilter?.let { t -> results = results.filter { it.extraData["primaryType"]?.contains(t, ignoreCase = true) == true } }
        minIvs.forEach { (statName, value) ->
            results = results.filter { (it.extraData[statName]?.toIntOrNull() ?: 0) == value }
        }
        return when (sortBy) {
            SortMode.PRICE_ASC -> results.sortedBy { it.price }
            SortMode.PRICE_DESC -> results.sortedByDescending { it.price }
            SortMode.LEVEL_ASC -> results.sortedBy { it.level }
            SortMode.LEVEL_DESC -> results.sortedByDescending { it.level }
            SortMode.NEWEST -> results.sortedByDescending { it.createdAt }
            SortMode.OLDEST -> results.sortedBy { it.createdAt }
        }
    }

    fun countActiveBySeller(sellerUuid: UUID): Int =
        listings.values.count { it.sellerUuid == sellerUuid && it.isActive() }

    fun expireOldListings(server: MinecraftServer, currentTime: Long) {
        val expired = listings.values.filter { it.isActive() && it.expiresAt <= currentTime }
        expired.forEach { listing ->
            listing.status = ListingStatus.EXPIRED
            val seller = server.playerManager.getPlayer(listing.sellerUuid)
            if (seller != null) {
                returnPokemon(seller, listing)
            } else {
                pendingReturns.getOrPut(listing.sellerUuid) { mutableListOf() }.add(listing)
            }
        }
        if (expired.isNotEmpty()) markDirty()
    }

    fun returnExpiredToPlayer(player: ServerPlayerEntity): Int {
        val playerReturns = pendingReturns.remove(player.uuid) ?: return 0
        var returned = 0
        playerReturns.forEach { listing ->
            if (returnPokemon(player, listing)) returned++
        }
        if (returned > 0) markDirty()
        return returned
    }

    private fun returnPokemon(player: ServerPlayerEntity, listing: MarketListing): Boolean {
        val party = Cobblemon.storage.getParty(player)
        val pokemon = com.cobblemon.mod.common.pokemon.Pokemon()
            .loadFromNBT(player.serverWorld.registryManager, listing.pokemonNbt)
        return party.add(pokemon)
    }

    fun markModified() = markDirty()

    fun addPendingBalance(playerUuid: UUID, amount: Int) {
        pendingBalances[playerUuid] = (pendingBalances[playerUuid] ?: 0) + amount
        markDirty()
    }

    fun claimPendingBalance(playerUuid: UUID): Int {
        val amount = pendingBalances.remove(playerUuid) ?: 0
        if (amount > 0) markDirty()
        return amount
    }

    override fun writeNbt(nbt: NbtCompound, registryLookup: RegistryWrapper.WrapperLookup): NbtCompound {
        val list = NbtList()
        listings.values.forEach { listing -> list.add(listing.toNbt()) }
        nbt.put("listings", list)

        val balances = NbtCompound()
        pendingBalances.forEach { (uuid, amount) -> balances.putInt(uuid.toString(), amount) }
        nbt.put("pendingBalances", balances)

        val returns = NbtCompound()
        pendingReturns.forEach { (uuid, returned) ->
            val rlist = NbtList()
            returned.forEach { rlist.add(it.toNbt()) }
            returns.put(uuid.toString(), rlist)
        }
        nbt.put("pendingReturns", returns)
        return nbt
    }

    companion object {
        private val TYPE = PersistentState.Type(
            { MarketState() },
            { nbt, _ ->
                MarketState().apply {
                    nbt.getList("listings", NbtList.COMPOUND_TYPE.toInt()).forEach { element ->
                        val listing = MarketListing.fromNbt(element as NbtCompound)
                        listings[listing.id] = listing
                    }
                    val balances = nbt.getCompound("pendingBalances")
                    balances.keys.forEach { key ->
                        pendingBalances[UUID.fromString(key)] = balances.getInt(key)
                    }
                    val returns = nbt.getCompound("pendingReturns")
                    returns.keys.forEach { key ->
                        val rlist = returns.getList(key, NbtList.COMPOUND_TYPE.toInt())
                        val mlist = mutableListOf<MarketListing>()
                        rlist.forEach { element ->
                            mlist.add(MarketListing.fromNbt(element as NbtCompound))
                        }
                        pendingReturns[UUID.fromString(key)] = mlist
                    }
                }
            },
            null
        )

        fun get(server: MinecraftServer): MarketState {
            return server.overworld.persistentStateManager.getOrCreate(TYPE, CobbleMarket.MOD_ID)
        }
    }
}
