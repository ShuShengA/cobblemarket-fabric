package com.shusheng.cobblemarket.screen

import com.shusheng.cobblemarket.network.AddItemBlacklistPayload
import com.shusheng.cobblemarket.network.ItemBlacklistDataPayload
import com.shusheng.cobblemarket.network.RemoveItemBlacklistPayload
import com.shusheng.cobblemarket.network.RequestItemBlacklistPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Drawable
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier

class ItemBlacklistScreen : Screen(Text.translatable("cobblemarket.op.blacklist_item")) {

    private val panelWidth = 296
    private val rowHeight = 24

    private var entries = listOf<String>()
    private var searchField: TextFieldWidget? = null
    private var addField: TextFieldWidget? = null
    private var previewItemId: String? = null
    private var hoveredRow = -1
    private var scrollOffset = 0
    private var backButton: NineSliceButton? = null
    private var addButton: NineSliceButton? = null
    private val removeButtons = mutableListOf<NineSliceButton>()

    private fun getListStartY() = 64
    private fun getMaxVisibleRows() = maxOf(0, (height - getListStartY() - 48) / rowHeight)

    override fun init() {
        super.init()
        val centerX = width / 2
        val leftX = centerX - panelWidth / 2

        val backBtn = NineSliceButton(
            leftX + panelWidth - 50, 13, 50, 16,
            Text.translatable("cobblemarket.gui.back"),
            { client?.setScreen(AdminScreen()) }
        )
        backButton = backBtn
        addDrawableChild(backBtn)

        searchField = TextFieldWidget(textRenderer, leftX + 2, 44, panelWidth - 4 - 52 - 20, 16, Text.translatable("cobblemarket.gui.search"))
        searchField?.setPlaceholder(Text.translatable("cobblemarket.item.search").formatted(Formatting.GRAY))
        addSelectableChild(searchField)
        addDrawableChild(searchField)

        val addBtn = NineSliceButton(
            leftX + panelWidth - 72, 44, 18, 16,
            Text.literal("+"), { openAddDialog() }
        )
        addButton = addBtn
        addDrawableChild(addBtn)

        scrollOffset = 0
        ClientPlayNetworking.send(RequestItemBlacklistPayload())
    }

    private fun openAddDialog() {
        searchField?.visible = false
        backButton?.visible = false
        addButton?.visible = false
        removeButtons.forEach { it.visible = false }
        val centerX = width / 2
        val dialogY = height / 2 - 55

        addDrawable(object : Drawable {
            override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
                renderAddDialogBackground(context)
            }
        })

        addField = TextFieldWidget(textRenderer, centerX - 80, dialogY + 30, 140, 16, Text.literal(""))
        addField?.setPlaceholder(Text.translatable("cobblemarket.blacklist.item_add_placeholder"))
        addField?.setChangedListener { updatePreview(it) }
        addDrawableChild(addField)

