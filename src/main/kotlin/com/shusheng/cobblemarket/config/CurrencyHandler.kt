package com.shusheng.cobblemarket.config

import com.shusheng.cobblemarket.CobbleMarket
import fr.harmex.cobbledollars.common.utils.CobbleDollarsPlayer
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import java.math.BigInteger

object CurrencyHandler {
    private var useCobbleDollars = false
    private var currencyItem: Item = Registries.ITEM.get(Identifier.of("minecraft", "diamond"))

    fun load(config: CobbleMarketConfig) {
        useCobbleDollars = config.cobbledollars
        CobbleMarket.LOGGER.info("Currency: ${if (useCobbleDollars) "CobbleDollars" else config.currencyItem}")
        if (!useCobbleDollars) {
            val id = Identifier.tryParse(config.currencyItem) ?: Identifier.of("minecraft", "diamond")
            currencyItem = Registries.ITEM.get(id)
        }
    }

    fun getBalance(player: ServerPlayerEntity): BigInteger {
        if (useCobbleDollars) {
            return try {
                (player as CobbleDollarsPlayer).`cobbleDollars$getCobbleDollars`()
            } catch (e: Exception) { BigInteger.ZERO }
        }
        var total = 0
        val inv = player.inventory
        for (i in 0 until inv.size()) {
            if (inv.getStack(i).isOf(currencyItem)) total += inv.getStack(i).count
        }
        return BigInteger.valueOf(total.toLong())
    }

    fun remove(player: ServerPlayerEntity, amount: Int): Boolean {
        if (amount <= 0) return false
        if (useCobbleDollars) {
            return try {
                val p = player as CobbleDollarsPlayer
                val bal = p.`cobbleDollars$getCobbleDollars`()
                val amt = BigInteger.valueOf(amount.toLong())
                if (bal < amt) return false
                p.`cobbleDollars$setCobbleDollars`(bal.subtract(amt))
                true
            } catch (e: Exception) { false }
        }
        val inv = player.inventory
        var total = 0
        for (i in 0 until inv.size()) {
            if (inv.getStack(i).isOf(currencyItem)) total += inv.getStack(i).count
        }
        if (total < amount) return false
        var remaining = amount
        for (i in 0 until inv.size()) {
            val stack = inv.getStack(i)
            if (stack.isOf(currencyItem)) {
                val r = minOf(remaining, stack.count)
                stack.decrement(r)
                remaining -= r
                if (remaining <= 0) break
            }
        }
        return true
    }

    fun give(player: ServerPlayerEntity, amount: Long) {
        if (amount <= 0L) return
        if (useCobbleDollars) {
            try {
                val p = player as CobbleDollarsPlayer
                val bal = p.`cobbleDollars$getCobbleDollars`()
                p.`cobbleDollars$setCobbleDollars`(bal.add(BigInteger.valueOf(amount)))
            } catch (_: Exception) {}
            return
        }
        var remaining = amount
        while (remaining > 0L) {
            val chunk = minOf(remaining, Int.MAX_VALUE.toLong()).toInt()
            val stack = ItemStack(currencyItem, chunk)
            if (!player.inventory.insertStack(stack)) {
                player.dropItem(stack, false)
            }
            remaining -= chunk
        }
    }

    fun getName(): String {
        return if (useCobbleDollars) "PokéDollars" else currencyItem.name.string
    }
}
