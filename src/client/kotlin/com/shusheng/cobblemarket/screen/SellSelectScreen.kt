package com.shusheng.cobblemarket.screen

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.entity.PoseType
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

class SellSelectScreen : Screen(Text.translatable("cobblemarket.sell.title")) {

    private var pokemonList = listOf<PokemonPreview>()
    private var selectedIndex = -1
    private var priceField: TextFieldWidget? = null
    private var searchField: TextFieldWidget? = null
    private var loaded = false
    private val rowHeight = 24
    private var scrollOffset = 0
    private var shinyOnly = false
    private var typeFilter = ""
    private var typeIdx = 0
    private val minIvs = IntArray(6) { -1 }
    private var hpF: TextFieldWidget? = null; private var atkF: TextFieldWidget? = null; private var defF: TextFieldWidget? = null
    private var spaF: TextFieldWidget? = null; private var spdF: TextFieldWidget? = null; private var speF: TextFieldWidget? = null

    // 3D icon cache
    private data class IconData(val renderable: RenderablePokemon, val state: FloatingState)
    private val iconData = mutableMapOf<Int, IconData>()

    private fun buildIconCache() {
        iconData.clear()
        pokemonList.forEachIndexed { i, p ->
            val id = Identifier.tryParse(p.speciesId) ?: return@forEachIndexed
            val species = PokemonSpecies.getByIdentifier(id) ?: return@forEachIndexed
            val aspects = mutableSetOf<String>()
            if (p.shiny) aspects.add("shiny")
            iconData[i] = IconData(RenderablePokemon(species, aspects, ItemStack.EMPTY), FloatingState())
        }
    }

    override fun init() {
        super.init()
        val lx = width / 2 - 148
        val panelW = 296

        // Row 1: Search (full width)
        searchField = TextFieldWidget(textRenderer, lx + 2, 30, panelW - 4, 16, Text.translatable("cobblemarket.sell.search"))
        searchField?.setPlaceholder(Text.translatable("cobblemarket.sell.search"))
        addSelectableChild(searchField)
        addDrawableChild(searchField)

        // Row 2 (y=50): IV fields
        fun mkIv(x: Int, ph: String): TextFieldWidget {
            val f = TextFieldWidget(textRenderer, x, 50, 46, 16, Text.literal(""))
            f.setPlaceholder(Text.literal(ph))
            f.setTextPredicate { it.length <= 2 && it.all { c -> c.isDigit() } }
            addSelectableChild(f); addDrawableChild(f)
            return f
        }
        hpF = mkIv(lx + 2, "HP"); atkF = mkIv(lx + 51, "ATK"); defF = mkIv(lx + 100, "DEF")
        spaF = mkIv(lx + 149, "SpA"); spdF = mkIv(lx + 198, "SpD"); speF = mkIv(lx + 247, "Spd")

        // Row 3 (y=72): Shiny + Type + Price + Sell + Cancel
        val btnY = 72
        addDrawableChild(NineSliceButton(
            lx + 2, btnY, 62, 20,
            if (shinyOnly) Text.literal("★").append(Text.translatable("cobblemarket.sell.shiny")) else Text.literal("☆").append(Text.translatable("cobblemarket.sell.shiny")),
            { shinyOnly = !shinyOnly; rebuild() }
        ))

        addDrawableChild(NineSliceButton(
            lx + 66, btnY, 58, 20,
            if (typeFilter.isEmpty()) Text.translatable("cobblemarket.sell.type") else Text.translatable(typeFilter),
            { cycleType(); rebuild() }
        ))

        priceField = TextFieldWidget(textRenderer, lx + 126, btnY, 66, 18, Text.literal(""))
        priceField?.setPlaceholder(Text.translatable("cobblemarket.sell.price_placeholder"))
        priceField?.setTextPredicate { it.length <= 8 && it.all { c -> c.isDigit() } }
        addSelectableChild(priceField)
        addDrawableChild(priceField)

        addDrawableChild(NineSliceButton(lx + 194, btnY, 48, 20, Text.translatable("cobblemarket.sell.sell"), { sellSelected() }))
        addDrawableChild(NineSliceButton(lx + 244, btnY, 48, 20, Text.translatable("cobblemarket.sell.back"), { client?.setScreen(MarketScreen()) }))

        if (!loaded) {
            ClientPlayNetworking.send(RequestMyPokemonPayload())
            loaded = true
        }
    }

