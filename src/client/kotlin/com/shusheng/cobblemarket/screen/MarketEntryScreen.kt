package com.shusheng.cobblemarket.screen

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier

class MarketEntryScreen : Screen(Text.translatable("cobblemarket.entry.title")) {

    private var btnStartY = 0
    private var totalH = 0

    override fun init() {
        super.init()
        val centerX = width / 2
        val btnW = 160
        val btnH = 24
        val gap = 8

        val isAdmin = client?.player?.hasPermissionLevel(2) == true
        val buttonCount = if (isAdmin) 4 else 3
        totalH = btnH * buttonCount + gap * (buttonCount - 1)
        val startY = height / 2 - totalH / 2
        btnStartY = startY

        addDrawableChild(TextureButton(
            centerX - btnW / 2, startY, btnW, btnH,
            Text.translatable("cobblemarket.entry.pokemon"),
            { client?.setScreen(MarketScreen()) }
        ))

        addDrawableChild(TextureButton(
            centerX - btnW / 2, startY + btnH + gap, btnW, btnH,
            Text.translatable("cobblemarket.entry.item"),
            { openItemMarket() }
        ))

        addDrawableChild(TextureButton(
            centerX - btnW / 2, startY + (btnH + gap) * 2, btnW, btnH,
            Text.translatable("cobblemarket.entry.history"),
            { client?.setScreen(HistoryScreen()) }
        ))

        if (isAdmin) {
            addDrawableChild(TextureButton(
                centerX - btnW / 2, startY + (btnH + gap) * 3, btnW, btnH,
                Text.translatable("cobblemarket.entry.op"),
                { client?.setScreen(AdminScreen()) }
            ))
        }
    }

    private fun openItemMarket() {
        client?.setScreen(ItemMarketScreen())
    }

    override fun renderBackground(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val bg = Identifier.of("cobblemarket", "textures/gui/market_entry_background.png")
        // Center 160-tall background on content (title + buttons)
        val contentTop = btnStartY - 14
        val contentBottom = btnStartY + totalH
        val contentHeight = contentBottom - contentTop
        val bgTop = contentTop - (160 - contentHeight) / 2
        context.drawTexture(bg, width / 2 - 96, bgTop, 0f, 0f, 192, 160, 192, 160)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable("cobblemarket.entry.title").formatted(Formatting.GOLD),
            width / 2, btnStartY - 14, 0xFFFFFF
        )
    }

    override fun shouldPause() = false
}
