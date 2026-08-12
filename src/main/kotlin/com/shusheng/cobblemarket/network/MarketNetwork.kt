package com.shusheng.cobblemarket.network

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.storage.party.PartyPosition
import com.shusheng.cobblemarket.CobbleMarket
import com.shusheng.cobblemarket.market.ListingStatus
import com.shusheng.cobblemarket.market.MarketListing
import com.shusheng.cobblemarket.market.MarketState
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.item.Items
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
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
    val ivsHp: Int, val ivsAtk: Int, val ivsDef: Int,
    val ivsSpAtk: Int, val ivsSpDef: Int, val ivsSpd: Int,
    val nature: String,
    val ability: String,
    val gender: String,
    val ball: String,
    val ballItem: String
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
        buf.writeInt(ivsHp); buf.writeInt(ivsAtk); buf.writeInt(ivsDef)
        buf.writeInt(ivsSpAtk); buf.writeInt(ivsSpDef); buf.writeInt(ivsSpd)
        buf.writeString(nature)
        buf.writeString(ability)
        buf.writeString(gender)
        buf.writeString(ball)
        buf.writeString(ballItem)
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
            ivsHp = buf.readInt(), ivsAtk = buf.readInt(), ivsDef = buf.readInt(),
            ivsSpAtk = buf.readInt(), ivsSpDef = buf.readInt(), ivsSpd = buf.readInt(),
            nature = buf.readString(),
            ability = buf.readString(),
            gender = buf.readString(),
            ball = buf.readString(),
            ballItem = buf.readString()
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
    val minIvsSpd: Int
) : CustomPayload {
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID

    companion object {
        val ID = CustomPayload.Id<RequestMarketPayload>(CobbleMarket.id("request_market"))
        val CODEC: PacketCodec<PacketByteBuf, RequestMarketPayload> = PacketCodec.of(
            { p, b -> b.writeString(p.speciesFilter); b.writeBoolean(p.shinyOnly); b.writeInt(p.minLevel); b.writeInt(p.maxLevel); b.writeString(p.sortMode); b.writeInt(p.page); b.writeString(p.genderFilter); b.writeString(p.typeFilter); b.writeInt(p.minIvsHp); b.writeInt(p.minIvsAtk); b.writeInt(p.minIvsDef); b.writeInt(p.minIvsSpAtk); b.writeInt(p.minIvsSpDef); b.writeInt(p.minIvsSpd) },
            { b -> RequestMarketPayload(b.readString(), b.readBoolean(), b.readInt(), b.readInt(), b.readString(), b.readInt(), b.readString(), b.readString(), b.readInt(), b.readInt(), b.readInt(), b.readInt(), b.readInt(), b.readInt()) }
        )
    }
}

// ── S2C: Market data response ──

