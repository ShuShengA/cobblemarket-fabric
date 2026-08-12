package com.shusheng.cobblemarket.screen

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.shusheng.cobblemarket.network.*
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import org.joml.Quaternionf

class MarketScreen : Screen(Text.translatable("cobblemarket.gui.title")) {

    private var listings = listOf<ListingEntry>()
    private var currentPage = 1
    private var totalPages = 1

    private var searchField: TextFieldWidget? = null
    private var shinyOnly = false
    private var showMineOnly = false
    private var filterExpanded = false
    private var sortMode = "PRICE_ASC"
    private var genderFilter = ""
    private var typeFilter = ""
    private var typeFilterIndex = 0
    private val minIvs = IntArray(6) { -1 }

    private lateinit var genderButton: ButtonWidget
    private lateinit var typeButton: ButtonWidget
    private lateinit var shinyButton: ButtonWidget
    private lateinit var sortButton: ButtonWidget
    private lateinit var resetButton: ButtonWidget
    private lateinit var mineButton: ButtonWidget
    private lateinit var filterToggleButton: ButtonWidget
    private lateinit var prevButton: ButtonWidget
    private lateinit var nextButton: ButtonWidget
    private val buyButtons = mutableListOf<ButtonWidget>()

    private var hpField: TextFieldWidget? = null
    private var atkField: TextFieldWidget? = null
    private var defField: TextFieldWidget? = null
    private var spaField: TextFieldWidget? = null
    private var spdField: TextFieldWidget? = null
    private var speField: TextFieldWidget? = null

    private var hoveredRow = -1
    private val panelWidth = 320
    private val iconSize = 22

    private val typeOptions = listOf(
        "" to "不限",
        "normal" to "一般",
        "fire" to "火",
        "water" to "水",
        "electric" to "电",
        "grass" to "草",
        "ice" to "冰",
        "fighting" to "格斗",
        "poison" to "毒",
        "ground" to "地面",
        "flying" to "飞行",
        "psychic" to "超能力",
        "bug" to "虫",
        "rock" to "岩石",
        "ghost" to "幽灵",
        "dragon" to "龙",
        "dark" to "恶",
        "steel" to "钢",
        "fairy" to "妖精"
    )

    // Cached renderables per listing index
    private data class IconData(val displayName: String, val renderable: RenderablePokemon, val state: FloatingState)
    private val iconData = mutableMapOf<Int, IconData>()

    private fun cacheIcons() {
        iconData.clear()
        listings.forEachIndexed { index, entry ->
            val id = Identifier.tryParse(entry.speciesId) ?: return@forEachIndexed
            val species = PokemonSpecies.getByIdentifier(id) ?: return@forEachIndexed
            val aspects = mutableSetOf<String>()
            if (entry.shiny) aspects.add("shiny")
            val displayName = species.translatedName.string
            iconData[index] = IconData(displayName, RenderablePokemon(species, aspects, ItemStack.EMPTY), FloatingState())
        }
    }