        addDrawableChild(NineSliceButton(
            centerX - 85, dialogY + 72, 80, 20,
            Text.translatable("cobblemarket.blacklist.add"),
            { confirmAdd() }
        ))
        addDrawableChild(NineSliceButton(
            centerX + 5, dialogY + 72, 80, 20,
            Text.translatable("cobblemarket.buy_confirm.cancel"),
            { closeAddDialog() }
        ))
    }

    private fun renderAddDialogBackground(context: DrawContext) {
        val centerX = width / 2
        val dialogW = 220
        val dialogH = 110
        val dialogX = centerX - dialogW / 2
        val dialogY = height / 2 - dialogH / 2

        context.fill(0, 0, width, height, 0xC0000000.toInt())
        drawNineSlice(context, DIALOG_BACKGROUND_TEXTURE, dialogX, dialogY, dialogW, dialogH, 0, DIALOG_BACKGROUND_TEX_H)
        context.drawCenteredTextWithShadow(textRenderer,
            Text.translatable("cobblemarket.blacklist.add_item_title").formatted(Formatting.GOLD),
            centerX, dialogY + 14, 0xFFFFFF)

        // 物品预览
        previewItemId?.let { itemId ->
            Identifier.tryParse(itemId)?.let { id ->
                val item = Registries.ITEM.get(id)
                if (item != Registries.ITEM.get(Identifier.of("minecraft", "air"))) {
                    context.drawItem(ItemStack(item), centerX + 66, dialogY + 24)
                }
            }
        }
    }

    private fun updatePreview(text: String) {
        previewItemId = resolvePreviewItemId(text)
    }

    private fun resolvePreviewItemId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.contains(":")) return trimmed
        val lower = trimmed.lowercase().replace(" ", "_")
        Registries.ITEM.forEach { item ->
            val id = Registries.ITEM.getId(item)
            if (id.path == lower) return id.toString()
        }
        Registries.ITEM.forEach { item ->
            val id = Registries.ITEM.getId(item)
            val name = item.name.string
            if (name == trimmed || name.contains(trimmed)) return id.toString()
        }
        return null
    }

    private fun confirmAdd() {
        val input = addField?.text?.trim()?.takeIf { it.isNotEmpty() } ?: return
        ClientPlayNetworking.send(AddItemBlacklistPayload(input))
        closeAddDialog()
    }

    private fun closeAddDialog() {
        addField = null
        previewItemId = null
        clearChildren()
        init()
    }

    fun onItemBlacklistData(payload: ItemBlacklistDataPayload) {
        entries = payload.entries
        scrollOffset = scrollOffset.coerceIn(0, maxOf(0, entries.size - getMaxVisibleRows()))
        rebuildRemoveButtons()
    }

    private fun filteredEntries(): List<String> {
        val query = searchField?.text?.trim()?.takeIf { it.isNotEmpty() } ?: return entries
        return entries.filter { itemId ->
            val name = Identifier.tryParse(itemId)?.let { Registries.ITEM.get(it).name.string } ?: itemId
            name.contains(query, ignoreCase = true) || itemId.contains(query, ignoreCase = true)
        }
    }

    private fun rebuildRemoveButtons() {
        removeButtons.forEach { remove(it) }
        removeButtons.clear()
        val leftX = width / 2 - panelWidth / 2
        val startY = getListStartY()
        filteredEntries().drop(scrollOffset).take(getMaxVisibleRows()).forEachIndexed { i, itemId ->
            val y = startY + i * rowHeight
            val btn = NineSliceButton(
                leftX + panelWidth - 50, y + 2, 44, rowHeight - 4,
                Text.translatable("cobblemarket.blacklist.remove"),
                { ClientPlayNetworking.send(RemoveItemBlacklistPayload(itemId)) }
            )
            btn.visible = addField == null
            removeButtons.add(btn)
            addDrawableChild(btn)
        }
    }

    private fun entryDisplay(itemId: String): String {
        return Identifier.tryParse(itemId)?.let { Registries.ITEM.get(it).name.string } ?: itemId
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
        val startY = getListStartY()
        val visibleRows = getMaxVisibleRows()
        val listAreaBottom = startY + visibleRows * rowHeight
        val displayList = filteredEntries()

        hoveredRow = -1
        if (mouseX in leftX..(leftX + panelWidth) && mouseY in startY..listAreaBottom) {
            val row = (mouseY - startY) / rowHeight
            val shownCount = minOf(visibleRows, displayList.size - scrollOffset)
            if (row in 0 until shownCount) hoveredRow = row
        }

        displayList.drop(scrollOffset).take(visibleRows).forEachIndexed { di, _ ->
            val rowY = startY + di * rowHeight
            val rowState = if (di == hoveredRow) 1 else 0
            drawNineSlice(context, ROW_BACKGROUND_TEXTURE, leftX, rowY, panelWidth, rowHeight, rowState, ROW_BACKGROUND_TEX_H)
        }
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)

        if (addField != null) {
            return
        }

        val centerX = width / 2
        val leftX = centerX - panelWidth / 2

        context.drawCenteredTextWithShadow(textRenderer,
            Text.translatable("cobblemarket.op.blacklist_item").formatted(Formatting.GOLD),
            centerX, 20, 0xFFFFFF)

        val startY = getListStartY()
        context.fill(leftX, startY - 4, leftX + panelWidth, startY - 3, 0xFF555555.toInt())

        val displayList = filteredEntries()

        if (displayList.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("cobblemarket.blacklist.empty").formatted(Formatting.GRAY),
                centerX, startY + 50, 0xFFFFFF)
        }

        displayList.drop(scrollOffset).take(getMaxVisibleRows()).forEachIndexed { i, itemId ->
            val y = startY + i * rowHeight
            Identifier.tryParse(itemId)?.let { id ->
                context.drawItem(ItemStack(Registries.ITEM.get(id)), leftX + 4, y + 4)
            }
            context.drawTextWithShadow(textRenderer, entryDisplay(itemId), leftX + 24, y + 7, 0xFFFFFF)
        }

        if (hoveredRow >= 0) {
            val actualIdx = scrollOffset + hoveredRow
            if (actualIdx in displayList.indices) {
                renderTooltip(context, displayList[actualIdx], mouseX, mouseY)
            }
        }

        if (displayList.size > getMaxVisibleRows()) {
            context.drawCenteredTextWithShadow(textRenderer,
                "${scrollOffset + 1}-${minOf(scrollOffset + getMaxVisibleRows(), displayList.size)} / ${displayList.size}",
                centerX, height - 49, 0x888888)
        }
    }

    private fun renderTooltip(context: DrawContext, itemId: String, mouseX: Int, mouseY: Int) {
        val name = entryDisplay(itemId)
        val lines = listOf(name, itemId)

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

    private fun drawPanelSlice(context: DrawContext, texture: Identifier, x: Int, y: Int) {
        context.matrices.push()
        context.matrices.translate(x.toDouble(), y.toDouble(), 0.0)
        context.matrices.scale(0.5f, 0.5f, 1f)
        context.drawTexture(texture, 0, 0, 0f, 0f, 640, 32, 640, 32)
        context.matrices.pop()
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        scrollOffset = (scrollOffset - verticalAmount.toInt()).coerceIn(0, maxOf(0, filteredEntries().size - getMaxVisibleRows()))
        rebuildRemoveButtons()
        return true
    }

    private fun isInputFieldFocused() = focused?.let { f -> f === searchField || f === addField } ?: false

    private fun isMouseOverAnyInput(mouseX: Double, mouseY: Double): Boolean =
        searchField?.isMouseOver(mouseX, mouseY) == true ||
        addField?.isMouseOver(mouseX, mouseY) == true

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val wasInInput = isInputFieldFocused()
        val result = super.mouseClicked(mouseX, mouseY, button)
        if (wasInInput && !isMouseOverAnyInput(mouseX, mouseY)) {
            focused = null
        }
        return result
    }

    override fun resize(client: MinecraftClient, width: Int, height: Int) {
        val wasOpen = addField != null
        val name = addField?.text ?: ""
        super.resize(client, width, height)
        if (wasOpen) {
            addField = null
            openAddDialog()
            addField?.text = name
        }
    }

    override fun shouldPause() = false
}
