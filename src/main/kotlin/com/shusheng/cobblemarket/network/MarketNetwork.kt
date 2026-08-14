package com.shusheng.cobblemarket.network

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.storage.party.PartyPosition
import com.shusheng.cobblemarket.config.CurrencyHandler
import com.shusheng.cobblemarket.CobbleMarket
import com.shusheng.cobblemarket.market.BanState
import com.shusheng.cobblemarket.market.ItemListing
import com.shusheng.cobblemarket.market.ItemMarketState
import com.shusheng.cobblemarket.market.ListingStatus
import com.shusheng.cobblemarket.market.MarketListing
import com.shusheng.cobblemarket.market.MarketState
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.network.packet.CustomPayload
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.util.UUID

// ── Data transfer objects ──

data class ListingEntry(
    val id: UUID,
    val sellerUuid: UUID,
    val species: String,
    val speciesId: String,
    val level: Int,
    val shiny: Boolean,
    val price: Int,
    val sellerName: String,
    val primaryType: String,
    val secondaryType: String,
    val ivsHp: Int, val ivsAtk: Int, val ivsDef: Int,
    val ivsSpAtk: Int, val ivsSpDef: Int, val ivsSpd: Int,
    val nature: String,
    val ability: String,
    val gender: String,
    val ball: String,
    val ballItem: String,
    val currencyName: String
) {
    fun write(buf: PacketByteBuf) {
        buf.writeUuid(id)
        buf.writeUuid(sellerUuid)
        buf.writeString(species)
        buf.writeString(speciesId)
        buf.writeInt(level)
        buf.writeBoolean(shiny)
        buf.writeInt(price)
        buf.writeString(sellerName)
        buf.writeString(primaryType)
        buf.writeString(secondaryType)
        buf.writeInt(ivsHp); buf.writeInt(ivsAtk); buf.writeInt(ivsDef)
        buf.writeInt(ivsSpAtk); buf.writeInt(ivsSpDef); buf.writeInt(ivsSpd)
        buf.writeString(nature)
        buf.writeString(ability)
        buf.writeString(gender)
        buf.writeString(ball)
        buf.writeString(ballItem)
        buf.writeString(currencyName)
    }

    companion object {
        fun read(buf: PacketByteBuf): ListingEntry = ListingEntry(
            id = buf.readUuid(),
            sellerUuid = buf.readUuid(),
            species = buf.readString(),
            speciesId = buf.readString(),
            level = buf.readInt(),
            shiny = buf.readBoolean(),
            price = buf.readInt(),
            sellerName = buf.readString(),
            primaryType = buf.readString(),
            secondaryType = buf.readString(),
            ivsHp = buf.readInt(), ivsAtk = buf.readInt(), ivsDef = buf.readInt(),
            ivsSpAtk = buf.readInt(), ivsSpDef = buf.readInt(), ivsSpd = buf.readInt(),
            nature = buf.readString(),
            ability = buf.readString(),
            gender = buf.readString(),
            ball = buf.readString(),
            ballItem = buf.readString(),
            currencyName = buf.readString()
        )
    }
}

// ── C2S: Request market data ──

data class RequestMarketPayload(
    val speciesFilter: String,
    val shinyOnly: Boolean,
    val minLevel: Int,
    val maxLevel: Int,
    val sortMode: String,
    val page: Int,
    val genderFilter: String,
    val typeFilter: String,
    val minIvsHp: Int,
    val minIvsAtk: Int,
    val minIvsDef: Int,
    val minIvsSpAtk: Int,
    val minIvsSpDef: Int,
    val minIvsSpd: Int,
    val pageSize: Int,
    val mineOnly: Boolean
) : CustomPayload {
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID

    companion object {
        val ID = CustomPayload.Id<RequestMarketPayload>(CobbleMarket.id("request_market"))
        val CODEC: PacketCodec<PacketByteBuf, RequestMarketPayload> = PacketCodec.of(
            { p, b ->
                b.writeString(p.speciesFilter); b.writeBoolean(p.shinyOnly); b.writeInt(p.minLevel); b.writeInt(p.maxLevel); b.writeString(
                p.sortMode
            ); b.writeInt(p.page); b.writeString(p.genderFilter); b.writeString(p.typeFilter); b.writeInt(p.minIvsHp); b.writeInt(
                p.minIvsAtk
            ); b.writeInt(p.minIvsDef); b.writeInt(p.minIvsSpAtk); b.writeInt(p.minIvsSpDef); b.writeInt(p.minIvsSpd); b.writeInt(
                p.pageSize
            ); b.writeBoolean(p.mineOnly)
            },
            { b ->
                RequestMarketPayload(
                    b.readString(),
                    b.readBoolean(),
                    b.readInt(),
                    b.readInt(),
                    b.readString(),
                    b.readInt(),
                    b.readString(),
                    b.readString(),
                    b.readInt(),
                    b.readInt(),
                    b.readInt(),
                    b.readInt(),
                    b.readInt(),
                    b.readInt(),
                    b.readInt(),
                    b.readBoolean()
                )
            }
        )
    }
}

// ── C2S: Admin request all Pokemon ──

data class AdminRequestPokemonPayload(
    val speciesFilter: String,
    val sellerFilter: String,
    val shinyOnly: Boolean,
    val sortMode: String,
    val page: Int,
    val minIvsHp: Int,
    val minIvsAtk: Int,
    val minIvsDef: Int,
    val minIvsSpAtk: Int,
    val minIvsSpDef: Int,
    val minIvsSpd: Int,
    val pageSize: Int,
    val mineOnly: Boolean
) : CustomPayload {
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID

    companion object {
        val ID = CustomPayload.Id<AdminRequestPokemonPayload>(CobbleMarket.id("admin_request_pokemon"))
        val CODEC: PacketCodec<PacketByteBuf, AdminRequestPokemonPayload> = PacketCodec.of(
            { p, b ->
                b.writeString(p.speciesFilter); b.writeString(p.sellerFilter); b.writeBoolean(p.shinyOnly); b.writeString(
                p.sortMode
            ); b.writeInt(p.page); b.writeInt(p.minIvsHp); b.writeInt(p.minIvsAtk); b.writeInt(p.minIvsDef); b.writeInt(
                p.minIvsSpAtk
            ); b.writeInt(p.minIvsSpDef); b.writeInt(p.minIvsSpd); b.writeInt(p.pageSize); b.writeBoolean(p.mineOnly)
            },
            { b ->
                AdminRequestPokemonPayload(
                    b.readString(),
                    b.readString(),
                    b.readBoolean(),
                    b.readString(),
                    b.readInt(),
                    b.readInt(),
                    b.readInt(),
                    b.readInt(),
                    b.readInt(),
                    b.readInt(),
                    b.readInt(),
                    b.readInt(),
                    b.readBoolean()
                )
            }
        )
    }
}

// ── C2S: Admin cancel Pokemon ──

data class AdminCancelPokemonPayload(val listingId: UUID) : CustomPayload {
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID

    companion object {
        val ID = CustomPayload.Id<AdminCancelPokemonPayload>(CobbleMarket.id("admin_cancel_pokemon"))
        val CODEC: PacketCodec<PacketByteBuf, AdminCancelPokemonPayload> = PacketCodec.of(
            { p, b -> b.writeUuid(p.listingId) },
            { b -> AdminCancelPokemonPayload(b.readUuid()) }
        )
    }
}

// ── S2C: Market data response ──

data class MarketDataPayload(
    val entries: List<ListingEntry>,
    val totalPages: Int,
    val currentPage: Int,
    val pendingBalance: Int
) : CustomPayload {
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID

    companion object {
        val ID = CustomPayload.Id<MarketDataPayload>(CobbleMarket.id("market_data"))
        val CODEC: PacketCodec<PacketByteBuf, MarketDataPayload> = PacketCodec.of(
            { p, b ->
                b.writeVarInt(p.entries.size)
                p.entries.forEach { it.write(b) }
                b.writeInt(p.totalPages)
                b.writeInt(p.currentPage)
                b.writeInt(p.pendingBalance)
            },
            { b ->
                val size = b.readVarInt()
                val entries = (0 until size).map { ListingEntry.read(b) }
                MarketDataPayload(entries, b.readInt(), b.readInt(), b.readInt())
            }
        )
    }
}

// ── C2S: Buy listing ──

data class BuyFromMarketPayload(val listingId: UUID) : CustomPayload {
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID

    companion object {
        val ID = CustomPayload.Id<BuyFromMarketPayload>(CobbleMarket.id("buy_from_market"))
        val CODEC: PacketCodec<PacketByteBuf, BuyFromMarketPayload> = PacketCodec.of(
            { p, b -> b.writeUuid(p.listingId) },
            { b -> BuyFromMarketPayload(b.readUuid()) }
        )
    }
}

// ── C2S: Cancel listing ──

data class CancelFromMarketPayload(val listingId: UUID) : CustomPayload {
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID

    companion object {
        val ID = CustomPayload.Id<CancelFromMarketPayload>(CobbleMarket.id("cancel_from_market"))
        val CODEC: PacketCodec<PacketByteBuf, CancelFromMarketPayload> = PacketCodec.of(
            { p, b -> b.writeUuid(p.listingId) },
            { b -> CancelFromMarketPayload(b.readUuid()) }
        )
    }
}

