package com.shusheng.cobblemarket.client

import com.shusheng.cobblemarket.CobbleMarket
import com.shusheng.cobblemarket.network.HistoryDataPayload
import com.shusheng.cobblemarket.network.MarketDataPayload
import com.shusheng.cobblemarket.network.MarketResultPayload
import com.shusheng.cobblemarket.network.MyPokemonListPayload
import com.shusheng.cobblemarket.network.OpenMarketPayload
import com.shusheng.cobblemarket.screen.HistoryScreen
import com.shusheng.cobblemarket.screen.MarketEntryScreen
import com.shusheng.cobblemarket.screen.MarketScreen
import com.shusheng.cobblemarket.screen.SellSelectScreen
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory

object CobbleMarketClient : ClientModInitializer {

    val LOGGER = LoggerFactory.getLogger(CobbleMarket.MOD_ID)

    private lateinit var openMarketKey: KeyBinding

    override fun onInitializeClient() {
        openMarketKey = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.cobblemarket.open_market",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "category.cobblemarket"
            )
        )

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (openMarketKey.wasPressed()) {
                client.setScreen(MarketEntryScreen())
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(OpenMarketPayload.ID) { _, _ ->
            val client = MinecraftClient.getInstance()
            client.execute {
                client.setScreen(MarketEntryScreen())
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

        ClientPlayNetworking.registerGlobalReceiver(MyPokemonListPayload.ID) { payload, _ ->
            val client = MinecraftClient.getInstance()
            client.execute {
                val screen = client.currentScreen
                if (screen is SellSelectScreen) {
                    screen.onPokemonList(payload)
                }
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(HistoryDataPayload.ID) { payload, _ ->
            val client = MinecraftClient.getInstance()
            client.execute {
                val screen = client.currentScreen
                if (screen is HistoryScreen) {
                    screen.onHistoryData(payload)
                }
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(MarketResultPayload.ID) { payload, _ ->
            val client = MinecraftClient.getInstance()
            client.execute {
                val screen = client.currentScreen
                when (screen) {
                    is MarketScreen -> screen.onMarketResult(payload)
                    is SellSelectScreen -> screen.onMarketResult(payload)
                }
            }
        }
    }
}
