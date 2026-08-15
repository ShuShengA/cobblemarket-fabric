package com.shusheng.cobblemarket.util

import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text

object TextUtil {
    // 截断到 maxWidth 像素：超宽时截断尾部加 "…"；未超宽原样返回。
    // 用于黑名单按钮/列表等固定宽度区域，防止无翻译物品的超长 key 原文溢出控件。
    fun truncateString(s: String, maxWidth: Int): String {
        val font = MinecraftClient.getInstance().textRenderer
        if (font.getWidth(s) <= maxWidth) return s
        var end = s.length - 1
        while (end > 0 && font.getWidth(s.substring(0, end) + "…") > maxWidth) end--
        return s.substring(0, end) + "…"
    }

    fun truncateText(t: Text, maxWidth: Int): Text = Text.literal(truncateString(t.string, maxWidth))
}