// ── S2C: Action result ──

data class MarketResultPayload(
    val success: Boolean,
    val message: Text
) : CustomPayload {
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID

    companion object {
        val ID = CustomPayload.Id<MarketResultPayload>(CobbleMarket.id("market_result"))
        val CODEC: PacketCodec<PacketByteBuf, MarketResultPayload> = PacketCodec.of(
            { p, b -> b.writeBoolean(p.success); net.minecraft.text.TextCodecs.PACKET_CODEC.encode(b, p.message) },
            { b -> MarketResultPayload(b.readBoolean(), net.minecraft.text.TextCodecs.PACKET_CODEC.decode(b)) }
        )
    }
}

// ── S2C: Open market screen ──

data class OpenMarketPayload(val dummy: Int) : CustomPayload {
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID

    companion object {
        val ID = CustomPayload.Id<OpenMarketPayload>(CobbleMarket.id("open_market"))
        val CODEC: PacketCodec<PacketByteBuf, OpenMarketPayload> = PacketCodec.of(
            { _, b -> b.writeInt(0) },
            { b -> b.readInt(); OpenMarketPayload(0) }
        )
    }
}

// ── Pokemon preview for sell selection ──

data class PokemonPreview(
    val uuid: UUID,
    val species: String,
    val speciesId: String,
    val speciesName: String,
    val level: Int,
    val shiny: Boolean,
    val gender: String,
    val nature: String,
    val ability: String,
    val ivsHp: Int, val ivsAtk: Int, val ivsDef: Int, val ivsSpAtk: Int, val ivsSpDef: Int, val ivsSpd: Int,
    val ball: String,
    val primaryType: String,
    val secondaryType: String,
    val source: String, // "party" or "pc"
    val slot: Int
) {
    fun write(buf: PacketByteBuf) {
        buf.writeUuid(uuid); buf.writeString(species); buf.writeString(speciesId); buf.writeString(speciesName)
        buf.writeInt(level); buf.writeBoolean(shiny); buf.writeString(gender)
        buf.writeString(nature); buf.writeString(ability)
        buf.writeInt(ivsHp); buf.writeInt(ivsAtk); buf.writeInt(ivsDef)
        buf.writeInt(ivsSpAtk); buf.writeInt(ivsSpDef); buf.writeInt(ivsSpd)
        buf.writeString(ball); buf.writeString(primaryType); buf.writeString(secondaryType)
        buf.writeString(source); buf.writeInt(slot)
    }

    companion object {
        fun read(buf: PacketByteBuf) = PokemonPreview(
            buf.readUuid(), buf.readString(), buf.readString(), buf.readString(),
            buf.readInt(), buf.readBoolean(), buf.readString(),
            buf.readString(), buf.readString(),
            buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
            buf.readString(), buf.readString(), buf.readString(),
            buf.readString(), buf.readInt()
        )
    }
}

// ── C2S: Request my Pokémon list ──

class RequestMyPokemonPayload : CustomPayload {
    override fun getId() = ID

    companion object {
        val ID = CustomPayload.Id<RequestMyPokemonPayload>(CobbleMarket.id("request_my_pokemon"))
        val CODEC: PacketCodec<PacketByteBuf, RequestMyPokemonPayload> = PacketCodec.of(
            { _, b -> b.writeInt(0) },
            { b -> b.readInt(); RequestMyPokemonPayload() }
        )
    }
}

// ── S2C: My Pokémon list response ──

data class MyPokemonListPayload(val pokemon: List<PokemonPreview>) : CustomPayload {
    override fun getId() = ID

    companion object {
        val ID = CustomPayload.Id<MyPokemonListPayload>(CobbleMarket.id("my_pokemon_list"))
        val CODEC: PacketCodec<PacketByteBuf, MyPokemonListPayload> = PacketCodec.of(
            { p, b -> b.writeVarInt(p.pokemon.size); p.pokemon.forEach { it.write(b) } },
            { b -> MyPokemonListPayload((0 until b.readVarInt()).map { PokemonPreview.read(b) }) }
        )
    }
}

// ── C2S: Collect pending balance ──

class CollectBalancePayload : CustomPayload {
    override fun getId() = ID

    companion object {
        val ID = CustomPayload.Id<CollectBalancePayload>(CobbleMarket.id("collect_balance"))
        val CODEC: PacketCodec<PacketByteBuf, CollectBalancePayload> = PacketCodec.of(
            { _, b -> b.writeInt(0) },
            { b -> b.readInt(); CollectBalancePayload() }
        )
    }
}

// ── History ──

data class HistoryEntry(
    val type: String,
    val category: String,
    val species: String,
    val price: Int,
    val buyerName: String,
    val sellerName: String,
    val timestamp: Long
) {
    fun write(buf: PacketByteBuf) {
        buf.writeString(type); buf.writeString(category); buf.writeString(species); buf.writeInt(price); buf.writeString(
            buyerName
        ); buf.writeString(sellerName); buf.writeLong(timestamp)
    }

    companion object {
        fun read(buf: PacketByteBuf) = HistoryEntry(
            buf.readString(),
            buf.readString(),
            buf.readString(),
            buf.readInt(),
            buf.readString(),
            buf.readString(),
            buf.readLong()
        )
    }
}

data class RequestHistoryPayload(val all: Boolean) : CustomPayload {
    override fun getId() = ID

    companion object {
        val ID = CustomPayload.Id<RequestHistoryPayload>(CobbleMarket.id("request_history"))
        val CODEC: PacketCodec<PacketByteBuf, RequestHistoryPayload> = PacketCodec.of(
            { p, b -> b.writeBoolean(p.all) },
            { b -> RequestHistoryPayload(b.readBoolean()) }
        )
    }
}

data class HistoryDataPayload(val entries: List<HistoryEntry>) : CustomPayload {
    override fun getId() = ID

    companion object {
        val ID = CustomPayload.Id<HistoryDataPayload>(CobbleMarket.id("history_data"))
        val CODEC: PacketCodec<PacketByteBuf, HistoryDataPayload> = PacketCodec.of(
            { p, b -> b.writeVarInt(p.entries.size); p.entries.forEach { it.write(b) } },
            { b -> HistoryDataPayload((0 until b.readVarInt()).map { HistoryEntry.read(b) }) }
        )
    }
}

// ── C2S: Sell from storage ──

data class SellFromStoragePayload(val pokemonUuid: UUID, val price: Int) : CustomPayload {
    override fun getId() = ID

    companion object {
        val ID = CustomPayload.Id<SellFromStoragePayload>(CobbleMarket.id("sell_from_storage"))
        val CODEC: PacketCodec<PacketByteBuf, SellFromStoragePayload> = PacketCodec.of(
            { p, b -> b.writeUuid(p.pokemonUuid); b.writeInt(p.price) },
            { b -> SellFromStoragePayload(b.readUuid(), b.readInt()) }
        )
    }
}

// ── C2S: Sell item ──

data class SellItemPayload(val itemId: String, val itemNbt: NbtCompound, val count: Int, val price: Int) :
    CustomPayload {
    override fun getId() = ID

    companion object {
        val ID = CustomPayload.Id<SellItemPayload>(CobbleMarket.id("sell_item"))
        val CODEC: PacketCodec<PacketByteBuf, SellItemPayload> = PacketCodec.of(
            { p, b ->
                b.writeString(p.itemId); PacketCodecs.NBT_COMPOUND.encode(
                b,
                p.itemNbt
            ); b.writeInt(p.count); b.writeInt(p.price)
            },
            { b -> SellItemPayload(b.readString(), PacketCodecs.NBT_COMPOUND.decode(b), b.readInt(), b.readInt()) }
        )
    }
}

// ── C2S: Request item market ──

data class RequestItemMarketPayload(
    val sortMode: String,
    val page: Int,
    val mineOnly: Boolean,
    val pageSize: Int
) : CustomPayload {
    override fun getId() = ID

    companion object {
        val ID = CustomPayload.Id<RequestItemMarketPayload>(CobbleMarket.id("request_item_market"))
        val CODEC: PacketCodec<PacketByteBuf, RequestItemMarketPayload> = PacketCodec.of(
            { p, b -> b.writeString(p.sortMode); b.writeInt(p.page); b.writeBoolean(p.mineOnly); b.writeInt(p.pageSize) },
            { b -> RequestItemMarketPayload(b.readString(), b.readInt(), b.readBoolean(), b.readInt()) }
        )
    }
}

// ── S2C: Item market data ──

data class ItemMarketDataPayload(
    val entries: List<ItemEntry>,
    val totalPages: Int,
    val currentPage: Int,
    val pendingBalance: Int
) : CustomPayload {
    override fun getId() = ID

    companion object {
        val ID = CustomPayload.Id<ItemMarketDataPayload>(CobbleMarket.id("item_market_data"))
        val CODEC: PacketCodec<PacketByteBuf, ItemMarketDataPayload> = PacketCodec.of(
            { p, b ->
                b.writeVarInt(p.entries.size)
                p.entries.forEach { it.write(b) }
                b.writeInt(p.totalPages)
                b.writeInt(p.currentPage)
                b.writeInt(p.pendingBalance)
            },
            { b ->
                val size = b.readVarInt()
                val entries = (0 until size).map { ItemEntry.read(b) }
                ItemMarketDataPayload(entries, b.readInt(), b.readInt(), b.readInt())
            }
        )
    }
}