    private fun rebuild() { clearChildren(); init() }

    private val allTypes = listOf("" to "") + listOf("normal","fire","water","electric","grass","ice","fighting","poison","ground","flying",
        "psychic","bug","rock","ghost","dragon","dark","steel","fairy").map { it to "cobblemon.type.$it" }

    private fun cycleType() {
        typeIdx = (typeIdx + 1) % allTypes.size
        typeFilter = allTypes[typeIdx].second
    }

    private fun filteredList(): List<PokemonPreview> {
        syncIvFields()
        return pokemonList.filter { p ->
            val q = searchField?.text?.trim()?.takeIf { it.isNotEmpty() }
            (q == null || p.species.contains(q, ignoreCase = true) || p.speciesName.contains(q, ignoreCase = true)) &&
            (!shinyOnly || p.shiny) &&
            (typeFilter.isEmpty() || p.primaryType == typeFilter || p.secondaryType == typeFilter) &&
            (minIvs[0] < 0 || p.ivsHp == minIvs[0]) && (minIvs[1] < 0 || p.ivsAtk == minIvs[1]) &&
            (minIvs[2] < 0 || p.ivsDef == minIvs[2]) && (minIvs[3] < 0 || p.ivsSpAtk == minIvs[3]) &&
            (minIvs[4] < 0 || p.ivsSpDef == minIvs[4]) && (minIvs[5] < 0 || p.ivsSpd == minIvs[5])
        }
    }

    private fun syncIvFields() {
        val fields = arrayOf(hpF, atkF, defF, spaF, spdF, speF)
        for (i in 0..5) {
            val raw = fields[i]?.text ?: ""
            val digits = raw.filter { it.isDigit() }.take(2)
            val v = digits.toIntOrNull()?.coerceIn(0, 31) ?: -1
            if (digits != raw) fields[i]?.text = if (v < 0) "" else v.toString()
            minIvs[i] = v
        }
    }

    fun onPokemonList(payload: MyPokemonListPayload) {
        pokemonList = payload.pokemon
        selectedIndex = -1
        buildIconCache()
    }

    private fun sellSelected() {
        val filtered = filteredList()
        if (selectedIndex !in filtered.indices) return
        val price = priceField?.text?.toIntOrNull() ?: return
        if (price <= 0) return
        ClientPlayNetworking.send(SellFromStoragePayload(filtered[selectedIndex].uuid, price))
    }

    private fun typeColor(tk: String) = when (tk.substringAfterLast(".").lowercase()) {
        "normal" -> 0xAAAA99; "fire" -> 0xFF4422; "water" -> 0x3399FF
        "electric" -> 0xFFCC33; "grass" -> 0x77CC55; "ice" -> 0x66CCFF
        "fighting" -> 0xBB5544; "poison" -> 0xAA5599; "ground" -> 0xDDBB55
        "flying" -> 0x8899FF; "psychic" -> 0xFF5599; "bug" -> 0xAABB22
        "rock" -> 0xBBAA66; "ghost" -> 0x6666BB; "dragon" -> 0x7766EE
        "dark" -> 0x775544; "steel" -> 0xAAAABB; "fairy" -> 0xFFAAFF
        else -> 0xFFFFFF
    }

