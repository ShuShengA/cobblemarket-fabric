package com.shusheng.cobblemarket.screen

import com.shusheng.cobblemarket.network.AdminBanPayload
import com.shusheng.cobblemarket.network.AdminUnbanPayload
import com.shusheng.cobblemarket.network.BanEntry
import com.shusheng.cobblemarket.network.BanListDataPayload
import com.shusheng.cobblemarket.network.MarketResultPayload
import com.shusheng.cobblemarket.network.RequestBanListPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Drawable
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier

class AdminBanScreen : Screen(Text.translatable("cobblemarket.ban.title")) {

    private val panelWidth = 300
    private val rowHeight = 22
    private val listStartY = 68

    private var nameField: TextFieldWidget? = null
    private var durationField: TextFieldWidget? = null
    private var reasonField: TextFieldWidget? = null
    private var banButton: NineSliceButton? = null
    private var backButton: NineSliceButton? = null
    private var pendingBanName = ""
    private var pendingBanDuration = ""
    private var bans = listOf<BanEntry>()
    private var scrollOffset = 0
    private var hoveredRow = -1
    private val unbanButtons = mutableListOf<NineSliceButton>()

    private fun maxVisible() = maxOf(0, (height - listStartY - 16) / rowHeight)

    override fun init() {
        super.init()
        val leftX = width / 2 - panelWidth / 2

        val backBtn = NineSliceButton(
            leftX + panelWidth - 50, 13, 50, 16,
            Text.translatable("cobblemarket.gui.back"),
            { client?.setScreen(AdminScreen()) }
        )
        backButton = backBtn
        addDrawableChild(backBtn)

        nameField = TextFieldWidget(textRenderer, leftX + 2, 44, 120, 16, Text.literal(""))
        nameField?.setPlaceholder(Text.translatable("cobblemarket.ban.player_name"))
        addSelectableChild(nameField)
        addDrawableChild(nameField)

        durationField = TextFieldWidget(textRenderer, leftX + 126, 44, 90, 16, Text.literal(""))
        durationField?.setPlaceholder(Text.translatable("cobblemarket.ban.duration"))
        addSelectableChild(durationField)
        addDrawableChild(durationField)

        val banBtn = NineSliceButton(
            leftX + 220, 44, 76, 16,
            Text.translatable("cobblemarket.ban.ban"),
            { doBan() }
        )
        banButton = banBtn
        addDrawableChild(banBtn)

        scrollOffset = 0
        ClientPlayNetworking.send(RequestBanListPayload())
    }

    private fun doBan() {
        val name = nameField?.text?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val duration = durationField?.text?.trim() ?: ""
        openBanConfirmDialog(name, duration)
    }

    private fun openBanConfirmDialog(name: String, duration: String) {
        pendingBanName = name
        pendingBanDuration = duration
        nameField?.visible = false
        durationField?.visible = false
        banButton?.visible = false
        backButton?.visible = false
        unbanButtons.forEach { it.visible = false }
        val centerX = width / 2
        val dialogY = height / 2 - 75

        addDrawable(object : Drawable {
            override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
                renderBanDialogBackground(context)
            }
        })

        reasonField = TextFieldWidget(textRenderer, centerX - 80, dialogY + 74, 160, 16, Text.literal(""))
        reasonField?.setPlaceholder(Text.translatable("cobblemarket.ban.reason"))
        reasonField?.setTextPredicate { it.length <= 100 }
        addDrawableChild(reasonField)

