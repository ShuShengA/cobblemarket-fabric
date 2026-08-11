package com.shusheng.cobblemarket.client

import com.shusheng.cobblemarket.CobbleMarket
import com.shusheng.cobblemarket.network.MarketDataPayload
import com.shusheng.cobblemarket.network.MarketResultPayload
import com.shusheng.cobblemarket.network.OpenMarketPayload
import com.shusheng.cobblemarket.screen.MarketScreen
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import org.slf4j.LoggerFactory

object CobbleMarketClient : ClientModInitializer {

    val LOGGER = LoggerFactory.getLogger(CobbleMarket.MOD_ID)

    override fun onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(OpenMarketPayload.ID) { _, _ ->
            val client = MinecraftClient.getInstance()
            client.execute {
                client.setScreen(MarketScreen())
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(MarketDataPayload.ID) { payload, _ ->
            val client = MinecraftClient.getInstance()
            client.execute {
                val screen = client.currentScreen
                if (screen is MarketScreen) {
                    screen.onMarketData(payload)
                }
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(MarketResultPayload.ID) { payload, _ ->
            val client = MinecraftClient.getInstance()
            client.execute {
                val screen = client.currentScreen
                if (screen is MarketScreen) {
                    screen.onMarketResult(payload)
                }
            }
        }
    }
}