    private fun drawPanelSlice(context: DrawContext, texture: Identifier, x: Int, y: Int) {
        context.matrices.push()
        context.matrices.translate(x.toDouble(), y.toDouble(), 0.0)
        context.matrices.scale(0.5f, 0.5f, 1f)
        context.drawTexture(texture, 0, 0, 0f, 0f, 640, 32, 640, 32)
        context.matrices.pop()
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
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)
        context.drawCenteredTextWithShadow(textRenderer,
            Text.translatable("cobblemarket.sell.title").formatted(Formatting.GOLD),
            width / 2, 20, 0xFFFFFF)
        val lx = width / 2 - 148
        val panelW = 296
        val iconSize = 20
        val startY = 96
        val filtered = filteredList()
        val maxVisible = maxOf(0, (height - startY - 48) / rowHeight)
        scrollOffset = scrollOffset.coerceIn(0, maxOf(0, filtered.size - maxVisible))
        var hovered = -1
        if (mouseX in lx..(lx + panelW) && mouseY >= startY) {
            val row = ((mouseY - startY) / rowHeight) + scrollOffset
            if (row in filtered.indices) hovered = row
        }
        // List
        for (i in scrollOffset until minOf(scrollOffset + maxVisible, filtered.size)) {
            val e = filtered[i]
            val origIdx = pokemonList.indexOf(e)
            val y = startY + (i - scrollOffset) * rowHeight
            val rowState = when { i == selectedIndex -> 2; i == hovered -> 1; else -> 0 }
            drawNineSlice(context, ROW_BACKGROUND_TEXTURE, lx, y, panelW, rowHeight, rowState, ROW_BACKGROUND_TEX_H)

            // 3D icon slot background
            val slotX = lx + 2
            val slotY = y + 2
            val slotTexture = Identifier.of("cobblemarket", "textures/gui/pokemon_slot.png")
            context.matrices.push()
            context.matrices.translate(slotX.toDouble(), slotY.toDouble(), 0.0)
            context.matrices.scale(iconSize / 66f, iconSize / 66f, 1f)
            context.drawTexture(slotTexture, 0, 0, 0f, 0f, 66, 66, 66, 66)
            context.matrices.pop()
            val iconX = lx + 2
            val iconY = y + 2
            renderPokemonIcon(context, origIdx, iconX, iconY, iconSize)

            // Species（[队]/[PC] 固定色，精灵名属性色）
            val src = Text.translatable(if (e.source == "party") "cobblemarket.sell.party" else "cobblemarket.sell.pc").string
            val sm = if (e.shiny) " ☆" else ""
            val tc = typeColor(if (e.primaryType.isNotEmpty()) e.primaryType else "cobblemon.type.normal")
            val srcColor = if (e.source == "party") 0x55FF55 else 0x55AAFF
            var sx = lx + 28
            context.drawText(textRenderer, src, sx, y + 7, srcColor, false)
            sx += textRenderer.getWidth(src) + 4
            val speciesName = "${e.species}$sm"
            context.drawText(textRenderer, speciesName, sx, y + 7, tc, false)
            sx += textRenderer.getWidth(speciesName) + 2

            // Gender icon（紧跟名字）
            if (e.gender == "MALE" || e.gender == "FEMALE") {
                val gi = Identifier.of("cobblemon", if (e.gender == "MALE") "textures/gui/pc/gender_icon_male.png" else "textures/gui/pc/gender_icon_female.png")
                com.cobblemon.mod.common.api.gui.blitk(matrixStack = context.matrices, texture = gi, x = sx, y = y + 5, width = 6, height = 8)
                sx += 8
            }

            // Held item icon（紧跟性别）
            if (e.heldItemId.isNotEmpty()) {
                Identifier.tryParse(e.heldItemId)?.let { heldId ->
                    val heldItem = Registries.ITEM.get(heldId)
                    if (heldItem != Registries.ITEM.get(Identifier.of("minecraft", "air"))) {
                        com.cobblemon.mod.common.client.render.renderScaledGuiItemIcon(
                            itemStack = ItemStack(heldItem), x = sx.toDouble(), y = y + 4.0, scale = 0.6, matrixStack = context.matrices)
                        sx += 12
                    }
                }
            }

            // Ball icon（紧跟携带物）
            if (e.ball.isNotEmpty()) {
                val ballId = Identifier.tryParse(e.ball.removePrefix("item.").replaceFirst(".", ":"))
                if (ballId != null) {
                    val bi = Registries.ITEM.get(ballId)
                    com.cobblemon.mod.common.client.render.renderScaledGuiItemIcon(
                        itemStack = ItemStack(bi), x = sx.toDouble(), y = y + 4.0, scale = 0.6, matrixStack = context.matrices)
                }
            }

            // Level
            context.drawText(textRenderer, "Lv.${e.level}", lx + 135, y + 7, 0xAAAAAA, false)
        }

