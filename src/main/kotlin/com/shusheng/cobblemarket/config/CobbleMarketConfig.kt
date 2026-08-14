package com.shusheng.cobblemarket.config

import com.google.gson.GsonBuilder
import com.shusheng.cobblemarket.CobbleMarket
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import java.io.File

object CobbleMarketConfig {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val configFile: File
        get() = FabricLoader.getInstance().configDir.resolve("cobblemarket.json").toFile()

    var cobbledollars: Boolean = false
        private set
    var currencyItem: String = "minecraft:diamond"
        private set
    var pokemonListingFeePercent: Double = 5.0
        private set
    var itemListingFeePercent: Double = 5.0
        private set
    var maxPokemonListingsPerPlayer: Int = 0
        private set
    var maxItemListingsPerPlayer: Int = 0
        private set
    var listingDurationDays: Int = 7
        private set

    fun load() {
        val hasCD = try { Class.forName("fr.harmex.cobbledollars.common.utils.CobbleDollarsPlayer"); true } catch (_: Exception) { false }
        if (!configFile.exists()) {
            cobbledollars = hasCD
            save()
        } else {
            try {
                val json = configFile.readText()
                val data = gson.fromJson(json, Map::class.java) as? Map<*, *> ?: emptyMap<Any?, Any?>()
                val currency = data["currency"] as? Map<*, *>
                if (currency != null) {
                    cobbledollars = (currency["cobbledollars"] as? Boolean ?: hasCD) && hasCD
                    currencyItem = currency["item"] as? String ?: "minecraft:diamond"
                }
                val legacyFee = data["listingFeePercent"] as? Double
                pokemonListingFeePercent = (data["pokemonListingFeePercent"] as? Double) ?: legacyFee ?: 5.0
                itemListingFeePercent = (data["itemListingFeePercent"] as? Double) ?: legacyFee ?: 5.0
                maxPokemonListingsPerPlayer = (data["maxPokemonListingsPerPlayer"] as? Double)?.toInt() ?: 0
                maxItemListingsPerPlayer = (data["maxItemListingsPerPlayer"] as? Double)?.toInt() ?: 0
                listingDurationDays = (data["listingDurationDays"] as? Double)?.toInt() ?: 7
            } catch (e: Exception) {
                CobbleMarket.LOGGER.warn("Failed to load config: ${e.message}")
                save()
            }
        }
        CurrencyHandler.load(this)
    }

    fun save() {
        val data = mapOf(
            "_comments" to mapOf(
                "currency.cobbledollars" to "是否使用 CobbleDollars 货币（true/false）/ Whether to use CobbleDollars currency (true/false)",
                "currency.item" to "货币物品 ID（cobbledollars=false 时生效）/ Currency item ID (used when cobbledollars=false)",
                "pokemonListingFeePercent" to "精灵市场上架手续费百分比（0=免手续费）/ Pokemon listing fee percentage (0=no fee)",
                "itemListingFeePercent" to "物品市场上架手续费百分比（0=免手续费）/ Item listing fee percentage (0=no fee)",
                "maxPokemonListingsPerPlayer" to "每个玩家同时活跃的精灵上架数量上限（0=不限制）/ Max active Pokemon listings per player (0=unlimited)",
                "maxItemListingsPerPlayer" to "每个玩家同时活跃的物品上架数量上限（0=不限制）/ Max active item listings per player (0=unlimited)",
                "listingDurationDays" to "上架过期天数 / Listing duration in days"
            ),
            "currency" to mapOf("cobbledollars" to cobbledollars, "item" to currencyItem),
            "pokemonListingFeePercent" to pokemonListingFeePercent,
            "itemListingFeePercent" to itemListingFeePercent,
            "maxPokemonListingsPerPlayer" to maxPokemonListingsPerPlayer,
            "maxItemListingsPerPlayer" to maxItemListingsPerPlayer,
            "listingDurationDays" to listingDurationDays
        )
        configFile.writeText(gson.toJson(data))
    }

    fun getCurrencyItem(): Item {
        val id = Identifier.tryParse(currencyItem) ?: Identifier.of("minecraft", "diamond")
        return Registries.ITEM.get(id)
    }

    fun getCurrencyName(): String {
        return getCurrencyItem().name.string
    }
}
