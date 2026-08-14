package com.shusheng.cobblemarket.screen

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblemon.mod.common.pokemon.Species
import com.shusheng.cobblemarket.market.PokemonBlacklistEntry
import com.shusheng.cobblemarket.network.AddPokemonBlacklistPayload
import com.shusheng.cobblemarket.network.PokemonBlacklistDataPayload
import com.shusheng.cobblemarket.network.RemovePokemonBlacklistPayload
import com.shusheng.cobblemarket.network.RequestPokemonBlacklistPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Drawable
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import org.joml.Quaternionf
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier

class PokemonBlacklistScreen : Screen(Text.translatable("cobblemarket.op.blacklist_pokemon")) {

    private val panelWidth = 296
    private val rowHeight = 24

    private var entries = listOf<PokemonBlacklistEntry>()
    private var searchField: TextFieldWidget? = null
    private var addField: TextFieldWidget? = null
    private var ivHpField: TextFieldWidget? = null
    private var ivAtkField: TextFieldWidget? = null
    private var ivDefField: TextFieldWidget? = null
    private var ivSpAtkField: TextFieldWidget? = null
    private var ivSpDefField: TextFieldWidget? = null
    private var ivSpdField: TextFieldWidget? = null
    private var hoveredRow = -1
    private var scrollOffset = 0
    private var backButton: NineSliceButton? = null
    private var addButton: NineSliceButton? = null
    private var previewRenderable: RenderablePokemon? = null
    private val previewState = FloatingState()
    private val removeButtons = mutableListOf<NineSliceButton>()

    private data class IconData(val displayName: String, val renderable: RenderablePokemon, val state: FloatingState)
    private val iconData = mutableMapOf<Int, IconData>()
    private val iconSize = 20

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
        searchField?.setPlaceholder(Text.translatable("cobblemarket.gui.search_placeholder").formatted(Formatting.GRAY))
        addSelectableChild(searchField)
        addDrawableChild(searchField)

        val addBtn = NineSliceButton(
            leftX + panelWidth - 72, 44, 18, 16,
            Text.literal("+"), { openAddDialog() }
        )
        addButton = addBtn
        addDrawableChild(addBtn)