    override fun init() {
        super.init()

        val centerX = width / 2
        val leftX = centerX - panelWidth / 2

        // Row 1 (y=32): Search text field (full width)
        searchField = TextFieldWidget(textRenderer, leftX + 2, 32, panelWidth - 4 - 52, 16, Text.translatable("cobblemarket.gui.search"))
        searchField?.setPlaceholder(Text.translatable("cobblemarket.gui.search_placeholder").formatted(Formatting.GRAY))
        // Search triggers on focus loss via mouseClicked
        addSelectableChild(searchField)
        addDrawableChild(searchField)

        // Collapse/expand filters toggle (right of search field)
        filterToggleButton = ButtonWidget.builder(
            Text.literal(if (filterExpanded) "▲" else "▼"),
            { toggleFilters() }
        ).dimensions(leftX + panelWidth - 52, 32, 50, 16).build()
        addDrawableChild(filterToggleButton)

        // Filter controls only when expanded
        if (filterExpanded) {

        // Row 2 (y=54): Gender button + Type button
        genderButton = ButtonWidget.builder(
            genderButtonText(),
            { cycleGender() }
        ).dimensions(leftX + 4, 54, 152, 20).build()
        addDrawableChild(genderButton)

        typeButton = ButtonWidget.builder(
            typeButtonText(),
            { cycleType() }
        ).dimensions(leftX + 160, 54, 152, 20).build()
        addDrawableChild(typeButton)

        // Row 3 (y=78): HP, Atk, Def IV inputs
        hpField = createIvField(leftX + 4, 78, "HP")
        atkField = createIvField(leftX + 105, 78, "ATK")
        defField = createIvField(leftX + 206, 78, "DEF")

        // Row 4 (y=102): SpA, SpD, Spd IV inputs
        spaField = createIvField(leftX + 4, 102, "SpA")
        spdField = createIvField(leftX + 105, 102, "SpD")
        speField = createIvField(leftX + 206, 102, "Spd")

        // Row 5 (y=126): Shiny + Sort + Mine + Reset
        shinyButton = ButtonWidget.builder(
            Text.translatable(if (shinyOnly) "cobblemarket.gui.shiny_on" else "cobblemarket.gui.shiny_off"),
            { toggleShiny() }
        ).dimensions(leftX + 4, 126, 95, 20).build()
        addDrawableChild(shinyButton)

        sortButton = ButtonWidget.builder(
            Text.translatable("cobblemarket.gui.sort", Text.translatable(sortDisplay())),
            { cycleSort() }
        ).dimensions(leftX + 103, 126, 95, 20).build()
        addDrawableChild(sortButton)

        mineButton = ButtonWidget.builder(
            Text.translatable(if (showMineOnly) "cobblemarket.gui.mine_active" else "cobblemarket.gui.mine"),
            { toggleMineOnly() }
        ).dimensions(leftX + 202, 126, 55, 20).build()
        addDrawableChild(mineButton)

        resetButton = ButtonWidget.builder(
            Text.translatable("cobblemarket.gui.reset"),
            { resetFilters() }
        ).dimensions(leftX + 261, 126, 55, 20).build()
        addDrawableChild(resetButton)

        } // end if (filterExpanded)

        // Page buttons at bottom
        prevButton = ButtonWidget.builder(
            Text.translatable("cobblemarket.gui.prev"),
            { prevPage() }
        ).dimensions(leftX, height - 28, 80, 20).build()
        addDrawableChild(prevButton)

        nextButton = ButtonWidget.builder(
            Text.translatable("cobblemarket.gui.next"),
            { nextPage() }
        ).dimensions(leftX + panelWidth - 80, height - 28, 80, 20).build()
        addDrawableChild(nextButton)

        rebuildBuyButtons()
        refreshData()
        applyFilterVisibility() // Apply current collapsed state
    }

    private fun createIvField(x: Int, y: Int, placeholder: String): TextFieldWidget {
        val field = TextFieldWidget(textRenderer, x, y, 95, 16, Text.literal(""))
        field.setPlaceholder(Text.literal(placeholder))
        field.setTextPredicate { it.length <= 2 && it.all { c -> c.isDigit() } }
        addSelectableChild(field)
        addDrawableChild(field)
        return field
    }

    private fun getListStartY() = (if (filterExpanded) 148 else 52) + 4
    private fun getMaxVisibleRows() = maxOf(3, (height - getListStartY() - 30) / 22)