// ── C2S: Admin request all items ──

data class AdminRequestItemPayload(
    val sellerFilter: String,
    val sortMode: String,
    val page: Int,
    val pageSize: Int,
    val mineOnly: Boolean
) : CustomPayload {
    override fun getId() = ID

    companion object {
        val ID = CustomPayload.Id<AdminRequestItemPayload>(CobbleMarket.id("admin_request_item"))
        val CODEC: PacketCodec<PacketByteBuf, AdminRequestItemPayload> = PacketCodec.of(
            { p, b ->
                b.writeString(p.sellerFilter); b.writeString(p.sortMode); b.writeInt(p.page); b.writeInt(p.pageSize); b.writeBoolean(
                p.mineOnly
            )
            },
            { b -> AdminRequestItemPayload(b.readString(), b.readString(), b.readInt(), b.readInt(), b.readBoolean()) }
        )
    }
}

// ── C2S: Admin cancel item ──

data class AdminCancelItemPayload(val listingId: UUID) : CustomPayload {
    override fun getId() = ID

    companion object {
        val ID = CustomPayload.Id<AdminCancelItemPayload>(CobbleMarket.id("admin_cancel_item"))
        val CODEC: PacketCodec<PacketByteBuf, AdminCancelItemPayload> = PacketCodec.of(
            { p, b -> b.writeUuid(p.listingId) },
            { b -> AdminCancelItemPayload(b.readUuid()) }
        )
    }
}

// ── C2S: Buy item ──

data class BuyItemPayload(val listingId: UUID, val count: Int) : CustomPayload {
    override fun getId() = ID

    companion object {
        val ID = CustomPayload.Id<BuyItemPayload>(CobbleMarket.id("buy_item"))
        val CODEC: PacketCodec<PacketByteBuf, BuyItemPayload> = PacketCodec.of(
            { p, b -> b.writeUuid(p.listingId); b.writeInt(p.count) },
            { b -> BuyItemPayload(b.readUuid(), b.readInt()) }
        )
    }
}

// ── C2S: Cancel item ──

data class CancelItemPayload(val listingId: UUID) : CustomPayload {
    override fun getId() = ID

    companion object {
        val ID = CustomPayload.Id<CancelItemPayload>(CobbleMarket.id("cancel_item"))
        val CODEC: PacketCodec<PacketByteBuf, CancelItemPayload> = PacketCodec.of(
            { p, b -> b.writeUuid(p.listingId) },
            { b -> CancelItemPayload(b.readUuid()) }
        )
    }
}

// ── C2S: Request pokemon returns ──

class RequestPokemonReturnPayload : CustomPayload {
    override fun getId() = ID

    companion object {
        val ID = CustomPayload.Id<RequestPokemonReturnPayload>(CobbleMarket.id("request_pokemon_return"))
        val CODEC: PacketCodec<PacketByteBuf, RequestPokemonReturnPayload> = PacketCodec.of(
            { _, b -> b.writeInt(0) },
            { b -> b.readInt(); RequestPokemonReturnPayload() }
        )
    }
}

// ── S2C: Pokemon return data ──

data class PokemonReturnDataPayload(val pokemon: List<PokemonPreview>) : CustomPayload {
    override fun getId() = ID

    companion object {
        val ID = CustomPayload.Id<PokemonReturnDataPayload>(CobbleMarket.id("pokemon_return_data"))
        val CODEC: PacketCodec<PacketByteBuf, PokemonReturnDataPayload> = PacketCodec.of(
            { p, b -> b.writeVarInt(p.pokemon.size); p.pokemon.forEach { it.write(b) } },
            { b -> PokemonReturnDataPayload((0 until b.readVarInt()).map { PokemonPreview.read(b) }) }
        )
    }
}

// ── C2S: Claim pokemon returns ──

class ClaimPokemonReturnPayload : CustomPayload {
    override fun getId() = ID

    companion object {
        val ID = CustomPayload.Id<ClaimPokemonReturnPayload>(CobbleMarket.id("claim_pokemon_return"))
        val CODEC: PacketCodec<PacketByteBuf, ClaimPokemonReturnPayload> = PacketCodec.of(
            { _, b -> b.writeInt(0) },
            { b -> b.readInt(); ClaimPokemonReturnPayload() }
        )
    }
}

// ── C2S: Request item returns ──

class RequestItemReturnPayload : CustomPayload {
    override fun getId() = ID

    companion object {
        val ID = CustomPayload.Id<RequestItemReturnPayload>(CobbleMarket.id("request_item_return"))
        val CODEC: PacketCodec<PacketByteBuf, RequestItemReturnPayload> = PacketCodec.of(
            { _, b -> b.writeInt(0) },
            { b -> b.readInt(); RequestItemReturnPayload() }
        )
    }
}

// ── S2C: Item return data ──

data class ItemReturnDataPayload(val items: List<ItemEntry>) : CustomPayload {
    override fun getId() = ID

    companion object {
        val ID = CustomPayload.Id<ItemReturnDataPayload>(CobbleMarket.id("item_return_data"))
        val CODEC: PacketCodec<PacketByteBuf, ItemReturnDataPayload> = PacketCodec.of(
            { p, b -> b.writeVarInt(p.items.size); p.items.forEach { it.write(b) } },
            { b -> ItemReturnDataPayload((0 until b.readVarInt()).map { ItemEntry.read(b) }) }
        )
    }
}

// ── C2S: Claim item returns ──

class ClaimItemReturnPayload : CustomPayload {
    override fun getId() = ID

    companion object {
        val ID = CustomPayload.Id<ClaimItemReturnPayload>(CobbleMarket.id("claim_item_return"))
        val CODEC: PacketCodec<PacketByteBuf, ClaimItemReturnPayload> = PacketCodec.of(
            { _, b -> b.writeInt(0) },
            { b -> b.readInt(); ClaimItemReturnPayload() }
        )
    }
}

// ── Registration ──

private const val LISTING_DURATION_DAYS = 7

private fun parseSortMode(name: String): com.shusheng.cobblemarket.market.SortMode =
    com.shusheng.cobblemarket.market.SortMode.entries.firstOrNull { it.name == name }
        ?: com.shusheng.cobblemarket.market.SortMode.PRICE_ASC

internal fun canFitInInventory(player: ServerPlayerEntity, stack: ItemStack): Boolean {
    var remaining = stack.count
    val main = player.inventory.main
    for (i in 0 until main.size) {
        val slot = main[i]
        if (!slot.isEmpty && ItemStack.areItemsAndComponentsEqual(slot, stack)) {
            remaining -= (slot.maxCount - slot.count)
            if (remaining <= 0) return true
        }
    }
    for (i in 0 until main.size) {
        if (main[i].isEmpty) {
            remaining -= stack.maxCount
            if (remaining <= 0) return true
        }
    }
    return false
}

object MarketNetwork {

