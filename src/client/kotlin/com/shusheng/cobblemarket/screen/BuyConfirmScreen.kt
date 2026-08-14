package com.shusheng.cobblemarket.screen

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.shusheng.cobblemarket.network.BuyFromMarketPayload
import com.shusheng.cobblemarket.network.ListingEntry
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import org.joml.Quaternionf

class BuyConfirmScreen(private val entry: ListingEntry) : Screen(Text.translatable("cobblemarket.buy_confirm.title")) {

    private var renderable: RenderablePokemon? = null
    private var displayName = ""
    private val state = FloatingState()

    override fun init() {
        super.init()
        val centerX = width / 2

        // Build renderable for 3D icon
        val id = Identifier.tryParse(entry.speciesId)
        if (id != null) {
            val species = PokemonSpecies.getByIdentifier(id)
            if (species != null) {
                displayName = species.translatedName.string
                val aspects = mutableSetOf<String>()
                if (entry.shiny) aspects.add("shiny")
                renderable = RenderablePokemon(species, aspects, ItemStack.EMPTY)
            }
        }

        val btnW = 100
        val btnH = 22
        val gap = 10
        val btnY = height - 36

        addDrawableChild(TextureButton(
            centerX - btnW - gap / 2, btnY, btnW, btnH,
            Text.translatable("cobblemarket.buy_confirm.confirm"),
            { confirm() }
        ))

        addDrawableChild(TextureButton(
            centerX + gap / 2, btnY, btnW, btnH,
            Text.translatable("cobblemarket.buy_confirm.cancel"),
            { client?.setScreen(MarketScreen()) }
        ))
    }

    private fun confirm() {
        ClientPlayNetworking.send(BuyFromMarketPayload(entry.id))
        client?.setScreen(MarketScreen())
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)
        val centerX = width / 2

        // Title
        context.drawCenteredTextWithShadow(textRenderer,
            Text.translatable("cobblemarket.buy_confirm.title").formatted(Formatting.GOLD),
            centerX, 14, 0xFFFFFF)

        // 3D icon
        val iconSize = 30
        val iconY = 26
        renderable?.let { rp ->
            val matrices = context.matrices
            matrices.push()
            matrices.translate(centerX.toDouble(), (iconY + iconSize / 2).toDouble(), 0.0)
            matrices.scale(iconSize / 25f * 2.5f, iconSize / 25f * 2.5f, 1f)
            drawProfilePokemon(
                renderablePokemon = rp, matrixStack = matrices,
                rotation = Quaternionf().rotateXYZ(Math.toRadians(13.0).toFloat(), Math.toRadians(35.0).toFloat(), 0f),
                state = state, partialTicks = 0f, scale = 4.5f
            )
            matrices.pop()
        }

        val lineH = 11
        var infoY = iconY + iconSize + 8

        // Pokemon name
        val name = (if (displayName.isNotEmpty()) displayName else entry.species) + (if (entry.shiny) " ☆" else "")
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal(name).formatted(Formatting.WHITE), centerX, infoY, 0xFFFFFF)
        infoY += lineH

        // Level
        context.drawCenteredTextWithShadow(textRenderer,
            Text.translatable("cobblemarket.buy_confirm.level", entry.level), centerX, infoY, 0xAAAAAA)
        infoY += lineH

        // Nature + Ability
        val nature = Text.translatable("cobblemarket.gui.tooltip_nature").string + Text.translatable(entry.nature).string
        val ability = Text.translatable("cobblemarket.gui.tooltip_ability").string + Text.translatable(entry.ability).string
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal("$nature  $ability"), centerX, infoY, 0xAAAAAA)
        infoY += lineH

        // IVs
        val hp = Text.translatable("cobblemon.stat.hp.name").string
        val atk = Text.translatable("cobblemon.stat.attack.name").string
        val def = Text.translatable("cobblemon.stat.defence.name").string
        val spa = Text.translatable("cobblemon.stat.special_attack.name").string
        val spd = Text.translatable("cobblemon.stat.special_defence.name").string
        val spe = Text.translatable("cobblemon.stat.speed.name").string
        listOf(
            "$hp: ${entry.ivsHp}" to 0x66FF66,
            "$atk: ${entry.ivsAtk}" to 0xFF6666,
            "$spa: ${entry.ivsSpAtk}" to 0x6699FF,
            "$def: ${entry.ivsDef}" to 0xFFCC66,
            "$spd: ${entry.ivsSpDef}" to 0x66FF99,
            "$spe: ${entry.ivsSpd}" to 0xFF99FF
        ).forEach { (line, color) ->
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(line), centerX, infoY, color)
            infoY += lineH
        }

        // Price
        context.drawCenteredTextWithShadow(textRenderer,
            Text.translatable("cobblemarket.buy_confirm.price", entry.price, entry.currencyName),
            centerX, infoY, 0x55FFFF)
    }

    override fun shouldPause() = false
}