    private fun rebuildBuyButtons() {
        buyButtons.forEach { remove(it) }
        buyButtons.clear()

        val centerX = width / 2
        val leftX = centerX - panelWidth / 2
        val startY = getListStartY()
        val rowHeight = 22
        val playerUuid = client?.player?.uuid

        displayedListings().take(getMaxVisibleRows()).forEachIndexed { di, (origIndex, entry) ->
            val y = startY + di * rowHeight
            val isMine = playerUuid != null && entry.sellerUuid == playerUuid
            val label = if (isMine) Text.literal("✕") else Text.translatable("cobblemarket.gui.buy")
            val action = if (isMine)
                ButtonWidget.PressAction { requestCancel(entry.id) }
            else
                ButtonWidget.PressAction { requestBuy(entry.id) }
            val btn = ButtonWidget.builder(label, action)
                .dimensions(leftX + panelWidth - 42, y + 1, 38, rowHeight - 2).build()
            buyButtons.add(btn)
            addDrawableChild(btn)
        }
    }

    private fun displayedListings(): List<IndexedValue<ListingEntry>> {
        val playerUuid = client?.player?.uuid
        var result = listings.withIndex().toList()
        if (showMineOnly && playerUuid != null) {
            result = result.filter { it.value.sellerUuid == playerUuid }
        }
        // Client-side search: matches English name + translated name
        val query = searchField?.text?.trim()?.takeIf { it.isNotEmpty() } ?: return result
        result = result.filter { (origIndex, entry) ->
            entry.species.contains(query, ignoreCase = true) ||
            (iconData[origIndex]?.displayName?.contains(query, ignoreCase = true) == true)
        }
        return result
    }

    // ── Gender filter ──

    private fun genderButtonText(): Text {
        val label = when (genderFilter) {
            "MALE" -> Text.translatable("cobblemarket.gui.filter_male")
            "FEMALE" -> Text.translatable("cobblemarket.gui.filter_female")
            else -> Text.translatable("cobblemarket.gui.filter_any")
        }
        return Text.translatable("cobblemarket.gui.gender").append(": ").append(label)
    }

    private fun cycleGender() {
        genderFilter = when (genderFilter) {
            "" -> "MALE"
            "MALE" -> "FEMALE"
            "FEMALE" -> ""
            else -> ""
        }
        currentPage = 1
        genderButton.message = genderButtonText()
        refreshData()
    }

    // ── Type filter ──

    private fun typeButtonText(): Text {
        val (key, _) = typeOptions.getOrElse(typeFilterIndex) { "" to "" }
        val label = if (key.isEmpty()) Text.translatable("cobblemarket.gui.filter_any")
            else Text.translatable("cobblemon.type.$key")
        return Text.translatable("cobblemarket.gui.type").append(": ").append(label)
    }

    private fun cycleType() {
        typeFilterIndex = (typeFilterIndex + 1) % typeOptions.size
        val (key, _) = typeOptions[typeFilterIndex]
        typeFilter = key
        currentPage = 1
        typeButton.message = typeButtonText()
        refreshData()
    }

    // ── Shiny toggle ──

    private fun toggleShiny() {
        shinyOnly = !shinyOnly
        currentPage = 1
        shinyButton.message = Text.translatable(if (shinyOnly) "cobblemarket.gui.shiny_on" else "cobblemarket.gui.shiny_off")
        mineButton.message = Text.translatable(if (showMineOnly) "cobblemarket.gui.mine_active" else "cobblemarket.gui.mine")
        refreshData()
    }

    // ── Sort cycling ──

    private fun cycleSort() {
        sortMode = when (sortMode) {
            "PRICE_ASC" -> "PRICE_DESC"
            "PRICE_DESC" -> "LEVEL_ASC"
            "LEVEL_ASC" -> "LEVEL_DESC"
            "LEVEL_DESC" -> "NEWEST"
            "NEWEST" -> "PRICE_ASC"
            else -> "PRICE_ASC"
        }
        currentPage = 1
        sortButton.message = Text.translatable("cobblemarket.gui.sort", Text.translatable(sortDisplay()))
        refreshData()
    }

