package com.shusheng.cobblemarket.market

import com.shusheng.cobblemarket.CobbleMarket
import com.shusheng.cobblemarket.network.canFitInInventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtList
import net.minecraft.registry.RegistryWrapper
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.world.PersistentState
import java.util.UUID

class ItemMarketState private constructor() : PersistentState() {

    private val listings = mutableMapOf<UUID, ItemListing>()
    private val pendingReturns = mutableMapOf<UUID, MutableList<ItemListing>>()

    fun addListing(listing: ItemListing) {
        listings[listing.id] = listing
        markDirty()
    }

    fun removeListing(id: UUID): ItemListing? {
        val removed = listings.remove(id)
        if (removed != null) markDirty()
        return removed
    }

    fun getListing(id: UUID): ItemListing? = listings[id]
    fun getAllListings(): List<ItemListing> = listings.values.toList()
    fun getActiveListings(): List<ItemListing> = listings.values.filter { it.isActive() }

    fun getListingsBySeller(sellerUuid: UUID): List<ItemListing> =
        listings.values.filter { it.sellerUuid == sellerUuid }

    fun countActiveBySeller(sellerUuid: UUID): Int =
        listings.values.count { it.sellerUuid == sellerUuid && it.isActive() }

    fun search(sortBy: SortMode = SortMode.NEWEST, sellerUuid: UUID? = null, sellerName: String? = null): List<ItemListing> {
        var results = getActiveListings()
        sellerUuid?.let { u -> results = results.filter { it.sellerUuid == u } }
        sellerName?.let { s -> results = results.filter { it.sellerName.contains(s, ignoreCase = true) } }
        return when (sortBy) {
            SortMode.PRICE_ASC -> results.sortedBy { it.price }
            SortMode.PRICE_DESC -> results.sortedByDescending { it.price }
            SortMode.LEVEL_ASC -> results.sortedBy { it.count }
            SortMode.LEVEL_DESC -> results.sortedByDescending { it.count }
            SortMode.NEWEST -> results.sortedByDescending { it.createdAt }
            SortMode.OLDEST -> results.sortedBy { it.createdAt }
        }
    }

    fun expireOldListings(currentTime: Long) {
        val expired = listings.values.filter { it.isActive() && it.expiresAt <= currentTime }
        expired.forEach { listing ->
            listing.status = ListingStatus.EXPIRED
            pendingReturns.getOrPut(listing.sellerUuid) { mutableListOf() }.add(listing)
        }
        if (expired.isNotEmpty()) markDirty()
    }

    fun getPendingReturns(playerUuid: UUID): List<ItemListing> =
        pendingReturns[playerUuid] ?: emptyList()

    fun addPendingReturn(playerUuid: UUID, listing: ItemListing) {
        pendingReturns.getOrPut(playerUuid) { mutableListOf() }.add(listing)
        markDirty()
    }

    fun claimReturns(player: ServerPlayerEntity): Int {
        val playerReturns = pendingReturns[player.uuid] ?: return 0
        val remaining = mutableListOf<ItemListing>()
        var returned = 0
        playerReturns.forEach { listing ->
            val stack = ItemStack.fromNbtOrEmpty(player.serverWorld.registryManager, listing.itemNbt)
            stack.count = listing.count
            if (canFitInInventory(player, stack)) {
                player.inventory.insertStack(stack)
                if (stack.isEmpty) {
                    returned++
                    com.shusheng.cobblemarket.event.TransactionHistory.get(player.server).addRecord(
                        com.shusheng.cobblemarket.event.TransactionRecord(
                            timestamp = System.currentTimeMillis(),
                            type = com.shusheng.cobblemarket.event.TransactionType.RETURN,
                            category = com.shusheng.cobblemarket.event.TransactionCategory.ITEM,
                            sellerUuid = player.uuid,
                            sellerName = player.name.string,
                            buyerUuid = null,
                            buyerName = "",
                            species = listing.itemId,
                            price = listing.price,
                            fee = 0
                        )
                    )
                } else {
                    // 部分插入：保留剩余数量，待下次领取，避免丢失。
                    listing.count = stack.count
                    remaining.add(listing)
                }
            } else {
                remaining.add(listing)
            }
        }
        if (remaining.isEmpty()) {
            pendingReturns.remove(player.uuid)
        } else {
            pendingReturns[player.uuid] = remaining
        }
        if (returned > 0) markDirty()
        return returned
    }

    fun markModified() = markDirty()

    override fun writeNbt(nbt: NbtCompound, registryLookup: RegistryWrapper.WrapperLookup): NbtCompound {
        val list = NbtList()
        listings.values.forEach { listing -> list.add(listing.toNbt()) }
        nbt.put("listings", list)

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
            { ItemMarketState() },
            { nbt, _ ->
                ItemMarketState().apply {
                    nbt.getList("listings", NbtList.COMPOUND_TYPE.toInt()).forEach { element ->
                        val listing = ItemListing.fromNbt(element as NbtCompound)
                        listings[listing.id] = listing
                    }
                    val returns = nbt.getCompound("pendingReturns")
                    returns.keys.forEach { key ->
                        val rlist = returns.getList(key, NbtList.COMPOUND_TYPE.toInt())
                        val mlist = mutableListOf<ItemListing>()
                        rlist.forEach { element ->
                            mlist.add(ItemListing.fromNbt(element as NbtCompound))
                        }
                        pendingReturns[UUID.fromString(key)] = mlist
                    }
                }
            },
            null
        )

        fun get(server: MinecraftServer): ItemMarketState =
            server.overworld.persistentStateManager.getOrCreate(TYPE, "${CobbleMarket.MOD_ID}_items")
    }
}