        scrollOffset = 0
        ClientPlayNetworking.send(RequestPokemonBlacklistPayload())
    }

    private fun openAddDialog() {
        searchField?.visible = false
        backButton?.visible = false
        addButton?.visible = false
        removeButtons.forEach { it.visible = false }
        val centerX = width / 2
        val dialogY = height / 2 - 90

        addDrawable(object : Drawable {
            override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
                renderAddDialogBackground(context)
            }
        })

        addField = TextFieldWidget(textRenderer, centerX - 80, dialogY + 30, 140, 16, Text.literal(""))
        addField?.setPlaceholder(Text.translatable("cobblemarket.blacklist.add_placeholder"))
        addField?.setChangedListener { updatePreview(it) }
        addDrawableChild(addField)

        ivHpField = createIvField(centerX - 75, dialogY + 64, "HP")
        ivAtkField = createIvField(centerX - 23, dialogY + 64, "ATK")
        ivDefField = createIvField(centerX + 29, dialogY + 64, "DEF")
        ivSpAtkField = createIvField(centerX - 75, dialogY + 86, "SPA")
        ivSpDefField = createIvField(centerX - 23, dialogY + 86, "SPD")
        ivSpdField = createIvField(centerX + 29, dialogY + 86, "SPE")

        addDrawableChild(NineSliceButton(
            centerX - 85, dialogY + 114, 80, 20,
            Text.translatable("cobblemarket.blacklist.add"),
            { confirmAdd() }
        ))
        addDrawableChild(NineSliceButton(
            centerX + 5, dialogY + 114, 80, 20,
            Text.translatable("cobblemarket.buy_confirm.cancel"),
            { closeAddDialog() }
        ))
    }

    private fun createIvField(x: Int, y: Int, placeholder: String): TextFieldWidget {
        val field = TextFieldWidget(textRenderer, x, y, 46, 16, Text.literal(""))
        field.setPlaceholder(Text.literal(placeholder))
        field.setTextPredicate { it.length <= 2 && it.all { c -> c.isDigit() } }
        addDrawableChild(field)
        return field
    }

    private fun renderAddDialogBackground(context: DrawContext) {
        val centerX = width / 2
        val dialogW = 220
        val dialogH = 180
        val dialogX = centerX - dialogW / 2
        val dialogY = height / 2 - dialogH / 2

        context.fill(0, 0, width, height, 0xC0000000.toInt())
        drawNineSlice(context, DIALOG_BACKGROUND_TEXTURE, dialogX, dialogY, dialogW, dialogH, 0, DIALOG_BACKGROUND_TEX_H)
        context.drawCenteredTextWithShadow(textRenderer,
            Text.translatable("cobblemarket.blacklist.add_title").formatted(Formatting.GOLD),
            centerX, dialogY + 14, 0xFFFFFF)

        context.drawCenteredTextWithShadow(textRenderer,
            Text.translatable("cobblemarket.blacklist.iv_hint"),
            centerX, dialogY + 50, 0xAAAAAA)

        // 精灵预览槽位
        val slotSize = 28
        val slotX = centerX + 66
        val slotY = dialogY + 24
        val slotTexture = Identifier.of("cobblemarket", "textures/gui/pokemon_slot.png")
        context.matrices.push()
        context.matrices.translate(slotX.toDouble(), slotY.toDouble(), 0.0)
        context.matrices.scale(slotSize / 66f, slotSize / 66f, 1f)
        context.drawTexture(slotTexture, 0, 0, 0f, 0f, 66, 66, 66, 66)
        context.matrices.pop()

        previewRenderable?.let { rp ->
            val matrices = context.matrices
            matrices.push()
            try {
                context.enableScissor(slotX - 1, slotY + 1, slotX + slotSize + 2, slotY + slotSize + 2)
                matrices.translate(slotX + slotSize / 2.0, slotY + 1.0, 0.0)
                matrices.scale(slotSize / 25f * 2.5f, slotSize / 25f * 2.5f, 1f)
                drawProfilePokemon(
                    renderablePokemon = rp,
                    matrixStack = matrices,
                    rotation = Quaternionf().rotateXYZ(Math.toRadians(13.0).toFloat(), Math.toRadians(35.0).toFloat(), 0f),
                    state = previewState,
                    partialTicks = 0f,
                    scale = 4.5f
                )
            } catch (_: Exception) {
            } finally {
                context.disableScissor()
                matrices.pop()
            }
        }
    }

    private fun confirmAdd() {
        val input = addField?.text?.trim()?.takeIf { it.isNotEmpty() } ?: return
        ClientPlayNetworking.send(AddPokemonBlacklistPayload(
            speciesId = input,
            ivHp = parseIv(ivHpField?.text),
            ivAtk = parseIv(ivAtkField?.text),
            ivDef = parseIv(ivDefField?.text),
            ivSpAtk = parseIv(ivSpAtkField?.text),
            ivSpDef = parseIv(ivSpDefField?.text),
            ivSpd = parseIv(ivSpdField?.text)
        ))
        closeAddDialog()
    }

    private fun parseIv(text: String?): Int {
        val t = text?.trim()
        if (t.isNullOrEmpty()) return -1
        return t.toIntOrNull()?.coerceIn(0, 31) ?: -1
    }

    private fun updatePreview(text: String) {
        previewRenderable = null
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val species = if (trimmed.contains(":")) {
            Identifier.tryParse(trimmed)?.let { PokemonSpecies.getByIdentifier(it) }
        } else {
            val byName = try { PokemonSpecies.getByName(trimmed) } catch (_: Exception) { null }
            byName ?: resolveByChineseName(trimmed)
        }
        if (species != null) {
            previewRenderable = RenderablePokemon(species, mutableSetOf(), ItemStack.EMPTY)
        }
    }

    private fun resolveByChineseName(name: String): Species? {
        return try {
            PokemonSpecies.implemented.firstOrNull { it.translatedName.string == name || it.translatedName.string.contains(name) }
        } catch (_: Exception) {
            null
        }
    }

    private fun closeAddDialog() {
        addField = null
        previewRenderable = null
        ivHpField = null
        ivAtkField = null
        ivDefField = null
        ivSpAtkField = null
        ivSpDefField = null
        ivSpdField = null
        clearChildren()
        init()
    }

    fun onBlacklistData(payload: PokemonBlacklistDataPayload) {
        entries = payload.entries
        cacheIcons()
        scrollOffset = scrollOffset.coerceIn(0, maxOf(0, entries.size - getMaxVisibleRows()))
        rebuildRemoveButtons()
    }

    private fun cacheIcons() {
        iconData.clear()
        entries.forEachIndexed { index, entry ->
            val id = Identifier.tryParse(entry.speciesId) ?: return@forEachIndexed
            val species = PokemonSpecies.getByIdentifier(id) ?: return@forEachIndexed
            iconData[index] = IconData(species.translatedName.string, RenderablePokemon(species, mutableSetOf(), ItemStack.EMPTY), FloatingState())
        }
    }

    private fun renderPokemonIcon(context: DrawContext, index: Int, x: Int, y: Int, size: Int) {
        val data = iconData[index] ?: return
        val matrices = context.matrices
        matrices.push()
        try {
            context.enableScissor(x - 1, y + 1, x + size + 2, y + size + 2)
            matrices.translate(x + size / 2.0, y + 1.0, 0.0)
            matrices.scale(size / 25f * 2.5f, size / 25f * 2.5f, 1f)
            drawProfilePokemon(
                renderablePokemon = data.renderable,
                matrixStack = matrices,
                rotation = Quaternionf().rotateXYZ(Math.toRadians(13.0).toFloat(), Math.toRadians(35.0).toFloat(), 0f),
                state = data.state,
                partialTicks = 0f,
                scale = 4.5f
            )
        } catch (_: Exception) {
        } finally {
            context.disableScissor()
            matrices.pop()
        }
    }

    private fun filteredEntries(): List<PokemonBlacklistEntry> {
        val query = searchField?.text?.trim()?.takeIf { it.isNotEmpty() } ?: return entries
        return entries.filter { entry ->
            val species = Identifier.tryParse(entry.speciesId)?.let { PokemonSpecies.getByIdentifier(it) }
            val name = species?.translatedName?.string ?: entry.speciesId
            name.contains(query, ignoreCase = true) || entry.speciesId.contains(query, ignoreCase = true)
        }
    }

    private fun rebuildRemoveButtons() {
        removeButtons.forEach { remove(it) }
        removeButtons.clear()
        val leftX = width / 2 - panelWidth / 2
        val startY = getListStartY()
        filteredEntries().drop(scrollOffset).take(getMaxVisibleRows()).forEachIndexed { i, entry ->
            val y = startY + i * rowHeight
            val btn = NineSliceButton(
                leftX + panelWidth - 50, y + 2, 44, rowHeight - 4,
                Text.translatable("cobblemarket.blacklist.remove"),
                { ClientPlayNetworking.send(RemovePokemonBlacklistPayload(entry.id)) }
            )
            btn.visible = addField == null
            removeButtons.add(btn)
            addDrawableChild(btn)
        }
    }

    private fun entryDisplay(entry: PokemonBlacklistEntry): String {
        val species = Identifier.tryParse(entry.speciesId)?.let { PokemonSpecies.getByIdentifier(it) }
        val name = species?.translatedName?.string ?: entry.speciesId
        val ivParts = mutableListOf<String>()
        if (entry.ivHp >= 0) ivParts.add("HP${entry.ivHp}")
        if (entry.ivAtk >= 0) ivParts.add("${Text.translatable("cobblemon.stat.attack.name").string}${entry.ivAtk}")
        if (entry.ivDef >= 0) ivParts.add("${Text.translatable("cobblemon.stat.defence.name").string}${entry.ivDef}")
        if (entry.ivSpAtk >= 0) ivParts.add("${Text.translatable("cobblemon.stat.special_attack.name").string}${entry.ivSpAtk}")
        if (entry.ivSpDef >= 0) ivParts.add("${Text.translatable("cobblemon.stat.special_defence.name").string}${entry.ivSpDef}")
        if (entry.ivSpd >= 0) ivParts.add("${Text.translatable("cobblemon.stat.speed.name").string}${entry.ivSpd}")
        return if (ivParts.isEmpty()) name else "$name（${ivParts.joinToString(" ")}）"
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
            Text.translatable("cobblemarket.op.blacklist_pokemon").formatted(Formatting.GOLD),
            centerX, 20, 0xFFFFFF)

        val startY = getListStartY()
        context.fill(leftX, startY - 4, leftX + panelWidth, startY - 3, 0xFF555555.toInt())

        val displayList = filteredEntries()

        if (displayList.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("cobblemarket.blacklist.empty").formatted(Formatting.GRAY),
                centerX, startY + 50, 0xFFFFFF)
        }

        displayList.drop(scrollOffset).take(getMaxVisibleRows()).forEachIndexed { i, entry ->
            val y = startY + i * rowHeight
            val origIndex = entries.indexOf(entry)
            val slotX = leftX + 2
            val slotY = y + 2
            val slotTexture = Identifier.of("cobblemarket", "textures/gui/pokemon_slot.png")
            context.matrices.push()
            context.matrices.translate(slotX.toDouble(), slotY.toDouble(), 0.0)
            context.matrices.scale(iconSize / 66f, iconSize / 66f, 1f)
            context.drawTexture(slotTexture, 0, 0, 0f, 0f, 66, 66, 66, 66)
            context.matrices.pop()
            renderPokemonIcon(context, origIndex, slotX, slotY, iconSize)
            context.drawTextWithShadow(textRenderer, entryDisplay(entry), leftX + 28, y + 7, 0xFFFFFF)
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

    private fun renderTooltip(context: DrawContext, entry: PokemonBlacklistEntry, mouseX: Int, mouseY: Int) {
        val species = Identifier.tryParse(entry.speciesId)?.let { PokemonSpecies.getByIdentifier(it) }
        val name = species?.translatedName?.string ?: entry.speciesId
        val lines = mutableListOf(name)
        if (entry.ivHp >= 0) lines.add("${Text.translatable("cobblemon.stat.hp.name").string}: ${entry.ivHp}")
        if (entry.ivAtk >= 0) lines.add("${Text.translatable("cobblemon.stat.attack.name").string}: ${entry.ivAtk}")
        if (entry.ivDef >= 0) lines.add("${Text.translatable("cobblemon.stat.defence.name").string}: ${entry.ivDef}")
        if (entry.ivSpAtk >= 0) lines.add("${Text.translatable("cobblemon.stat.special_attack.name").string}: ${entry.ivSpAtk}")
        if (entry.ivSpDef >= 0) lines.add("${Text.translatable("cobblemon.stat.special_defence.name").string}: ${entry.ivSpDef}")
        if (entry.ivSpd >= 0) lines.add("${Text.translatable("cobblemon.stat.speed.name").string}: ${entry.ivSpd}")

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

    private fun isInputFieldFocused() = focused?.let { f ->
        f === searchField || f === addField || f === ivHpField || f === ivAtkField || f === ivDefField ||
        f === ivSpAtkField || f === ivSpDefField || f === ivSpdField
    } ?: false

    private fun isMouseOverAnyInput(mouseX: Double, mouseY: Double): Boolean =
        searchField?.isMouseOver(mouseX, mouseY) == true ||
        addField?.isMouseOver(mouseX, mouseY) == true ||
        ivHpField?.isMouseOver(mouseX, mouseY) == true ||
        ivAtkField?.isMouseOver(mouseX, mouseY) == true ||
        ivDefField?.isMouseOver(mouseX, mouseY) == true ||
        ivSpAtkField?.isMouseOver(mouseX, mouseY) == true ||
        ivSpDefField?.isMouseOver(mouseX, mouseY) == true ||
        ivSpdField?.isMouseOver(mouseX, mouseY) == true

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
        val hp = ivHpField?.text ?: ""
        val atk = ivAtkField?.text ?: ""
        val def = ivDefField?.text ?: ""
        val spa = ivSpAtkField?.text ?: ""
        val spd = ivSpDefField?.text ?: ""
        val spe = ivSpdField?.text ?: ""
        super.resize(client, width, height)
        if (wasOpen) {
            addField = null
            openAddDialog()
            addField?.text = name
            ivHpField?.text = hp
            ivAtkField?.text = atk
            ivDefField?.text = def
            ivSpAtkField?.text = spa
            ivSpDefField?.text = spd
            ivSpdField?.text = spe
        }
    }

    override fun shouldPause() = false
}