    private fun sortDisplay(): String = when (sortMode) {
        "PRICE_ASC" -> "cobblemarket.sort.price_asc"
        "PRICE_DESC" -> "cobblemarket.sort.price_desc"
        "LEVEL_ASC" -> "cobblemarket.sort.level_asc"
        "LEVEL_DESC" -> "cobblemarket.sort.level_desc"
        "NEWEST" -> "cobblemarket.sort.newest"
        else -> "cobblemarket.sort.price_asc"
    }

    // ── Reset all filters ──

    private fun applyFilterVisibility() {
        filterToggleButton.message = Text.literal(if (filterExpanded) "▲ 收起" else "▼ 筛选")
    }

    private fun toggleFilters() {
        filterExpanded = !filterExpanded
        applyFilterVisibility()
        // Rebuild the screen to correctly show/hide filter controls
        clearChildren()
        init()
        if (client != null) rebuildBuyButtons()
    }

    private fun toggleMineOnly() {
        showMineOnly = !showMineOnly
        mineButton.message = Text.translatable(if (showMineOnly) "cobblemarket.gui.mine_active" else "cobblemarket.gui.mine")
        currentPage = 1
        rebuildBuyButtons()
    }

    private fun resetFilters() {
        searchField?.text = ""
        shinyOnly = false
        showMineOnly = false
        genderFilter = ""
        typeFilter = ""
        typeFilterIndex = 0
        sortMode = "PRICE_ASC"
        currentPage = 1
        for (i in 0..5) minIvs[i] = -1
        hpField?.text = ""
        atkField?.text = ""
        defField?.text = ""
        spaField?.text = ""
        spdField?.text = ""
        speField?.text = ""
        shinyButton.message = Text.literal("☆ 闪光")
        genderButton.message = genderButtonText()
        typeButton.message = typeButtonText()
        sortButton.message = Text.translatable("cobblemarket.gui.sort", Text.translatable(sortDisplay()))
        refreshData()
    }

    // ── Pagination ──

    private fun prevPage() {
        if (currentPage > 1) { currentPage--; refreshData() }
    }

    private fun nextPage() {
        if (currentPage < totalPages) { currentPage++; refreshData() }
    }

    private fun requestBuy(listingId: java.util.UUID) {
        ClientPlayNetworking.send(BuyFromMarketPayload(listingId))
    }

    private fun requestCancel(listingId: java.util.UUID) {
        ClientPlayNetworking.send(CancelFromMarketPayload(listingId))
    }

    // ── Render ──

