package com.shusheng.cobblemarket.screen

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.client.sound.SoundManager
import net.minecraft.sound.SoundEvent
import net.minecraft.text.Text
import net.minecraft.util.Identifier

class TextureButton(
    x: Int, y: Int, width: Int, height: Int,
    message: Text,
    onPress: PressAction
) : ButtonWidget(x, y, width, height, message, onPress, ButtonWidget.DEFAULT_NARRATION_SUPPLIER) {

    private val texture = Identifier.of("cobblemarket", "textures/gui/button.png")
    private val textureWidth = 320
    private val textureHeight = 96
    private val stateHeight = 48

    override fun renderWidget(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val hovered = isHovered
        val vOffset = if (hovered) stateHeight else 0

        // Texture is 2x resolution; scale down to the button's logical size
        context.matrices.push()
        context.matrices.translate(x.toDouble(), y.toDouble(), 0.0)
        context.matrices.scale(width / 320f, height / 48f, 1f)
        context.drawTexture(texture, 0, 0, 0f, vOffset.toFloat(), 320, stateHeight, textureWidth, textureHeight)
        context.matrices.pop()

        // Draw centered text
        val font = MinecraftClient.getInstance().textRenderer
        val textX = x + (width - font.getWidth(message)) / 2
        val textY = y + (height - 8) / 2
        context.drawTextWithShadow(font, message, textX, textY, 0xFFFFFF)
    }

    override fun playDownSound(soundManager: SoundManager) {
        soundManager.play(PositionedSoundInstance.master(
            SoundEvent.of(Identifier.of("cobblemarket", "button_click")),
            1.0f
        ))
    }
}
