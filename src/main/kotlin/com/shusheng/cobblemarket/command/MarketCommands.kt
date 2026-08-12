package com.shusheng.cobblemarket.command

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.storage.party.PartyPosition
import com.cobblemon.mod.common.pokemon.Pokemon
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.shusheng.cobblemarket.market.ListingStatus
import com.shusheng.cobblemarket.market.MarketListing
import com.shusheng.cobblemarket.market.MarketState
import com.shusheng.cobblemarket.network.MarketNetwork
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.util.UUID

private fun currencyName() = com.shusheng.cobblemarket.config.CurrencyHandler.getName()
private const val LISTINGS_PER_PAGE = 10
private const val LISTING_DURATION_DAYS = 7

object MarketCommands {

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                CommandManager.literal("market")
                    .then(CommandManager.literal("sell")
                        .then(CommandManager.argument("slot", IntegerArgumentType.integer(1, 6))
                            .then(CommandManager.argument("price", IntegerArgumentType.integer(1))
                                .executes(::sell)
                            )
                        )
                    )
                    .then(CommandManager.literal("browse")
                        .executes(::browse)
                        .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                            .executes(::browsePage)
                        )
                    )
                    .then(CommandManager.literal("buy")
                        .then(CommandManager.argument("id", StringArgumentType.word())
                            .executes(::buy)
                        )
                    )
                    .then(CommandManager.literal("cancel")
                        .then(CommandManager.argument("id", StringArgumentType.word())
                            .executes(::cancel)
                        )
                    )
                    .then(CommandManager.literal("mylistings")
                        .executes(::myListings)
                    )
                    .then(CommandManager.literal("collect")
                        .executes(::collect)
                    )
                .then(CommandManager.literal("gui")
                        .executes(::openGui)
                    )
            )
        }
    }

    private fun sell(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.playerOrThrow
        val slot = IntegerArgumentType.getInteger(context, "slot") - 1
        val price = IntegerArgumentType.getInteger(context, "price")
        val server = source.server

        val party = Cobblemon.storage.getParty(player)
        val pokemon = party.get(PartyPosition(slot))
        if (pokemon == null) {
            source.sendFeedback(
                { Text.translatable("cobblemarket.cmd.no_pokemon", slot + 1).formatted(Formatting.RED) },
                false
            )
            return 0
        }

        val world = source.world
        val nbt = pokemon.saveToNBT(world.registryManager, NbtCompound())
        val now = System.currentTimeMillis()

        val ballPath = pokemon.caughtBall.name.path
        val extra = mapOf(
            "speciesId" to pokemon.species.resourceIdentifier.toString(),
            "speciesName" to pokemon.species.translatedName.string,
            "primaryType" to "cobblemon.type.${pokemon.primaryType.name.lowercase()}",
            "secondaryType" to (pokemon.secondaryType?.name?.lowercase()?.let { "cobblemon.type.$it" } ?: ""),
            "ivsHp" to pokemon.ivs[com.cobblemon.mod.common.api.pokemon.stats.Stats.HP].toString(),
            "ivsAtk" to pokemon.ivs[com.cobblemon.mod.common.api.pokemon.stats.Stats.ATTACK].toString(),
            "ivsDef" to pokemon.ivs[com.cobblemon.mod.common.api.pokemon.stats.Stats.DEFENCE].toString(),
            "ivsSpAtk" to pokemon.ivs[com.cobblemon.mod.common.api.pokemon.stats.Stats.SPECIAL_ATTACK].toString(),
            "ivsSpDef" to pokemon.ivs[com.cobblemon.mod.common.api.pokemon.stats.Stats.SPECIAL_DEFENCE].toString(),
            "ivsSpd" to pokemon.ivs[com.cobblemon.mod.common.api.pokemon.stats.Stats.SPEED].toString(),
            "nature" to "cobblemon.nature.${pokemon.effectiveNature.name.path}",
            "ability" to "cobblemon.ability.${pokemon.ability.name}",
            "gender" to pokemon.gender.name,
            "ball" to "item.cobblemon.$ballPath",
            "ballItem" to "cobblemon:$ballPath"
        )

        val listing = MarketListing(
            id = UUID.randomUUID(),
            sellerUuid = player.uuid,
            sellerName = player.name.string,
            pokemonNbt = nbt,
            species = pokemon.species.name,
            level = pokemon.level,
            shiny = pokemon.shiny,
            price = price,
            createdAt = now,
            expiresAt = now + LISTING_DURATION_DAYS * 24 * 60 * 60 * 1000L,
            status = ListingStatus.ACTIVE,
            extraData = extra
        )

        val feePercent = com.shusheng.cobblemarket.config.CobbleMarketConfig.listingFeePercent
        val fee = if (feePercent > 0) Math.ceil(price * feePercent / 100.0).toInt() else 0
        if (fee > 0 && !com.shusheng.cobblemarket.config.CurrencyHandler.remove(player, fee)) {
            source.sendFeedback(
                { Text.translatable("cobblemarket.cmd.need_fee", fee, currencyName()).formatted(Formatting.RED) },
                false
            )
            return 0
        }

        party.remove(PartyPosition(slot))
        val state = MarketState.get(server)
        state.addListing(listing)

        if (fee > 0) {
            source.sendFeedback(
                { Text.translatable("cobblemarket.cmd.listed_fee", pokemon.species.translatedName.string, pokemon.level, price, currencyName(), fee, currencyName()) },
                false
            )
        } else {
            source.sendFeedback(
                { Text.translatable("cobblemarket.cmd.listed", pokemon.species.translatedName.string, pokemon.level, price, currencyName()) },
                false
            )
        }
        return 1
    }

    private fun browse(context: CommandContext<ServerCommandSource>): Int =
        showListings(context.source, 1)

    private fun browsePage(context: CommandContext<ServerCommandSource>): Int {
        val page = IntegerArgumentType.getInteger(context, "page")
        return showListings(context.source, page)
    }

    private fun showListings(source: ServerCommandSource, page: Int): Int {
        val state = MarketState.get(source.server)
        state.expireOldListings(source.server, System.currentTimeMillis())
        val all = state.getActiveListings()
        val totalPages = ((all.size - 1) / LISTINGS_PER_PAGE) + 1

        if (all.isEmpty()) {
            source.sendFeedback({ Text.translatable("cobblemarket.cmd.no_listings").formatted(Formatting.YELLOW) }, false)
            return 0
        }

        val clampedPage = page.coerceIn(1, maxOf(1, totalPages))
        val start = (clampedPage - 1) * LISTINGS_PER_PAGE
        val pageItems = all.drop(start).take(LISTINGS_PER_PAGE)

        source.sendFeedback(
            { Text.translatable("cobblemarket.cmd.browse_header", clampedPage, totalPages).formatted(Formatting.GOLD) },
            false
        )
        pageItems.forEach { listing ->
            val shortId = listing.id.toString().take(8)
            val shinyIcon = if (listing.shiny) " ☆" else ""
            source.sendFeedback(
                { Text.literal("$shortId | ${listing.species}$shinyIcon Lv.${listing.level} | ${listing.price} ${currencyName()} | by ${listing.sellerName}") },
                false
            )
        }
        return 1
    }

    private fun buy(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.playerOrThrow
        val input = StringArgumentType.getString(context, "id")
        val server = source.server
        val state = MarketState.get(server)

        val listing = findListing(state, input)
        if (listing == null || !listing.isActive()) {
            source.sendFeedback({ Text.translatable("cobblemarket.cmd.not_found").formatted(Formatting.RED) }, false)
            return 0
        }
        if (listing.sellerUuid == player.uuid) {
            source.sendFeedback({ Text.translatable("cobblemarket.cmd.cannot_buy_own").formatted(Formatting.RED) }, false)
            return 0
        }

        val price = listing.price
        if (!com.shusheng.cobblemarket.config.CurrencyHandler.remove(player, price)) {
            source.sendFeedback(
                { Text.translatable("cobblemarket.cmd.need_diamonds", price, currencyName()).formatted(Formatting.RED) },
                false
            )
            return 0
        }

        val registryLookup = source.world.registryManager
        val pokemon = Pokemon().loadFromNBT(registryLookup, listing.pokemonNbt)

        val party = Cobblemon.storage.getParty(player)
        val added = party.add(pokemon)
        if (!added) {
            com.shusheng.cobblemarket.config.CurrencyHandler.give(player,price)
            source.sendFeedback(
                { Text.translatable("cobblemarket.cmd.party_full").formatted(Formatting.RED) },
                false
            )
            return 0
        }

        state.addPendingBalance(listing.sellerUuid, price)
        listing.status = ListingStatus.SOLD
        state.markModified()

        source.sendFeedback(
            { Text.translatable("cobblemarket.cmd.bought", listing.species, price, currencyName()).formatted(Formatting.GREEN) },
            false
        )

        val seller = server.playerManager.getPlayer(listing.sellerUuid)
        seller?.sendMessage(
            Text.translatable("cobblemarket.cmd.sold", listing.species, price, currencyName()),
            false
        )

        return 1
    }

    private fun cancel(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.playerOrThrow
        val input = StringArgumentType.getString(context, "id")
        val server = source.server
        val state = MarketState.get(server)

        val listing = findListing(state, input)
        if (listing == null || !listing.isActive()) {
            source.sendFeedback({ Text.translatable("cobblemarket.cmd.not_found").formatted(Formatting.RED) }, false)
            return 0
        }
        if (listing.sellerUuid != player.uuid) {
            source.sendFeedback({ Text.translatable("cobblemarket.cmd.not_your_listing").formatted(Formatting.RED) }, false)
            return 0
        }

        val registryLookup = source.world.registryManager
        val pokemon = Pokemon().loadFromNBT(registryLookup, listing.pokemonNbt)
        val party = Cobblemon.storage.getParty(player)
        if (!party.add(pokemon)) {
            source.sendFeedback(
                { Text.translatable("cobblemarket.cmd.party_full_cancel").formatted(Formatting.RED) },
                false
            )
            return 0
        }

        listing.status = ListingStatus.CANCELLED
        state.markModified()

        source.sendFeedback(
            { Text.translatable("cobblemarket.cmd.cancelled", listing.species).formatted(Formatting.YELLOW) },
            false
        )
        return 1
    }

    private fun myListings(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.playerOrThrow
        val state = MarketState.get(source.server)

        val myActive = state.getListingsBySeller(player.uuid).filter { it.isActive() }
        if (myActive.isEmpty()) {
            source.sendFeedback({ Text.translatable("cobblemarket.cmd.no_active").formatted(Formatting.YELLOW) }, false)
            return 0
        }

        source.sendFeedback(
            { Text.translatable("cobblemarket.cmd.your_listings").formatted(Formatting.GOLD) },
            false
        )
        myActive.forEach { listing ->
            val shortId = listing.id.toString().take(8)
            source.sendFeedback(
                { Text.literal("$shortId | ${listing.species} Lv.${listing.level} | ${listing.price} ${currencyName()}") },
                false
            )
        }
        return 1
    }

    private fun collect(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.playerOrThrow
        val state = MarketState.get(source.server)

        val amount = state.claimPendingBalance(player.uuid)
        if (amount == 0) {
            source.sendFeedback({ Text.translatable("cobblemarket.cmd.no_earnings").formatted(Formatting.YELLOW) }, false)
            return 0
        }

        com.shusheng.cobblemarket.config.CurrencyHandler.give(player,amount)
        source.sendFeedback(
            { Text.translatable("cobblemarket.cmd.collected", amount, currencyName()).formatted(Formatting.GREEN) },
            false
        )
        return 1
    }

    private fun findListing(state: MarketState, input: String): MarketListing? {
        val uuid = try {
            UUID.fromString(input)
        } catch (_: Exception) {
            null
        }
        if (uuid != null) return state.getListing(uuid)
        return state.getAllListings().find { it.id.toString().startsWith(input, ignoreCase = true) }
    }

    private fun openGui(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.playerOrThrow
        MarketNetwork.openScreen(player)
        return 1
    }

}