        // Tooltip on hover
        if (hovered in filtered.indices) {
            renderTooltip(context, filtered[hovered], mouseX, mouseY, if (hovered == selectedIndex) 2 else 1)
        }

        // Footer
        if (filtered.isEmpty() && loaded) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("cobblemarket.sell.no_pokemon").formatted(Formatting.GRAY), width / 2, startY + 50, 0xFFFFFF)
        }
        if (!loaded) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("cobblemarket.sell.loading").formatted(Formatting.GRAY), width / 2, startY + 50, 0xFFFFFF)
        }
        if (filtered.size > maxVisible) {
            context.drawCenteredTextWithShadow(textRenderer, "${scrollOffset + 1}-${minOf(scrollOffset + maxVisible, filtered.size)} / ${filtered.size}", width / 2, height - 49, 0x888888)
        }
    }

    private fun renderPokemonIcon(context: DrawContext, origIdx: Int, x: Int, y: Int, size: Int) {
        val data = iconData[origIdx] ?: return run {
            val tc = 0x88888888.toInt()
            context.fill(x, y, x + size, y + size, tc)
        }
        val matrices = context.matrices
        matrices.push()
        try {
            context.enableScissor(x - 1, y + 1, x + size + 2, y + size + 2)
            matrices.translate(x + size / 2.0, y + 1.0, 0.0)
            matrices.scale(size / 25f * 2.5f, size / 25f * 2.5f, 1f)
            drawProfilePokemon(
                renderablePokemon = data.renderable, matrixStack = matrices,
                rotation = Quaternionf().rotateXYZ(Math.toRadians(13.0).toFloat(), Math.toRadians(35.0).toFloat(), 0f),
                state = data.state, partialTicks = 0f, scale = 4.5f
            )
        } catch (_: Exception) {
        } finally {
            context.disableScissor()
            matrices.pop()
        }
    }

    private fun renderTooltip(context: DrawContext, p: PokemonPreview, mx: Int, my: Int, bgState: Int) {
        val hp = Text.translatable("cobblemon.stat.hp.name").string
        val atk = Text.translatable("cobblemon.stat.attack.name").string
        val def = Text.translatable("cobblemon.stat.defence.name").string
        val spa = Text.translatable("cobblemon.stat.special_attack.name").string
        val spd = Text.translatable("cobblemon.stat.special_defence.name").string
        val spe = Text.translatable("cobblemon.stat.speed.name").string
        val typeText = Text.translatable(p.primaryType).string +
            if (p.secondaryType.isNotEmpty()) " + ${Text.translatable(p.secondaryType).string}" else ""

        val hasHeldItem = p.heldItemId.isNotEmpty() &&
            Identifier.tryParse(p.heldItemId)?.let { Registries.ITEM.get(it) != Registries.ITEM.get(Identifier.of("minecraft", "air")) } == true

        val lines = mutableListOf<Pair<String, Int>>()
        lines.add("${p.species}${if (p.shiny) " ☆" else ""}  Lv.${p.level}" to 0xFFFFFF)
        lines.add("${Text.translatable("cobblemarket.gui.tooltip_type").string}$typeText" to 0xFFFFFF)
        lines.add("${Text.translatable("cobblemarket.gui.tooltip_nature").string}${Text.translatable(p.nature).string}  ${Text.translatable("cobblemarket.gui.tooltip_ability").string}${Text.translatable(p.ability).string}" to 0xFFFFFF)
        var heldItemLine = -1
        if (hasHeldItem) {
            heldItemLine = lines.size
            lines.add(Text.translatable("cobblemarket.gui.tooltip_held").string to 0xFFFFFF)
        }
        lines.add("${Text.translatable("cobblemarket.gui.tooltip_ivs").string}" to 0xFFFFFF)
        lines.add("  $hp:${p.ivsHp}" to 0x66FF66); lines.add("  $atk:${p.ivsAtk}" to 0xFF6666)
        lines.add("  $def:${p.ivsDef}" to 0xFFCC66); lines.add("  $spa:${p.ivsSpAtk}" to 0x6699FF)
        lines.add("  $spd:${p.ivsSpDef}" to 0x66FF99); lines.add("  $spe:${p.ivsSpd}" to 0xFF99FF)

        var mw = 0; lines.forEach { mw = maxOf(mw, textRenderer.getWidth(it.first)) }
        if (heldItemLine >= 0) {
            mw = maxOf(mw, textRenderer.getWidth(lines[heldItemLine].first) + 14)
        }
        val pad = 4
        val tx = minOf(mx + 12, width - mw - 12)
        val th = lines.size * 10 + pad
        val ty = if (my - th - 4 <= 0) minOf(my + 12, height - th) else my - th - 4

        context.matrices.push(); context.matrices.translate(0.0, 0.0, 400.0)
        drawNineSlice(context, ROW_BACKGROUND_TEXTURE, tx - pad, ty - pad, mw + 2 * pad, lines.size * 10 + 2 * pad, bgState, ROW_BACKGROUND_TEX_H)
        lines.forEachIndexed { i, (line, color) ->
            if (i == heldItemLine) {
                context.drawTextWithShadow(textRenderer, line, tx, ty + i * 10, color)
                Identifier.tryParse(p.heldItemId)?.let { heldId ->
                    com.cobblemon.mod.common.client.render.renderScaledGuiItemIcon(
                        itemStack = ItemStack(Registries.ITEM.get(heldId)),
                        x = tx + textRenderer.getWidth(line) + 2.0,
                        y = ty + i * 10 - 2.0,
                        scale = 0.6,
                        matrixStack = context.matrices
                    )
                }
            } else {
                context.drawTextWithShadow(textRenderer, line, tx, ty + i * 10, color)
            }
        }
        context.matrices.pop()
    }

    private fun isInputFieldFocused() = focused?.let { f ->
        f === searchField || f === hpF || f === atkF || f === defF || f === spaF || f === spdF || f === speF || f === priceField
    } ?: false

    private fun isMouseOverAnyInput(mouseX: Double, mouseY: Double): Boolean =
        searchField?.isMouseOver(mouseX, mouseY) == true ||
        hpF?.isMouseOver(mouseX, mouseY) == true ||
        atkF?.isMouseOver(mouseX, mouseY) == true ||
        defF?.isMouseOver(mouseX, mouseY) == true ||
        spaF?.isMouseOver(mouseX, mouseY) == true ||
        spdF?.isMouseOver(mouseX, mouseY) == true ||
        speF?.isMouseOver(mouseX, mouseY) == true ||
        priceField?.isMouseOver(mouseX, mouseY) == true

    override fun mouseClicked(mx: Double, my: Double, btn: Int): Boolean {
        val wasInInput = isInputFieldFocused()
        val r = super.mouseClicked(mx, my, btn)
        if (wasInInput && !isMouseOverAnyInput(mx, my)) {
            focused = null
        }
        val filtered = filteredList()
        val lx = width / 2 - 148
        val startY = 96
        val maxVisible = maxOf(0, (height - startY - 48) / rowHeight)
        if (mx in lx.toDouble()..(lx + 296).toDouble() && my >= startY) {
            val row = ((my - startY) / rowHeight).toInt() + scrollOffset
            if (row in filtered.indices) { selectedIndex = row; return true }
        }
        return r
    }

    override fun mouseScrolled(mx: Double, my: Double, h: Double, v: Double): Boolean {
        val filtered = filteredList()
        val maxVisible = maxOf(0, (height - 96 - 48) / rowHeight)
        scrollOffset = (scrollOffset - v.toInt()).coerceIn(0, maxOf(0, filtered.size - maxVisible))
        return true
    }

    fun onMarketResult(payload: MarketResultPayload) {
        if (payload.success) {
            client?.player?.sendMessage(payload.message.copy().formatted(Formatting.GREEN), false)
            priceField?.text = ""
            selectedIndex = -1
            ClientPlayNetworking.send(RequestMyPokemonPayload())
        } else {
            client?.player?.sendMessage(payload.message.copy().formatted(Formatting.RED), false)
        }
    }

    override fun shouldPause() = false
}
