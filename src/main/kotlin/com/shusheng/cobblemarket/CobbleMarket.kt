package com.shusheng.cobblemarket

import com.shusheng.cobblemarket.command.MarketCommands
import com.shusheng.cobblemarket.event.TransactionHistory
import com.shusheng.cobblemarket.market.ItemMarketState
import com.shusheng.cobblemarket.market.MarketState
import com.shusheng.cobblemarket.network.MarketNetwork
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory

object CobbleMarket : ModInitializer {
	const val MOD_ID: String = "cobblemarket"

	val LOGGER = LoggerFactory.getLogger(MOD_ID)

	override fun onInitialize() {
		LOGGER.info("CobbleMarket initializing...")
		com.shusheng.cobblemarket.config.CobbleMarketConfig.load()
		MarketNetwork.register()
		MarketCommands.register()
		com.shusheng.cobblemarket.event.TransactionLogger.register()
		TransactionHistory.register()

		ServerLifecycleEvents.SERVER_STARTED.register { server ->
			TransactionHistory.historyRef = TransactionHistory.get(server)
		}
		ServerLifecycleEvents.SERVER_STOPPING.register {
			TransactionHistory.historyRef = null
		}

		ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
			val player = handler.player
			val state = MarketState.get(player.server)
			val stateServer = player.server

			stateServer.execute {
				val balance = state.claimPendingBalance(player.uuid)
				if (balance > 0) {
					com.shusheng.cobblemarket.config.CurrencyHandler.give(player, balance)
					player.sendMessage(
						Text.translatable("cobblemarket.cmd.login_earnings", balance, com.shusheng.cobblemarket.config.CurrencyHandler.getName())
							.formatted(Formatting.GREEN),
						false
					)
				}
			}
		}

		LOGGER.info("CobbleMarket ready!")
	}

	fun id(path: String): Identifier = Identifier.of(MOD_ID, path)
}