    private fun isInputFieldFocused() = focused?.let { f ->
        f === searchField || f === hpField || f === atkField || f === defField || f === spaField || f === spdField || f === speField
    } ?: false

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val wasInInput = isInputFieldFocused()
        val result = super.mouseClicked(mouseX, mouseY, button)
        if (wasInInput) {
            focused = null
            syncIvFromFields()
            currentPage = 1
            refreshData()
        }
        return result
    }

    private fun syncIvFromFields() {
        val fields = arrayOf(hpField, atkField, defField, spaField, spdField, speField)
        val ivs = IntArray(6)
        for (i in 0..5) {
            val raw = fields[i]?.text ?: ""
            val digits = raw.filter { it.isDigit() }.take(2)
            // -1 sentinel: field is empty/unset, skip filtering
            // 0: user explicitly typed "0", filter for IV == 0
            ivs[i] = if (digits.isEmpty()) -1 else digits.toIntOrNull()?.coerceIn(0, 31) ?: -1
            if (digits != raw) fields[i]?.text = if (ivs[i] <= 0) "" else ivs[i].toString()
            minIvs[i] = ivs[i]
        }
        currentPage = 1
        refreshData(ivs)
    }

    private fun refreshData(ivs: IntArray = minIvs) {
        ClientPlayNetworking.send(
            RequestMarketPayload(
                speciesFilter = "",  // Searching is client-side via displayedListings()
                shinyOnly = shinyOnly,
                minLevel = 0,
                maxLevel = 100,
                sortMode = sortMode,
                page = currentPage,
                genderFilter = genderFilter,
                typeFilter = typeFilter,
                minIvsHp = ivs[0],
                minIvsAtk = ivs[1],
                minIvsDef = ivs[2],
                minIvsSpAtk = ivs[3],
                minIvsSpDef = ivs[4],
                minIvsSpd = ivs[5]
            )
        )
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)

        val centerX = width / 2
        val leftX = centerX - panelWidth / 2

        // Row 0 (y=8): Title
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable("cobblemarket.gui.title").formatted(Formatting.GOLD),
            centerX, 8, 0xFFFFFF
        )

        // Page indicator
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable("cobblemarket.gui.page", currentPage, totalPages).formatted(Formatting.GRAY),
            centerX, 20, 0xFFFFFF
        )

        val dividerY = getListStartY() - 4
        val startY = getListStartY()
        val rowHeight = 22
        val visibleRows = getMaxVisibleRows()
        val listAreaBottom = startY + visibleRows * rowHeight

        context.fill(leftX, dividerY, leftX + panelWidth, dividerY + 1, 0xFF555555.toInt())

        val displayList = displayedListings()

        // Detect hover
        hoveredRow = -1
        if (mouseX in leftX..(leftX + panelWidth) && mouseY in startY..listAreaBottom) {
            val relY = mouseY - startY
            val row = relY / rowHeight
            if (row in displayList.indices) hoveredRow = row
        }

        if (displayList.isEmpty()) {
            context.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("cobblemarket.gui.no_listings").formatted(Formatting.GRAY),
                centerX, startY + 50, 0xFFFFFF
            )
        }

        displayList.take(visibleRows).forEachIndexed { di, (origIndex, entry) ->
            val y = startY + di * rowHeight

            // Row background
            val bgColor = if (di == hoveredRow) 0x55FFFFFF.toInt()
                else if (di % 2 == 1) 0x22FFFFFF.toInt()
                else 0
            context.fill(leftX, y, leftX + panelWidth, y + rowHeight, bgColor)

            // 3D Pokemon icon
            val iconX = leftX + 2
            val iconY = y + 2
            renderPokemonIcon(context, origIndex, iconX, iconY, iconSize)

            // Species name (translated)
            val typeColor = typeColor(entry.primaryType)
            val shinyMark = if (entry.shiny) " ☆" else ""
            val displayName = iconData[origIndex]?.displayName ?: entry.species
            val speciesText = "$displayName$shinyMark"
            context.drawTextWithShadow(textRenderer, speciesText, leftX + iconSize + 6, y + 3, typeColor)

            // Gender icon (Cobblemon blue/red arrows)
            var iconOffset = 0
            if (entry.gender == "MALE" || entry.gender == "FEMALE") {
                val genderIcon = if (entry.gender == "MALE")
                    Identifier.of("cobblemon", "textures/gui/pc/gender_icon_male.png")
                else
                    Identifier.of("cobblemon", "textures/gui/pc/gender_icon_female.png")
                val genderX = leftX + iconSize + 6 + textRenderer.getWidth(speciesText) + 2
                com.cobblemon.mod.common.api.gui.blitk(
                    matrixStack = context.matrices,
                    texture = genderIcon,
                    x = genderX,
                    y = y + 5,
                    width = 6,
                    height = 8
                )
                iconOffset = 8
            }

            // Ball icon
            val ballId = Identifier.tryParse(entry.ballItem)
            if (ballId != null) {
                val ballItem = Registries.ITEM.get(ballId)
                com.cobblemon.mod.common.client.render.renderScaledGuiItemIcon(
                    itemStack = ItemStack(ballItem),
                    x = leftX + iconSize + 6 + textRenderer.getWidth(speciesText) + 2 + iconOffset + 0.0,
                    y = y + 4.0,
                    scale = 0.6,
                    matrixStack = context.matrices
                )
            }

            // Level
            val levelText = Text.translatable("cobblemarket.gui.lv").string + entry.level
            context.drawTextWithShadow(textRenderer, levelText, leftX + 135, y + 3, 0xAAAAAA)

            // Price
            context.drawTextWithShadow(textRenderer, "${entry.price} ◆", leftX + 185, y + 3, 0x55FFFF)
        }

        // Tooltip on hover
        if (hoveredRow in displayList.indices) {
            renderTooltip(context, displayList[hoveredRow].value, mouseX, mouseY)
        }

        prevButton.active = currentPage > 1
        nextButton.active = currentPage < totalPages
    }

    // ── 3D Pokemon icon rendering ──

    private fun renderPokemonIcon(context: DrawContext, index: Int, x: Int, y: Int, size: Int) {
        val data = iconData[index] ?: run {
            // Fallback: type-colored placeholder
            val entry = listings.getOrNull(index) ?: return
            val tc = typeColor(entry.primaryType)
            context.fill(x, y, x + size, y + size, 0x88000000.toInt())
            context.fill(x + 1, y + 1, x + size - 1, y + size - 1, tc or 0xCC000000.toInt())
            val initial = (iconData[index]?.displayName ?: entry.species).take(2)
            val tw = textRenderer.getWidth(initial)
            context.drawTextWithShadow(textRenderer, initial, x + (size - tw) / 2, y + (size - 8) / 2, 0xFFFFFF)
            if (entry.shiny) context.drawTextWithShadow(textRenderer, "☆", x + size - 6, y - 5, 0xFFFF55)
            return
        }
        try {
            val matrices = context.matrices
            context.enableScissor(x - 1, y + 1, x + size + 2, y + size + 2)
            matrices.push()
            matrices.translate(x + size / 2.0, y + 1.0, 0.0)
            matrices.scale(size / 25f * 2.5f, size / 25f * 2.5f, 1f)

            drawProfilePokemon(
                renderablePokemon = data.renderable,
                matrixStack = matrices,
                rotation = Quaternionf().rotateXYZ(
                    Math.toRadians(13.0).toFloat(),
                    Math.toRadians(35.0).toFloat(),
                    0f
                ),
                state = data.state,
                partialTicks = 0f,
                scale = 4.5f
            )

            matrices.pop()
            context.disableScissor()
        } catch (e: Exception) {
            // Fallback on error
            val entry = listings.getOrNull(index) ?: return
            val tc = typeColor(entry.primaryType)
            context.fill(x, y, x + size, y + size, 0x88000000.toInt())
            context.fill(x + 1, y + 1, x + size - 1, y + size - 1, tc or 0xCC000000.toInt())
            val initial = (iconData[index]?.displayName ?: entry.species).take(2)
            val tw = textRenderer.getWidth(initial)
            context.drawTextWithShadow(textRenderer, initial, x + (size - tw) / 2, y + (size - 8) / 2, 0xFFFFFF)
        }
    }

    // ── Tooltip ──

    private fun renderTooltip(context: DrawContext, entry: ListingEntry, mouseX: Int, mouseY: Int) {
        val hp = Text.translatable("cobblemon.stat.hp.name").string
        val atk = Text.translatable("cobblemon.stat.attack.name").string
        val def = Text.translatable("cobblemon.stat.defence.name").string
        val spa = Text.translatable("cobblemon.stat.special_attack.name").string
        val spd = Text.translatable("cobblemon.stat.special_defence.name").string
        val spe = Text.translatable("cobblemon.stat.speed.name").string

        val w = 0xFFFFFF
        val ivColors = intArrayOf(0x66FF66, 0xFF6666, 0xFFCC66, 0x6699FF, 0x66FF99, 0xFF99FF)
        val lines = listOf(
            "${iconData[listings.indexOf(entry)]?.displayName ?: entry.species}${if (entry.shiny) " ☆" else ""}  ${Text.translatable("cobblemarket.gui.lv").string}${entry.level}" to w,
            "${Text.translatable("cobblemarket.gui.tooltip_type").string}${Text.translatable(entry.primaryType).string}${if (entry.secondaryType.isNotEmpty()) " + ${Text.translatable(entry.secondaryType).string}" else ""}" to w,
            "${Text.translatable("cobblemarket.gui.tooltip_nature").string}${Text.translatable(entry.nature).string}  ${Text.translatable("cobblemarket.gui.tooltip_ability").string}${Text.translatable(entry.ability).string}" to w,
            "${Text.translatable("cobblemarket.gui.tooltip_ivs").string}" to w,
            "  $hp:${entry.ivsHp}" to ivColors[0],
            "  $atk:${entry.ivsAtk}" to ivColors[1],
            "  $def:${entry.ivsDef}" to ivColors[2],
            "  $spa:${entry.ivsSpAtk}" to ivColors[3],
            "  $spd:${entry.ivsSpDef}" to ivColors[4],
            "  $spe:${entry.ivsSpd}" to ivColors[5],
            "${Text.translatable("cobblemarket.gui.tooltip_seller").formatted(Formatting.GRAY).string} ${entry.sellerName}" to w,
            "${Text.translatable("cobblemarket.gui.tooltip_price").formatted(Formatting.GRAY).string} ${entry.price} ${entry.currencyName}" to w
        )

        var maxWidth = 0
        lines.forEach { maxWidth = maxOf(maxWidth, textRenderer.getWidth(it.first)) }

        val padding = 4
        val tx = minOf(mouseX + 12, width - maxWidth - 12)
        val tooltipHeight = lines.size * 10 + padding
        val tyAbove = mouseY - tooltipHeight - 4
        val ty = if (tyAbove <= 0) minOf(mouseY + 12, height - tooltipHeight) else tyAbove

        context.matrices.push()
        context.matrices.translate(0.0, 0.0, 400.0)
        context.fill(tx - padding, ty - padding, tx + maxWidth + padding, ty + lines.size * 10 + padding, 0xFF000000.toInt())
        lines.forEachIndexed { i, (line, color) ->
            context.drawTextWithShadow(textRenderer, line, tx, ty + i * 10, color)
        }
        context.matrices.pop()
    }

    // ── Type color mapping ──

    private fun typeColor(typeKey: String): Int = when (typeKey.substringAfterLast(".").lowercase()) {
        "normal" -> 0xAAAA99; "fire" -> 0xFF4422; "water" -> 0x3399FF
        "electric" -> 0xFFCC33; "grass" -> 0x77CC55; "ice" -> 0x66CCFF
        "fighting" -> 0xBB5544; "poison" -> 0xAA5599; "ground" -> 0xDDBB55
        "flying" -> 0x8899FF; "psychic" -> 0xFF5599; "bug" -> 0xAABB22
        "rock" -> 0xBBAA66; "ghost" -> 0x6666BB; "dragon" -> 0x7766EE
        "dark" -> 0x775544; "steel" -> 0xAAAABB; "fairy" -> 0xFFAAFF
        else -> 0xFFFFFF
    }

    override fun shouldPause(): Boolean = false

    override fun resize(client: net.minecraft.client.MinecraftClient, width: Int, height: Int) {
        val oldSearch = searchField?.text ?: ""
        super.resize(client, width, height)
        searchField?.text = oldSearch
    }

    fun onMarketData(payload: MarketDataPayload) {
        listings = payload.entries
        totalPages = payload.totalPages
        currentPage = payload.currentPage
        cacheIcons()
        rebuildBuyButtons()
    }

    fun onMarketResult(payload: MarketResultPayload) {
        if (client?.player != null) {
            val color = if (payload.success) Formatting.GREEN else Formatting.RED
            client!!.player!!.sendMessage(Text.literal(payload.message).formatted(color), false)
            // Note: payload.message is already translated server-side (single-player compatible)
        }
        if (payload.success) refreshData()
    }
}