data class MarketDataPayload(
    val entries: List<ListingEntry>,
    val totalPages: Int,
    val currentPage: Int
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
            },
            { b ->
                val size = b.readVarInt()
                val entries = (0 until size).map { ListingEntry.read(b) }
                MarketDataPayload(entries, b.readInt(), b.readInt())
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
    val message: String
) : CustomPayload {
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID

    companion object {
        val ID = CustomPayload.Id<MarketResultPayload>(CobbleMarket.id("market_result"))
        val CODEC: PacketCodec<PacketByteBuf, MarketResultPayload> = PacketCodec.of(
            { p, b -> b.writeBoolean(p.success); b.writeString(p.message) },
            { b -> MarketResultPayload(b.readBoolean(), b.readString()) }
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

// ── Registration ──

private const val LISTINGS_PER_PAGE = 10
private const val LISTING_DURATION_DAYS = 7

object MarketNetwork {

    fun register() {
        PayloadTypeRegistry.playC2S().register(RequestMarketPayload.ID, RequestMarketPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(BuyFromMarketPayload.ID, BuyFromMarketPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(CancelFromMarketPayload.ID, CancelFromMarketPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(OpenMarketPayload.ID, OpenMarketPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(MarketDataPayload.ID, MarketDataPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(MarketResultPayload.ID, MarketResultPayload.CODEC)

        ServerPlayNetworking.registerGlobalReceiver(RequestMarketPayload.ID) { payload, context ->
            val player = context.player()
            val server = player.server
            val state = MarketState.get(server)
            state.expireOldListings(server, System.currentTimeMillis())

            val sortMode = com.shusheng.cobblemarket.market.SortMode.valueOf(payload.sortMode)
            val results = state.search(
                species = payload.speciesFilter.ifBlank { null },
                shiny = if (payload.shinyOnly) true else null,
                minLevel = if (payload.minLevel > 0) payload.minLevel else null,
                maxLevel = if (payload.maxLevel < 100) payload.maxLevel else null,
                sortBy = sortMode,
                gender = payload.genderFilter.ifBlank { null },
                typeFilter = payload.typeFilter.ifBlank { null },
                minIvs = buildMap {
                    if (payload.minIvsHp > 0) put("ivsHp", payload.minIvsHp)
                    if (payload.minIvsAtk > 0) put("ivsAtk", payload.minIvsAtk)
                    if (payload.minIvsDef > 0) put("ivsDef", payload.minIvsDef)
                    if (payload.minIvsSpAtk > 0) put("ivsSpAtk", payload.minIvsSpAtk)
                    if (payload.minIvsSpDef > 0) put("ivsSpDef", payload.minIvsSpDef)
                    if (payload.minIvsSpd > 0) put("ivsSpd", payload.minIvsSpd)
                }
            )

            val totalPages = ((results.size - 1) / LISTINGS_PER_PAGE) + 1
            val clampedPage = payload.page.coerceIn(1, maxOf(1, totalPages))

            val pageEntries = if (results.isEmpty()) emptyList() else {
                val start = (clampedPage - 1) * LISTINGS_PER_PAGE
                results.drop(start).take(LISTINGS_PER_PAGE).map { listing ->
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
                        ballItem = detail["ballItem"] ?: "cobblemon:poke_ball"
                    )
                }
            }

            ServerPlayNetworking.send(player, MarketDataPayload(pageEntries, maxOf(1, totalPages), clampedPage))
        }

        ServerPlayNetworking.registerGlobalReceiver(BuyFromMarketPayload.ID) { payload, context ->
            val player = context.player()
            val server = player.server
            server.execute {
                val state = MarketState.get(server)
                val listing = state.getListing(payload.listingId)

                if (listing == null || !listing.isActive()) {
                    ServerPlayNetworking.send(player, MarketResultPayload(false, Text.translatable("cobblemarket.network.not_found").string))
                    return@execute
                }
                if (listing.sellerUuid == player.uuid) {
                    ServerPlayNetworking.send(player, MarketResultPayload(false, Text.translatable("cobblemarket.network.cannot_buy_own").string))
                    return@execute
                }
                if (!removeCurrency(player, listing.price)) {
                    ServerPlayNetworking.send(player, MarketResultPayload(false, Text.translatable("cobblemarket.network.need_diamonds", listing.price).string))
                    return@execute
                }

                val registryLookup = player.serverWorld.registryManager
                val pokemon = com.cobblemon.mod.common.pokemon.Pokemon().loadFromNBT(registryLookup, listing.pokemonNbt)
                val party = Cobblemon.storage.getParty(player)
                if (!party.add(pokemon)) {
                    giveCurrency(player, listing.price)
                    ServerPlayNetworking.send(player, MarketResultPayload(false, Text.translatable("cobblemarket.network.party_full").string))
                    return@execute
                }

                state.addPendingBalance(listing.sellerUuid, listing.price)
                listing.status = ListingStatus.SOLD
                state.markModified()

                ServerPlayNetworking.send(player, MarketResultPayload(true, Text.translatable("cobblemarket.network.bought", listing.species, listing.price).string))

                val seller = server.playerManager.getPlayer(listing.sellerUuid)
                seller?.sendMessage(Text.translatable("cobblemarket.network.sold", listing.species), false)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(CancelFromMarketPayload.ID) { payload, context ->
            val player = context.player()
            val server = player.server
            server.execute {
                val state = MarketState.get(server)
                val listing = state.getListing(payload.listingId)

                if (listing == null || !listing.isActive()) {
                    ServerPlayNetworking.send(player, MarketResultPayload(false, Text.translatable("cobblemarket.network.not_found").string))
                    return@execute
                }
                if (listing.sellerUuid != player.uuid) {
                    ServerPlayNetworking.send(player, MarketResultPayload(false, Text.translatable("cobblemarket.network.cannot_buy_own").string))
                    return@execute
                }

                val pokemon = com.cobblemon.mod.common.pokemon.Pokemon()
                    .loadFromNBT(player.serverWorld.registryManager, listing.pokemonNbt)
                val party = Cobblemon.storage.getParty(player)
                if (!party.add(pokemon)) {
                    ServerPlayNetworking.send(player, MarketResultPayload(false, Text.translatable("cobblemarket.network.party_full").string))
                    return@execute
                }

                listing.status = ListingStatus.CANCELLED
                state.markModified()
                ServerPlayNetworking.send(player, MarketResultPayload(true, Text.translatable("cobblemarket.cmd.cancelled", listing.species).string))
            }
        }
    }

    fun openScreen(player: ServerPlayerEntity) {
        ServerPlayNetworking.send(player, OpenMarketPayload(0))
    }

    private fun removeCurrency(player: ServerPlayerEntity, amount: Int): Boolean {
        val item = com.shusheng.cobblemarket.config.CobbleMarketConfig.getCurrencyItem()
        val inv = player.inventory
        var total = 0
        for (i in 0 until inv.size()) {
            if (inv.getStack(i).isOf(item)) total += inv.getStack(i).count
        }
        if (total < amount) return false
        var remaining = amount
        for (i in 0 until inv.size()) {
            val stack = inv.getStack(i)
            if (stack.isOf(item)) {
                val r = minOf(remaining, stack.count)
                stack.decrement(r)
                remaining -= r
                if (remaining <= 0) break
            }
        }
        return true
    }

    private fun giveCurrency(player: ServerPlayerEntity, amount: Int) {
        val stack = ItemStack(com.shusheng.cobblemarket.config.CobbleMarketConfig.getCurrencyItem(), amount)
        if (!player.inventory.insertStack(stack)) {
            player.dropItem(stack, false)
        }
    }
}