    fun register() {
        PayloadTypeRegistry.playC2S().register(RequestMarketPayload.ID, RequestMarketPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(BuyFromMarketPayload.ID, BuyFromMarketPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(CancelFromMarketPayload.ID, CancelFromMarketPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(RequestMyPokemonPayload.ID, RequestMyPokemonPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(SellFromStoragePayload.ID, SellFromStoragePayload.CODEC)
        PayloadTypeRegistry.playC2S().register(SellItemPayload.ID, SellItemPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(RequestItemMarketPayload.ID, RequestItemMarketPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(BuyItemPayload.ID, BuyItemPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(CancelItemPayload.ID, CancelItemPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(RequestPokemonReturnPayload.ID, RequestPokemonReturnPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(ClaimPokemonReturnPayload.ID, ClaimPokemonReturnPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(RequestItemReturnPayload.ID, RequestItemReturnPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(ClaimItemReturnPayload.ID, ClaimItemReturnPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(CollectBalancePayload.ID, CollectBalancePayload.CODEC)
        PayloadTypeRegistry.playC2S().register(RequestHistoryPayload.ID, RequestHistoryPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(AdminRequestPokemonPayload.ID, AdminRequestPokemonPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(AdminCancelPokemonPayload.ID, AdminCancelPokemonPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(AdminRequestItemPayload.ID, AdminRequestItemPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(AdminCancelItemPayload.ID, AdminCancelItemPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(MyPokemonListPayload.ID, MyPokemonListPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(HistoryDataPayload.ID, HistoryDataPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(OpenMarketPayload.ID, OpenMarketPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(MarketDataPayload.ID, MarketDataPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(ItemMarketDataPayload.ID, ItemMarketDataPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(PokemonReturnDataPayload.ID, PokemonReturnDataPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(ItemReturnDataPayload.ID, ItemReturnDataPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(MarketResultPayload.ID, MarketResultPayload.CODEC)

        ServerPlayNetworking.registerGlobalReceiver(RequestMarketPayload.ID) { payload, context ->
            val player = context.player()
            val server = player.server
            server.execute {
                val state = MarketState.get(server)
                state.expireOldListings(System.currentTimeMillis())

                val sortMode = parseSortMode(payload.sortMode)
                val results = state.search(
                    species = payload.speciesFilter.ifBlank { null },
                    shiny = if (payload.shinyOnly) true else null,
                    minLevel = if (payload.minLevel > 0) payload.minLevel else null,
                    maxLevel = if (payload.maxLevel < 100) payload.maxLevel else null,
                    sortBy = sortMode,
                    gender = payload.genderFilter.ifBlank { null },
                    typeFilter = payload.typeFilter.ifBlank { null },
                    minIvs = buildMap {
                        if (payload.minIvsHp >= 0) put("ivsHp", payload.minIvsHp)
                        if (payload.minIvsAtk >= 0) put("ivsAtk", payload.minIvsAtk)
                        if (payload.minIvsDef >= 0) put("ivsDef", payload.minIvsDef)
                        if (payload.minIvsSpAtk >= 0) put("ivsSpAtk", payload.minIvsSpAtk)
                        if (payload.minIvsSpDef >= 0) put("ivsSpDef", payload.minIvsSpDef)
                        if (payload.minIvsSpd >= 0) put("ivsSpd", payload.minIvsSpd)
                    },
                    sellerUuid = if (payload.mineOnly) player.uuid else null
                )

                val pageSize = payload.pageSize.coerceIn(1, 30)
                val totalPages = ((results.size - 1) / pageSize) + 1
                val clampedPage = payload.page.coerceIn(1, maxOf(1, totalPages))

                val pageEntries = if (results.isEmpty()) emptyList() else {
                    val start = (clampedPage - 1) * pageSize
                    results.drop(start).take(pageSize).map { listing ->
                        val detail = listing.extraData
                        ListingEntry(
                            id = listing.id,
                            sellerUuid = listing.sellerUuid,
                            species = listing.species,
                            speciesId = detail["speciesId"] ?: listing.species.lowercase().replace(" ", "_"),
                            level = listing.level,
                            shiny = listing.shiny,
                            price = listing.price,
                            sellerName = listing.sellerName,
                            primaryType = detail["primaryType"] ?: "normal",
                            secondaryType = detail["secondaryType"] ?: "",
                            ivsHp = detail["ivsHp"]?.toIntOrNull() ?: 0,
                            ivsAtk = detail["ivsAtk"]?.toIntOrNull() ?: 0,
                            ivsDef = detail["ivsDef"]?.toIntOrNull() ?: 0,
                            ivsSpAtk = detail["ivsSpAtk"]?.toIntOrNull() ?: 0,
                            ivsSpDef = detail["ivsSpDef"]?.toIntOrNull() ?: 0,
                            ivsSpd = detail["ivsSpd"]?.toIntOrNull() ?: 0,
                            nature = detail["nature"] ?: "?",
                            ability = detail["ability"] ?: "?",
                            gender = detail["gender"] ?: "?",
                            ball = detail["ball"] ?: "?",
                            ballItem = detail["ballItem"] ?: "cobblemon:poke_ball",
                            currencyName = com.shusheng.cobblemarket.config.CurrencyHandler.getName()
                        )
                    }
                }

                ServerPlayNetworking.send(
                    player,
                    MarketDataPayload(
                        pageEntries,
                        maxOf(1, totalPages),
                        clampedPage,
                        minOf(state.getPendingBalance(player.uuid), Int.MAX_VALUE.toLong()).toInt()
                    )
                )
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(BuyFromMarketPayload.ID) { payload, context ->
            val player = context.player()
            val server = player.server
            server.execute {
                val banCheckTime = System.currentTimeMillis()
                val banInfo = BanState.get(server).getBanInfo(player.uuid, banCheckTime)
                if (banInfo != null) {
                    val timeDesc = if (banInfo.isPermanent)
                        Text.translatable("cobblemarket.ban.permanent").string
                    else
                        Text.translatable(
                            "cobblemarket.ban.remaining",
                            BanState.formatRemaining(banInfo.expiresAt!! - banCheckTime)
                        ).string
                    val banMsg = if (banInfo.reason.isNotBlank())
                        Text.translatable("cobblemarket.ban.banned_msg_time_reason", timeDesc, banInfo.reason)
                    else
                        Text.translatable("cobblemarket.ban.banned_msg_time", timeDesc)
                    ServerPlayNetworking.send(player, MarketResultPayload(false, banMsg))
                    return@execute
                }
                val state = MarketState.get(server)
                state.expireOldListings(System.currentTimeMillis())
                val listing = state.getListing(payload.listingId)

                if (listing == null || !listing.isActive()) {
                    ServerPlayNetworking.send(
                        player,
                        MarketResultPayload(false, Text.translatable("cobblemarket.network.not_found"))
                    )
                    return@execute
                }
                if (listing.sellerUuid == player.uuid) {
                    ServerPlayNetworking.send(
                        player,
                        MarketResultPayload(false, Text.translatable("cobblemarket.network.cannot_buy_own"))
                    )
                    return@execute
                }
                if (!removeCurrency(player, listing.price)) {
                    ServerPlayNetworking.send(
                        player,
                        MarketResultPayload(
                            false,
                            Text.translatable(
                                "cobblemarket.network.need_diamonds",
                                listing.price,
                                com.shusheng.cobblemarket.config.CurrencyHandler.getName()
                            )
                        )
                    )
                    return@execute
                }

                val registryLookup = player.serverWorld.registryManager
                val pokemon = com.cobblemon.mod.common.pokemon.Pokemon().loadFromNBT(registryLookup, listing.pokemonNbt)
                val party = Cobblemon.storage.getParty(player)
                if (!party.add(pokemon)) {
                    giveCurrency(player, listing.price)
                    ServerPlayNetworking.send(
                        player,
                        MarketResultPayload(false, Text.translatable("cobblemarket.network.party_full"))
                    )
                    return@execute
                }

                state.addPendingBalance(listing.sellerUuid, listing.price)
                listing.status = ListingStatus.SOLD
                state.markModified()
                com.shusheng.cobblemarket.event.MarketEvents.PURCHASE.trigger(
                    com.shusheng.cobblemarket.event.PurchaseEvent(
                        player.uuid,
                        player.name.string,
                        listing.sellerUuid,
                        listing,
                        listing.price
                    )
                )

                ServerPlayNetworking.send(
                    player,
                    MarketResultPayload(
                        true,
                        Text.translatable(
                            "cobblemarket.network.bought",
                            listing.speciesText(),
                            listing.price,
                            com.shusheng.cobblemarket.config.CurrencyHandler.getName()
                        )
                    )
                )

                val seller = server.playerManager.getPlayer(listing.sellerUuid)
                seller?.sendMessage(Text.translatable("cobblemarket.network.sold", listing.speciesText()), false)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(CancelFromMarketPayload.ID) { payload, context ->
            val player = context.player()
            val server = player.server
            server.execute {
                val banCheckTime = System.currentTimeMillis()
                val banInfo = BanState.get(server).getBanInfo(player.uuid, banCheckTime)
                if (banInfo != null) {
                    val timeDesc = if (banInfo.isPermanent)
                        Text.translatable("cobblemarket.ban.permanent").string
                    else
                        Text.translatable(
                            "cobblemarket.ban.remaining",
                            BanState.formatRemaining(banInfo.expiresAt!! - banCheckTime)
                        ).string
                    val banMsg = if (banInfo.reason.isNotBlank())
                        Text.translatable("cobblemarket.ban.banned_msg_time_reason", timeDesc, banInfo.reason)
                    else
                        Text.translatable("cobblemarket.ban.banned_msg_time", timeDesc)
                    ServerPlayNetworking.send(player, MarketResultPayload(false, banMsg))
                    return@execute
                }
                val state = MarketState.get(server)
                state.expireOldListings(System.currentTimeMillis())
                val listing = state.getListing(payload.listingId)

                if (listing == null || !listing.isActive()) {
                    ServerPlayNetworking.send(
                        player,
                        MarketResultPayload(false, Text.translatable("cobblemarket.network.not_found"))
                    )
                    return@execute
                }
                if (listing.sellerUuid != player.uuid) {
                    ServerPlayNetworking.send(
                        player,
                        MarketResultPayload(false, Text.translatable("cobblemarket.network.cannot_buy_own"))
                    )
                    return@execute
                }

                val pokemon = com.cobblemon.mod.common.pokemon.Pokemon()
                    .loadFromNBT(player.serverWorld.registryManager, listing.pokemonNbt)
                val party = Cobblemon.storage.getParty(player)
                if (!party.add(pokemon)) {
                    ServerPlayNetworking.send(
                        player,
                        MarketResultPayload(false, Text.translatable("cobblemarket.network.party_full"))
                    )
                    return@execute
                }

                listing.status = ListingStatus.CANCELLED
                state.markModified()
                com.shusheng.cobblemarket.event.MarketEvents.CANCEL.trigger(
                    com.shusheng.cobblemarket.event.CancelEvent(
                        player.uuid,
                        listing
                    )
                )
                ServerPlayNetworking.send(
                    player,
                    MarketResultPayload(true, Text.translatable("cobblemarket.cmd.cancelled", listing.speciesText()))
                )
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(AdminRequestPokemonPayload.ID) { payload, context ->
            val player = context.player()
            if (!player.hasPermissionLevel(2)) return@registerGlobalReceiver
            val server = player.server
            server.execute {
                val state = MarketState.get(server)
                state.expireOldListings(System.currentTimeMillis())

                val sortMode = parseSortMode(payload.sortMode)
                val results = state.search(
                    species = payload.speciesFilter.ifBlank { null },
                    shiny = if (payload.shinyOnly) true else null,
                    sortBy = sortMode,
                    minIvs = buildMap {
                        if (payload.minIvsHp >= 0) put("ivsHp", payload.minIvsHp)
                        if (payload.minIvsAtk >= 0) put("ivsAtk", payload.minIvsAtk)
                        if (payload.minIvsDef >= 0) put("ivsDef", payload.minIvsDef)
                        if (payload.minIvsSpAtk >= 0) put("ivsSpAtk", payload.minIvsSpAtk)
                        if (payload.minIvsSpDef >= 0) put("ivsSpDef", payload.minIvsSpDef)
                        if (payload.minIvsSpd >= 0) put("ivsSpd", payload.minIvsSpd)
                    },
                    sellerUuid = if (payload.mineOnly) player.uuid else null,
                    sellerName = payload.sellerFilter.ifBlank { null }
                )

                val pageSize = payload.pageSize.coerceIn(1, 30)
                val totalPages = ((results.size - 1) / pageSize) + 1
                val clampedPage = payload.page.coerceIn(1, maxOf(1, totalPages))

                val pageEntries = if (results.isEmpty()) emptyList() else {
                    val start = (clampedPage - 1) * pageSize
                    results.drop(start).take(pageSize).map { listing ->
                        val detail = listing.extraData
                        ListingEntry(
                            id = listing.id,
                            sellerUuid = listing.sellerUuid,
                            species = listing.species,
                            speciesId = detail["speciesId"] ?: listing.species.lowercase().replace(" ", "_"),
                            level = listing.level,
                            shiny = listing.shiny,
                            price = listing.price,
                            sellerName = listing.sellerName,
                            primaryType = detail["primaryType"] ?: "normal",
                            secondaryType = detail["secondaryType"] ?: "",
                            ivsHp = detail["ivsHp"]?.toIntOrNull() ?: 0,
                            ivsAtk = detail["ivsAtk"]?.toIntOrNull() ?: 0,
                            ivsDef = detail["ivsDef"]?.toIntOrNull() ?: 0,
                            ivsSpAtk = detail["ivsSpAtk"]?.toIntOrNull() ?: 0,
                            ivsSpDef = detail["ivsSpDef"]?.toIntOrNull() ?: 0,
                            ivsSpd = detail["ivsSpd"]?.toIntOrNull() ?: 0,
                            nature = detail["nature"] ?: "?",
                            ability = detail["ability"] ?: "?",
                            gender = detail["gender"] ?: "?",
                            ball = detail["ball"] ?: "?",
                            ballItem = detail["ballItem"] ?: "cobblemon:poke_ball",
                            currencyName = com.shusheng.cobblemarket.config.CurrencyHandler.getName()
                        )
                    }
                }

                ServerPlayNetworking.send(player, MarketDataPayload(pageEntries, maxOf(1, totalPages), clampedPage, 0))
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(AdminCancelPokemonPayload.ID) { payload, context ->
            val player = context.player()
            if (!player.hasPermissionLevel(2)) return@registerGlobalReceiver
            val server = player.server
            server.execute {
                val state = MarketState.get(server)
                val listing = state.getListing(payload.listingId)
                if (listing == null || !listing.isActive()) {
                    ServerPlayNetworking.send(
                        player,
                        MarketResultPayload(false, Text.translatable("cobblemarket.network.not_found"))
                    )
                    return@execute
                }
                listing.status = ListingStatus.CANCELLED
                state.addPendingReturn(listing.sellerUuid, listing)
                state.markModified()
                com.shusheng.cobblemarket.event.MarketEvents.CANCEL.trigger(
                    com.shusheng.cobblemarket.event.CancelEvent(listing.sellerUuid, listing)
                )
                server.playerManager.getPlayer(listing.sellerUuid)?.sendMessage(
                    Text.translatable("cobblemarket.cmd.cancelled", listing.speciesText()), false
                )
                ServerPlayNetworking.send(
                    player,
                    MarketResultPayload(
                        true,
                        Text.translatable("cobblemarket.op.cancelled", listing.sellerName, listing.speciesText())
                    )
                )
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(AdminRequestItemPayload.ID) { payload, context ->
            val player = context.player()
            if (!player.hasPermissionLevel(2)) return@registerGlobalReceiver
            val server = player.server
            server.execute {
                val state = ItemMarketState.get(server)
                state.expireOldListings(System.currentTimeMillis())

                val sortMode = parseSortMode(payload.sortMode)
                val results = state.search(
                    sortBy = sortMode,
                    sellerUuid = if (payload.mineOnly) player.uuid else null,
                    sellerName = payload.sellerFilter.ifBlank { null }
                )

                val pageSize = payload.pageSize.coerceIn(1, 200)
                val totalPages = ((results.size - 1) / pageSize) + 1
                val clampedPage = payload.page.coerceIn(1, maxOf(1, totalPages))

                val pageEntries = if (results.isEmpty()) emptyList() else {
                    val start = (clampedPage - 1) * pageSize
                    results.drop(start).take(pageSize).map { listing ->
                        ItemEntry(
                            id = listing.id,
                            sellerUuid = listing.sellerUuid,
                            sellerName = listing.sellerName,
                            itemId = listing.itemId,
                            itemNbt = listing.itemNbt,
                            count = listing.count,
                            price = listing.price,
                            currencyName = CurrencyHandler.getName()
                        )
                    }
                }

                ServerPlayNetworking.send(
                    player,
                    ItemMarketDataPayload(pageEntries, maxOf(1, totalPages), clampedPage, 0)
                )
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(AdminCancelItemPayload.ID) { payload, context ->
            val player = context.player()
            if (!player.hasPermissionLevel(2)) return@registerGlobalReceiver
            val server = player.server
            server.execute {
                val state = ItemMarketState.get(server)
                val listing = state.getListing(payload.listingId)
                if (listing == null || !listing.isActive()) {
                    ServerPlayNetworking.send(
                        player,
                        MarketResultPayload(false, Text.translatable("cobblemarket.network.not_found"))
                    )
                    return@execute
                }
                listing.status = ListingStatus.CANCELLED
                state.addPendingReturn(listing.sellerUuid, listing)
                state.markModified()
                com.shusheng.cobblemarket.event.TransactionHistory.get(server).addRecord(
                    com.shusheng.cobblemarket.event.TransactionRecord(
                        timestamp = System.currentTimeMillis(),
                        type = com.shusheng.cobblemarket.event.TransactionType.CANCEL,
                        category = com.shusheng.cobblemarket.event.TransactionCategory.ITEM,
                        sellerUuid = listing.sellerUuid,
                        sellerName = listing.sellerName,
                        buyerUuid = null,
                        buyerName = "",
                        species = listing.itemId,
                        price = listing.price,
                        fee = 0
                    )
                )
                server.playerManager.getPlayer(listing.sellerUuid)?.sendMessage(
                    Text.translatable("cobblemarket.item.cancelled"), false
                )
                ServerPlayNetworking.send(
                    player,
                    MarketResultPayload(
                        true,
                        Text.translatable("cobblemarket.op.cancelled", listing.sellerName, listing.itemId)
                    )
                )
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(RequestMyPokemonPayload.ID) { _, context ->
            val player = context.player()
            val server = player.server
            server.execute {
                val previews = mutableListOf<PokemonPreview>()
                val party = Cobblemon.storage.getParty(player)
                for (i in 0..5) {
                    try {
                        val p = party.get(PartyPosition(i)) ?: continue
                        previews.add(toPreview(p, "party", i))
                    } catch (_: Exception) {
                    }
                }
                try {
                    val pc = Cobblemon.storage.getPC(player)
                    // PC is iterable; limit to first 30 to avoid huge packets
                    val world = player.serverWorld
                    var count = 0
                    val iter = pc.iterator()
                    while (iter.hasNext() && count < 30) {
                        try {
                            val p = iter.next()
                            previews.add(toPreview(p, "pc", count))
                            count++
                        } catch (_: Exception) {
                        }
                    }
                } catch (_: Exception) {
                }
                ServerPlayNetworking.send(player, MyPokemonListPayload(previews))
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(SellFromStoragePayload.ID) { payload, context ->
            val player = context.player()
            val server = player.server
            server.execute {
                val banCheckTime = System.currentTimeMillis()
                val banInfo = BanState.get(server).getBanInfo(player.uuid, banCheckTime)
                if (banInfo != null) {
                    val timeDesc = if (banInfo.isPermanent)
                        Text.translatable("cobblemarket.ban.permanent").string
                    else
                        Text.translatable(
                            "cobblemarket.ban.remaining",
                            BanState.formatRemaining(banInfo.expiresAt!! - banCheckTime)
                        ).string
                    val banMsg = if (banInfo.reason.isNotBlank())
                        Text.translatable("cobblemarket.ban.banned_msg_time_reason", timeDesc, banInfo.reason)
                    else
                        Text.translatable("cobblemarket.ban.banned_msg_time", timeDesc)
                    ServerPlayNetworking.send(player, MarketResultPayload(false, banMsg))
                    return@execute
                }
                val party = Cobblemon.storage.getParty(player)
                val pc = Cobblemon.storage.getPC(player)
                val pokemonUuid = payload.pokemonUuid

                // Find in party first
                var pokemon = party.find { it.uuid == pokemonUuid }
                val fromParty = pokemon != null
                if (pokemon == null) {
                    pokemon = pc.find { it.uuid == pokemonUuid }
                }
                if (pokemon == null) {
                    ServerPlayNetworking.send(
                        player,
                        MarketResultPayload(false, Text.translatable("cobblemarket.network.not_found"))
                    )
                    return@execute
                }
                if (payload.price <= 0) {
                    ServerPlayNetworking.send(
                        player,
                        MarketResultPayload(false, Text.translatable("cobblemarket.network.invalid_price"))
                    )
                    return@execute
                }
                // 队伍至少要留一只精灵
                if (fromParty && party.occupied() <= 1) {
                    ServerPlayNetworking.send(
                        player,
                        MarketResultPayload(false, Text.translatable("cobblemarket.network.party_last"))
                    )
                    return@execute
                }

                // 黑名单检查
                if (com.shusheng.cobblemarket.market.PokemonBlacklistState.get(server)
                        .isBlacklisted(pokemon.species.resourceIdentifier.toString(), pokemon.ivs)
                ) {
                    ServerPlayNetworking.send(
                        player,
                        MarketResultPayload(false, Text.translatable("cobblemarket.blacklist.blocked"))
                    )
                    return@execute
                }

                // 上架数量上限检查
                val maxPokemonListings = com.shusheng.cobblemarket.config.CobbleMarketConfig.maxPokemonListingsPerPlayer
                if (maxPokemonListings > 0 && MarketState.get(server)
                        .countActiveBySeller(player.uuid) >= maxPokemonListings
                ) {
                    ServerPlayNetworking.send(
                        player,
                        MarketResultPayload(
                            false,
                            Text.translatable("cobblemarket.cmd.max_listings", maxPokemonListings)
                        )
                    )
                    return@execute
                }

                // Listing fee check
                val feePercent = com.shusheng.cobblemarket.config.CobbleMarketConfig.pokemonListingFeePercent
                val fee = if (feePercent > 0) Math.ceil(payload.price * feePercent / 100.0).toInt() else 0
                if (fee > 0 && !CurrencyHandler.remove(player, fee)) {
                    ServerPlayNetworking.send(
                        player, MarketResultPayload(
                            false,
                            Text.translatable("cobblemarket.cmd.need_fee", fee, CurrencyHandler.getName())
                        )
                    )
                    return@execute
                }

                // 移除精灵
                if (fromParty) party.remove(pokemon) else pc.remove(pokemon)

                val world = player.serverWorld
                val nbt = pokemon.saveToNBT(world.registryManager, NbtCompound())
                val now = System.currentTimeMillis()

                val extra = mutableMapOf(
                    "speciesId" to pokemon.species.resourceIdentifier.toString(),
                    "speciesName" to pokemon.species.translatedName.string,
                    "speciesKey" to com.shusheng.cobblemarket.util.SpeciesText.translationKey(pokemon.species),
                    "primaryType" to "cobblemon.type.${pokemon.primaryType.name.lowercase()}",
                    "ivsHp" to pokemon.ivs[com.cobblemon.mod.common.api.pokemon.stats.Stats.HP].toString(),
                    "ivsAtk" to pokemon.ivs[com.cobblemon.mod.common.api.pokemon.stats.Stats.ATTACK].toString(),
                    "ivsDef" to pokemon.ivs[com.cobblemon.mod.common.api.pokemon.stats.Stats.DEFENCE].toString(),
                    "ivsSpAtk" to pokemon.ivs[com.cobblemon.mod.common.api.pokemon.stats.Stats.SPECIAL_ATTACK].toString(),
                    "ivsSpDef" to pokemon.ivs[com.cobblemon.mod.common.api.pokemon.stats.Stats.SPECIAL_DEFENCE].toString(),
                    "ivsSpd" to pokemon.ivs[com.cobblemon.mod.common.api.pokemon.stats.Stats.SPEED].toString(),
                    "nature" to "cobblemon.nature.${pokemon.effectiveNature.name.path}",
                    "ability" to "cobblemon.ability.${pokemon.ability.name}",
                    "gender" to pokemon.gender.name,
                    "ball" to "item.cobblemon.${pokemon.caughtBall.name.path}",
                    "ballItem" to "cobblemon:${pokemon.caughtBall.name.path}"
                )
                pokemon.secondaryType?.let { extra["secondaryType"] = "cobblemon.type.${it.name.lowercase()}" }

                val listing = MarketListing(
                    id = UUID.randomUUID(),
                    sellerUuid = player.uuid,
                    sellerName = player.name.string,
                    pokemonNbt = nbt,
                    species = pokemon.species.name,
                    level = pokemon.level,
                    shiny = pokemon.shiny,
                    price = payload.price,
                    createdAt = now,
                    expiresAt = now + com.shusheng.cobblemarket.config.CobbleMarketConfig.listingDurationDays * 24L * 60 * 60 * 1000,
                    status = ListingStatus.ACTIVE,
                    extraData = extra
                )

                val state = MarketState.get(server)
                state.addListing(listing)
                com.shusheng.cobblemarket.event.MarketEvents.ADD.trigger(
                    com.shusheng.cobblemarket.event.AddEvent(
                        listing,
                        fee
                    )
                )

                val listedMsg: Text = if (fee > 0)
                    Text.translatable(
                        "cobblemarket.cmd.listed_fee",
                        pokemon.species.translatedName,
                        pokemon.level,
                        payload.price,
                        CurrencyHandler.getName(),
                        fee,
                        CurrencyHandler.getName()
                    )
                else
                    Text.translatable(
                        "cobblemarket.cmd.listed",
                        pokemon.species.translatedName,
                        pokemon.level,
                        payload.price,
                        CurrencyHandler.getName()
                    )
                ServerPlayNetworking.send(player, MarketResultPayload(true, listedMsg))
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(SellItemPayload.ID) { payload, context ->
            val player = context.player()
            val server = player.server
            server.execute {
                val banCheckTime = System.currentTimeMillis()
                val banInfo = BanState.get(server).getBanInfo(player.uuid, banCheckTime)
                if (banInfo != null) {
                    val timeDesc = if (banInfo.isPermanent)
                        Text.translatable("cobblemarket.ban.permanent").string
                    else
                        Text.translatable(
                            "cobblemarket.ban.remaining",
                            BanState.formatRemaining(banInfo.expiresAt!! - banCheckTime)
                        ).string
                    val banMsg = if (banInfo.reason.isNotBlank())
                        Text.translatable("cobblemarket.ban.banned_msg_time_reason", timeDesc, banInfo.reason)
                    else
                        Text.translatable("cobblemarket.ban.banned_msg_time", timeDesc)
                    ServerPlayNetworking.send(player, MarketResultPayload(false, banMsg))
                    return@execute
                }
                if (payload.count <= 0 || payload.price <= 0) {
                    ServerPlayNetworking.send(
                        player,
                        MarketResultPayload(false, Text.translatable("cobblemarket.network.not_found"))
                    )
                    return@execute
                }

                // 重建目标物品，以服务端重建的物品为准（不信任客户端 itemId/itemNbt 的一致性）
                val targetStack = ItemStack.fromNbtOrEmpty(player.serverWorld.registryManager, payload.itemNbt)
                if (targetStack.isEmpty) {
                    ServerPlayNetworking.send(
                        player,
                        MarketResultPayload(false, Text.translatable("cobblemarket.network.not_found"))
                    )
                    return@execute
                }
                val authoritativeItemId = Registries.ITEM.getId(targetStack.item).toString()

                // 物品黑名单检查（以权威 itemId 为准）
                if (com.shusheng.cobblemarket.market.ItemBlacklistState.get(server).contains(authoritativeItemId)) {
                    ServerPlayNetworking.send(
                        player,
                        MarketResultPayload(false, Text.translatable("cobblemarket.blacklist.item_blocked"))
                    )
                    return@execute
                }

                val main = player.inventory.main
                var available = 0
                for (i in 0 until main.size) {
                    val stack = main[i]
                    if (ItemStack.areItemsAndComponentsEqual(stack, targetStack)) available += stack.count
                }
                if (available < payload.count) {
                    ServerPlayNetworking.send(
                        player,
                        MarketResultPayload(false, Text.translatable("cobblemarket.network.not_found"))
                    )
                    return@execute
                }

                // 上架数量上限检查
                val maxItemListings = com.shusheng.cobblemarket.config.CobbleMarketConfig.maxItemListingsPerPlayer
                if (maxItemListings > 0 && ItemMarketState.get(server)
                        .countActiveBySeller(player.uuid) >= maxItemListings
                ) {
                    ServerPlayNetworking.send(
                        player,
                        MarketResultPayload(false, Text.translatable("cobblemarket.cmd.max_listings", maxItemListings))
                    )
                    return@execute
                }

                // 手续费（基于总价）
                val feePercent = com.shusheng.cobblemarket.config.CobbleMarketConfig.itemListingFeePercent
                val totalPrice = payload.price.toLong() * payload.count
                val fee = if (feePercent > 0) Math.ceil(totalPrice * feePercent / 100.0).toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else 0
                if (fee > 0 && !CurrencyHandler.remove(player, fee)) {
                    ServerPlayNetworking.send(
                        player, MarketResultPayload(
                            false,
                            Text.translatable("cobblemarket.cmd.need_fee", fee, CurrencyHandler.getName())
                        )
                    )
                    return@execute
                }

                // 扣物品，并捕获真实物品 NBT（不信任客户端 NBT）
                var remaining = payload.count
                var listingNbt: NbtCompound? = null
                for (i in 0 until main.size) {
                    val stack = main[i]
                    if (ItemStack.areItemsAndComponentsEqual(stack, targetStack)) {
                        if (listingNbt == null) {
                            listingNbt = stack.encode(player.serverWorld.registryManager) as? NbtCompound
                        }
                        val r = minOf(remaining, stack.count)
                        stack.decrement(r)
                        remaining -= r
                        if (remaining <= 0) break
                    }
                }

                // 手动同步背包，确保客户端先收到背包更新、再收到上架结果
                player.currentScreenHandler.sendContentUpdates()

                val now = System.currentTimeMillis()
                val listing = ItemListing(
                    id = UUID.randomUUID(),
                    sellerUuid = player.uuid,
                    sellerName = player.name.string,
                    itemId = authoritativeItemId,
                    itemNbt = listingNbt ?: payload.itemNbt,
                    count = payload.count,
                    price = payload.price,
                    createdAt = now,
                    expiresAt = now + com.shusheng.cobblemarket.config.CobbleMarketConfig.listingDurationDays * 24L * 60 * 60 * 1000,
                    status = ListingStatus.ACTIVE
                )

                val state = ItemMarketState.get(server)
                state.addListing(listing)

                com.shusheng.cobblemarket.event.TransactionHistory.get(server).addRecord(
                    com.shusheng.cobblemarket.event.TransactionRecord(
                        timestamp = System.currentTimeMillis(),
                        type = com.shusheng.cobblemarket.event.TransactionType.ADD,
                        category = com.shusheng.cobblemarket.event.TransactionCategory.ITEM,
                        sellerUuid = player.uuid,
                        sellerName = player.name.string,
                        buyerUuid = null,
                        buyerName = "",
                        species = payload.itemId,
                        price = payload.price,
                        fee = fee
                    )
                )

                val listedMsg: Text = if (fee > 0)
                    Text.translatable(
                        "cobblemarket.item.listed_fee",
                        targetStack.item.name,
                        payload.count,
                        payload.price,
                        CurrencyHandler.getName(),
                        fee,
                        CurrencyHandler.getName()
                    )
                else
                    Text.translatable(
                        "cobblemarket.item.listed",
                        targetStack.item.name,
                        payload.count,
                        payload.price,
                        CurrencyHandler.getName()
                    )
                ServerPlayNetworking.send(player, MarketResultPayload(true, listedMsg))
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(RequestItemMarketPayload.ID) { payload, context ->
            val player = context.player()
            val server = player.server
            server.execute {
                val state = ItemMarketState.get(server)
                state.expireOldListings(System.currentTimeMillis())

                val sortMode = parseSortMode(payload.sortMode)
                val results = state.search(
                    sortBy = sortMode,
                    sellerUuid = if (payload.mineOnly) player.uuid else null
                )

                val pageSize = payload.pageSize.coerceIn(1, 200)
                val totalPages = ((results.size - 1) / pageSize) + 1
                val clampedPage = payload.page.coerceIn(1, maxOf(1, totalPages))

                val pageEntries = if (results.isEmpty()) emptyList() else {
                    val start = (clampedPage - 1) * pageSize
                    results.drop(start).take(pageSize).map { listing ->
                        ItemEntry(
                            id = listing.id,
                            sellerUuid = listing.sellerUuid,
                            sellerName = listing.sellerName,
                            itemId = listing.itemId,
                            itemNbt = listing.itemNbt,
                            count = listing.count,
                            price = listing.price,
                            currencyName = CurrencyHandler.getName()
                        )
                    }
                }

                ServerPlayNetworking.send(
                    player,
                    ItemMarketDataPayload(
                        pageEntries,
                        maxOf(1, totalPages),
                        clampedPage,
                        minOf(MarketState.get(server).getPendingBalance(player.uuid), Int.MAX_VALUE.toLong()).toInt()
                    )
                )
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(BuyItemPayload.ID) { payload, context ->
            val player = context.player()
            val server = player.server
            server.execute {
                val banCheckTime = System.currentTimeMillis()
                val banInfo = BanState.get(server).getBanInfo(player.uuid, banCheckTime)
                if (banInfo != null) {
                    val timeDesc = if (banInfo.isPermanent)
                        Text.translatable("cobblemarket.ban.permanent").string
                    else
                        Text.translatable(
                            "cobblemarket.ban.remaining",
                            BanState.formatRemaining(banInfo.expiresAt!! - banCheckTime)
                        ).string
                    val banMsg = if (banInfo.reason.isNotBlank())
                        Text.translatable("cobblemarket.ban.banned_msg_time_reason", timeDesc, banInfo.reason)
                    else
                        Text.translatable("cobblemarket.ban.banned_msg_time", timeDesc)
                    ServerPlayNetworking.send(player, MarketResultPayload(false, banMsg))
                    return@execute
                }
                val state = ItemMarketState.get(server)
                state.expireOldListings(System.currentTimeMillis())
                val listing = state.getListing(payload.listingId)
                if (listing == null || !listing.isActive()) {
                    ServerPlayNetworking.send(
                        player,
                        MarketResultPayload(false, Text.translatable("cobblemarket.network.not_found"))
                    )
                    return@execute
                }
                if (listing.sellerUuid == player.uuid) {
                    ServerPlayNetworking.send(
                        player,
                        MarketResultPayload(false, Text.translatable("cobblemarket.network.cannot_buy_own"))
                    )
                    return@execute
                }
                val count = payload.count
                if (count <= 0 || count > listing.count) {
                    ServerPlayNetworking.send(
                        player,
                        MarketResultPayload(false, Text.translatable("cobblemarket.network.not_found"))
                    )
                    return@execute
                }
                val stack = ItemStack.fromNbtOrEmpty(player.serverWorld.registryManager, listing.itemNbt)
                stack.count = count

                if (!canFitInInventory(player, stack)) {
                    ServerPlayNetworking.send(
                        player,
                        MarketResultPayload(false, Text.translatable("cobblemarket.network.inventory_full"))
                    )
                    return@execute
                }

                val totalPriceLong = listing.price.toLong() * count
                if (totalPriceLong <= 0 || totalPriceLong > Int.MAX_VALUE) {
                    ServerPlayNetworking.send(
                        player,
                        MarketResultPayload(false, Text.translatable("cobblemarket.network.invalid_price"))
                    )
                    return@execute
                }
                val totalPrice = totalPriceLong.toInt()
                if (!CurrencyHandler.remove(player, totalPrice)) {
                    ServerPlayNetworking.send(
                        player,
                        MarketResultPayload(
                            false,
                            Text.translatable(
                                "cobblemarket.network.need_diamonds",
                                totalPrice,
                                CurrencyHandler.getName()
                            )
                        )
                    )
                    return@execute
                }

                if (!player.inventory.insertStack(stack)) {
                    // insertStack 非原子，可能已塞入部分物品；回滚已插入部分，避免复制。
                    val inserted = count - stack.count
                    if (inserted > 0) {
                        var toRemove = inserted
                        for (i in 0 until player.inventory.main.size) {
                            val slot = player.inventory.main[i]
                            if (ItemStack.areItemsAndComponentsEqual(slot, stack)) {
                                val r = minOf(toRemove, slot.count)
                                slot.decrement(r)
                                toRemove -= r
                                if (toRemove <= 0) break
                            }
                        }
                    }
                    CurrencyHandler.give(player, totalPrice.toLong())
                    ServerPlayNetworking.send(
                        player,
                        MarketResultPayload(false, Text.translatable("cobblemarket.network.inventory_full"))
                    )
                    return@execute
                }

                MarketState.get(server).addPendingBalance(listing.sellerUuid, totalPrice)
                listing.count -= count
                if (listing.count <= 0) {
                    listing.status = ListingStatus.SOLD
                }
                state.markModified()

                com.shusheng.cobblemarket.event.TransactionHistory.get(server).addRecord(
                    com.shusheng.cobblemarket.event.TransactionRecord(
                        timestamp = System.currentTimeMillis(),
                        type = com.shusheng.cobblemarket.event.TransactionType.PURCHASE,
                        category = com.shusheng.cobblemarket.event.TransactionCategory.ITEM,
                        sellerUuid = listing.sellerUuid,
                        sellerName = listing.sellerName,
                        buyerUuid = player.uuid,
                        buyerName = player.name.string,
                        species = listing.itemId,
                        price = totalPrice,
                        fee = 0
                    )
                )

                ServerPlayNetworking.send(
                    player,
                    MarketResultPayload(
                        true,
                        Text.translatable(
                            "cobblemarket.item.bought",
                            count,
                            listing.itemId,
                            totalPrice,
                            CurrencyHandler.getName()
                        )
                    )
                )

                val seller = server.playerManager.getPlayer(listing.sellerUuid)
                val soldItemName = Identifier.tryParse(listing.itemId)?.let { Registries.ITEM.get(it).name }
                    ?: Text.literal(listing.itemId)
                seller?.sendMessage(Text.translatable("cobblemarket.network.sold", soldItemName), false)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(CancelItemPayload.ID) { payload, context ->
            val player = context.player()
            val server = player.server
            server.execute {
                val banCheckTime = System.currentTimeMillis()
                val banInfo = BanState.get(server).getBanInfo(player.uuid, banCheckTime)
                if (banInfo != null) {
                    val timeDesc = if (banInfo.isPermanent)
                        Text.translatable("cobblemarket.ban.permanent").string
                    else
                        Text.translatable(
                            "cobblemarket.ban.remaining",
                            BanState.formatRemaining(banInfo.expiresAt!! - banCheckTime)
                        ).string
                    val banMsg = if (banInfo.reason.isNotBlank())
                        Text.translatable("cobblemarket.ban.banned_msg_time_reason", timeDesc, banInfo.reason)
                    else
                        Text.translatable("cobblemarket.ban.banned_msg_time", timeDesc)
                    ServerPlayNetworking.send(player, MarketResultPayload(false, banMsg))
                    return@execute
                }
                val state = ItemMarketState.get(server)
                state.expireOldListings(System.currentTimeMillis())
                val listing = state.getListing(payload.listingId)
                if (listing == null || !listing.isActive()) {
                    ServerPlayNetworking.send(
                        player,
                        MarketResultPayload(false, Text.translatable("cobblemarket.network.not_found"))
                    )
                    return@execute
                }
                if (listing.sellerUuid != player.uuid) {
                    ServerPlayNetworking.send(
                        player,
                        MarketResultPayload(false, Text.translatable("cobblemarket.cmd.not_your_listing"))
                    )
                    return@execute
                }

                val stack = ItemStack.fromNbtOrEmpty(player.serverWorld.registryManager, listing.itemNbt)
                stack.count = listing.count
                if (!canFitInInventory(player, stack)) {
                    ServerPlayNetworking.send(
                        player,
                        MarketResultPayload(false, Text.translatable("cobblemarket.network.inventory_full"))
                    )
                    return@execute
                }
                player.inventory.insertStack(stack)
                listing.status = ListingStatus.CANCELLED
                state.markModified()

                com.shusheng.cobblemarket.event.TransactionHistory.get(server).addRecord(
                    com.shusheng.cobblemarket.event.TransactionRecord(
                        timestamp = System.currentTimeMillis(),
                        type = com.shusheng.cobblemarket.event.TransactionType.CANCEL,
                        category = com.shusheng.cobblemarket.event.TransactionCategory.ITEM,
                        sellerUuid = player.uuid,
                        sellerName = listing.sellerName,
                        buyerUuid = null,
                        buyerName = "",
                        species = listing.itemId,
                        price = listing.price,
                        fee = 0
                    )
                )

                ServerPlayNetworking.send(
                    player,
                    MarketResultPayload(true, Text.translatable("cobblemarket.item.cancelled"))
                )
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(CollectBalancePayload.ID) { _, context ->
            val player = context.player()
            val server = player.server
            server.execute {
                val state = MarketState.get(server)
                val amount = state.claimPendingBalance(player.uuid)
                if (amount <= 0) {
                    ServerPlayNetworking.send(
                        player,
                        MarketResultPayload(false, Text.translatable("cobblemarket.cmd.no_earnings"))
                    )
                    return@execute
                }
                CurrencyHandler.give(player, amount)
                ServerPlayNetworking.send(
                    player,
                    MarketResultPayload(
                        true,
                        Text.translatable("cobblemarket.cmd.collected", amount, CurrencyHandler.getName())
                    )
                )
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(RequestHistoryPayload.ID) { payload, context ->
            val player = context.player()
            val server = player.server
            server.execute {
                val history = com.shusheng.cobblemarket.event.TransactionHistory.get(server)
                val isAdmin = player.hasPermissionLevel(2)
                val records =
                    if (payload.all && isAdmin) history.getRecords() else history.getRecordsByPlayer(player.uuid)
                val entries = records.take(50).map { r ->
                    val t =
                        if (r.type == com.shusheng.cobblemarket.event.TransactionType.PURCHASE && r.buyerUuid == player.uuid) "BUY" else r.type.name
                    HistoryEntry(t, r.category.name, r.species, r.price, r.buyerName, r.sellerName, r.timestamp)
                }
                ServerPlayNetworking.send(player, HistoryDataPayload(entries))
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(RequestPokemonReturnPayload.ID) { _, context ->
            val player = context.player()
            val server = player.server
            server.execute {
                val state = MarketState.get(server)
                val returns = state.getPendingReturns(player.uuid)
                val previews = returns.mapIndexed { i, listing ->
                    val pokemon = com.cobblemon.mod.common.pokemon.Pokemon()
                        .loadFromNBT(player.serverWorld.registryManager, listing.pokemonNbt)
                    toPreview(pokemon, "return", i)
                }
                ServerPlayNetworking.send(player, PokemonReturnDataPayload(previews))
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(ClaimPokemonReturnPayload.ID) { _, context ->
            val player = context.player()
            val server = player.server
            server.execute {
                val state = MarketState.get(server)
                val returned = state.claimReturns(player)
                val remaining = state.getPendingReturns(player.uuid).size
                val msg = if (remaining > 0)
                    Text.translatable("cobblemarket.return.claimed", returned, remaining)
                else
                    Text.translatable("cobblemarket.return.claimed_all", returned)
                ServerPlayNetworking.send(player, MarketResultPayload(true, msg))
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(RequestItemReturnPayload.ID) { _, context ->
            val player = context.player()
            val server = player.server
            server.execute {
                val state = ItemMarketState.get(server)
                val returns = state.getPendingReturns(player.uuid)
                val entries = returns.map { listing ->
                    ItemEntry(
                        id = listing.id,
                        sellerUuid = listing.sellerUuid,
                        sellerName = listing.sellerName,
                        itemId = listing.itemId,
                        itemNbt = listing.itemNbt,
                        count = listing.count,
                        price = listing.price,
                        currencyName = CurrencyHandler.getName()
                    )
                }
                ServerPlayNetworking.send(player, ItemReturnDataPayload(entries))
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(ClaimItemReturnPayload.ID) { _, context ->
            val player = context.player()
            val server = player.server
            server.execute {
                val state = ItemMarketState.get(server)
                val returned = state.claimReturns(player)
                val remaining = state.getPendingReturns(player.uuid).size
                val msg = if (remaining > 0)
                    Text.translatable("cobblemarket.return.item_claimed", returned, remaining)
                else
                    Text.translatable("cobblemarket.return.item_claimed_all", returned)
                ServerPlayNetworking.send(player, MarketResultPayload(true, msg))
            }
        }
    }

    private fun toPreview(pokemon: com.cobblemon.mod.common.pokemon.Pokemon, source: String, slot: Int) =
        PokemonPreview(
            uuid = pokemon.uuid,
            species = pokemon.species.translatedName.string,
            speciesId = pokemon.species.resourceIdentifier.toString(),
            speciesName = pokemon.species.name,
            level = pokemon.level,
            shiny = pokemon.shiny,
            gender = pokemon.gender.name,
            nature = "cobblemon.nature.${pokemon.effectiveNature.name.path}",
            ability = "cobblemon.ability.${pokemon.ability.name}",
            ivsHp = pokemon.ivs[com.cobblemon.mod.common.api.pokemon.stats.Stats.HP] ?: 0,
            ivsAtk = pokemon.ivs[com.cobblemon.mod.common.api.pokemon.stats.Stats.ATTACK] ?: 0,
            ivsDef = pokemon.ivs[com.cobblemon.mod.common.api.pokemon.stats.Stats.DEFENCE] ?: 0,
            ivsSpAtk = pokemon.ivs[com.cobblemon.mod.common.api.pokemon.stats.Stats.SPECIAL_ATTACK] ?: 0,
            ivsSpDef = pokemon.ivs[com.cobblemon.mod.common.api.pokemon.stats.Stats.SPECIAL_DEFENCE] ?: 0,
            ivsSpd = pokemon.ivs[com.cobblemon.mod.common.api.pokemon.stats.Stats.SPEED] ?: 0,
            ball = "item.cobblemon.${pokemon.caughtBall.name.path}",
            primaryType = "cobblemon.type.${pokemon.primaryType.name.lowercase()}",
            secondaryType = pokemon.secondaryType?.let { "cobblemon.type.${it.name.lowercase()}" } ?: "",
            source = source,
            slot = slot
        )

    fun openScreen(player: ServerPlayerEntity) {
        ServerPlayNetworking.send(player, OpenMarketPayload(0))
    }

    private fun removeCurrency(player: ServerPlayerEntity, amount: Int) =
        com.shusheng.cobblemarket.config.CurrencyHandler.remove(player, amount)

    private fun giveCurrency(player: ServerPlayerEntity, amount: Int) =
        com.shusheng.cobblemarket.config.CurrencyHandler.give(player, amount.toLong())
}