        addDrawableChild(NineSliceButton(
            centerX - 85, dialogY + 108, 80, 20,
            Text.translatable("cobblemarket.ban.confirm"),
            { confirmBan() }
        ))
        addDrawableChild(NineSliceButton(
            centerX + 5, dialogY + 108, 80, 20,
            Text.translatable("cobblemarket.buy_confirm.cancel"),
            { closeBanConfirmDialog() }
        ))
    }

    private fun renderBanDialogBackground(context: DrawContext) {
        val centerX = width / 2
        val dialogW = 240
        val dialogH = 150
        val dialogX = centerX - dialogW / 2
        val dialogY = height / 2 - dialogH / 2

        context.fill(0, 0, width, height, 0xC0000000.toInt())
        drawNineSlice(context, DIALOG_BACKGROUND_TEXTURE, dialogX, dialogY, dialogW, dialogH, 0, DIALOG_BACKGROUND_TEX_H)

        context.drawCenteredTextWithShadow(textRenderer,
            Text.translatable("cobblemarket.ban.confirm_title").formatted(Formatting.GOLD),
            centerX, dialogY + 14, 0xFFFFFF)

        context.drawTextWithShadow(textRenderer,
            Text.translatable("cobblemarket.ban.target").string + pendingBanName,
            dialogX + 12, dialogY + 40, 0xFFFFFF)

        context.drawTextWithShadow(textRenderer,
            Text.translatable("cobblemarket.ban.by").string + (client?.player?.name?.string ?: ""),
            dialogX + 12, dialogY + 58, 0xFFFFFF)
    }

    private fun confirmBan() {
        val reason = reasonField?.text?.trim() ?: ""
        ClientPlayNetworking.send(AdminBanPayload(pendingBanName, pendingBanDuration, reason))
        closeBanConfirmDialog()
        nameField?.text = ""
        durationField?.text = ""
    }

    private fun closeBanConfirmDialog() {
        pendingBanName = ""
        pendingBanDuration = ""
        reasonField = null
        clearChildren()
        init()
    }

    private fun doUnban(entry: BanEntry) {
        ClientPlayNetworking.send(AdminUnbanPayload(entry.playerUuid))
    }

    fun onBanList(payload: BanListDataPayload) {
        bans = payload.entries
        scrollOffset = scrollOffset.coerceIn(0, maxOf(0, bans.size - maxVisible()))
        rebuildUnbanButtons()
    }

    fun onResult(payload: MarketResultPayload) {
        client?.player?.sendMessage(payload.message, false)
        ClientPlayNetworking.send(RequestBanListPayload())
    }

    private fun rebuildUnbanButtons() {
        unbanButtons.forEach { remove(it) }
        unbanButtons.clear()
        val leftX = width / 2 - panelWidth / 2
        bans.drop(scrollOffset).take(maxVisible()).forEachIndexed { i, entry ->
            val y = listStartY + i * rowHeight
            val btn = NineSliceButton(
                leftX + panelWidth - 60, y + 3, 56, 16,
                Text.translatable("cobblemarket.ban.unban"),
                { doUnban(entry) }
            )
            btn.visible = pendingBanName.isEmpty()
            unbanButtons.add(btn)
            addDrawableChild(btn)
        }
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)

        if (pendingBanName.isNotEmpty()) {
            return
        }

        val centerX = width / 2
        val leftX = centerX - panelWidth / 2

        context.drawCenteredTextWithShadow(textRenderer,
            Text.translatable("cobblemarket.ban.title").formatted(Formatting.GOLD),
            centerX, 20, 0xFFFFFF)

        context.fill(leftX, listStartY - 4, leftX + panelWidth, listStartY - 3, 0xFF555555.toInt())

        if (bans.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("cobblemarket.ban.banlist_empty").formatted(Formatting.GRAY),
                centerX, listStartY + 30, 0xFFFFFF)
            return
        }

        bans.drop(scrollOffset).take(maxVisible()).forEachIndexed { i, entry ->
            val y = listStartY + i * rowHeight
            context.drawTextWithShadow(textRenderer,
                "${entry.playerName}  ·  ${entry.bannedBy}  ·  ${entry.durationDisplay}",
                leftX + 4, y + 5, 0xFFFFFF)
        }

        if (bans.size > maxVisible()) {
            context.drawCenteredTextWithShadow(textRenderer,
                "${scrollOffset + 1}-${minOf(scrollOffset + maxVisible(), bans.size)} / ${bans.size}",
                width / 2, height - 49, 0x888888)
        }

        if (hoveredRow >= 0) {
            val actualIdx = scrollOffset + hoveredRow
            if (actualIdx in bans.indices) {
                renderBanTooltip(context, bans[actualIdx], mouseX, mouseY)
            }
        }
    }

    private fun renderBanTooltip(context: DrawContext, entry: BanEntry, mouseX: Int, mouseY: Int) {
        val lines = listOf(
            "${Text.translatable("cobblemarket.ban.target").string} ${entry.playerName}",
            "${Text.translatable("cobblemarket.ban.by").string} ${entry.bannedBy}",
            "${Text.translatable("cobblemarket.ban.reason_label").string} ${entry.reason.ifBlank { "-" }}"
        )

        var maxWidth = 0
        lines.forEach { maxWidth = maxOf(maxWidth, textRenderer.getWidth(it)) }

        val padding = 4
        val tx = minOf(mouseX + 12, width - maxWidth - 12)
        val tooltipHeight = lines.size * 10 + padding
        val tyAbove = mouseY - tooltipHeight - 4
        val ty = if (tyAbove <= 0) minOf(mouseY + 12, height - tooltipHeight) else tyAbove

        context.matrices.push()
        context.matrices.translate(0.0, 0.0, 400.0)
        drawNineSlice(context, ROW_BACKGROUND_TEXTURE, tx - padding, ty - padding, maxWidth + 2 * padding, lines.size * 10 + 2 * padding, 1, ROW_BACKGROUND_TEX_H)
        lines.forEachIndexed { i, line ->
            context.drawTextWithShadow(textRenderer, line, tx, ty + i * 10, 0xFFFFFF)
        }
        context.matrices.pop()
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        scrollOffset = (scrollOffset - verticalAmount.toInt()).coerceIn(0, maxOf(0, bans.size - maxVisible()))
        rebuildUnbanButtons()
        return true
    }

    override fun resize(client: MinecraftClient, width: Int, height: Int) {
        val name = pendingBanName
        val duration = pendingBanDuration
        val reason = reasonField?.text ?: ""
        super.resize(client, width, height)
        if (name.isNotEmpty()) {
            pendingBanName = ""
            pendingBanDuration = ""
            openBanConfirmDialog(name, duration)
            reasonField?.text = reason
        }
    }

    private fun isInputFieldFocused() = focused?.let { f -> f === nameField || f === durationField } ?: false

    private fun isMouseOverAnyInput(mouseX: Double, mouseY: Double): Boolean =
        nameField?.isMouseOver(mouseX, mouseY) == true ||
        durationField?.isMouseOver(mouseX, mouseY) == true

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val wasInInput = isInputFieldFocused()
        val result = super.mouseClicked(mouseX, mouseY, button)
        if (wasInInput && !isMouseOverAnyInput(mouseX, mouseY)) {
            focused = null
        }
        return result
    }

    override fun renderBackground(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val panelLeft = width / 2 - 160
        val panelTop = 2
        val panelBottom = height - 32
        val sliceH = 16

        val top = Identifier.of("cobblemarket", "textures/gui/market_panel_top.png")
        val mid = Identifier.of("cobblemarket", "textures/gui/market_panel_middle.png")
        val bot = Identifier.of("cobblemarket", "textures/gui/market_panel_bottom.png")

        drawPanelSlice(context, top, panelLeft, panelTop)
        var y = panelTop + sliceH
        while (y < panelBottom - sliceH) {
            drawPanelSlice(context, mid, panelLeft, y)
            y += sliceH
        }
        drawPanelSlice(context, bot, panelLeft, panelBottom - sliceH)

        val leftX = width / 2 - panelWidth / 2
        val visibleRows = maxVisible()
        val listAreaBottom = listStartY + visibleRows * rowHeight

        hoveredRow = -1
        if (mouseX in leftX..(leftX + panelWidth) && mouseY in listStartY..listAreaBottom) {
            val row = (mouseY - listStartY) / rowHeight
            val shownCount = minOf(visibleRows, bans.size - scrollOffset)
            if (row in 0 until shownCount) hoveredRow = row
        }

        bans.drop(scrollOffset).take(visibleRows).forEachIndexed { di, _ ->
            val rowY = listStartY + di * rowHeight
            val rowState = if (di == hoveredRow) 1 else 0
            drawNineSlice(context, ROW_BACKGROUND_TEXTURE, leftX, rowY, panelWidth, rowHeight, rowState, ROW_BACKGROUND_TEX_H)
        }
    }

    private fun drawPanelSlice(context: DrawContext, texture: Identifier, x: Int, y: Int) {
        context.matrices.push()
        context.matrices.translate(x.toDouble(), y.toDouble(), 0.0)
        context.matrices.scale(0.5f, 0.5f, 1f)
        context.drawTexture(texture, 0, 0, 0f, 0f, 640, 32, 640, 32)
        context.matrices.pop()
    }

    override fun shouldPause() = false
}
