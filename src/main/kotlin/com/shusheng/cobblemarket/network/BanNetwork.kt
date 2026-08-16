package com.shusheng.cobblemarket.network

import com.shusheng.cobblemarket.CobbleMarket
import com.shusheng.cobblemarket.market.BanState
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.util.UUID

// ── DTO ──

data class BanEntry(
    val playerUuid: UUID,
    val playerName: String,
    val bannedBy: String,
    val bannedAt: Long,
    val expiresAt: Long?,
    val durationDisplay: String,
    val reason: String
) {
    fun write(buf: PacketByteBuf) {
        buf.writeUuid(playerUuid)
        buf.writeString(playerName)
        buf.writeString(bannedBy)
        buf.writeLong(bannedAt)
        buf.writeBoolean(expiresAt != null)
        expiresAt?.let { buf.writeLong(it) }
        buf.writeString(durationDisplay)
        buf.writeString(reason)
    }

    companion object {
        fun read(buf: PacketByteBuf) = BanEntry(
            playerUuid = buf.readUuid(),
            playerName = buf.readString(),
            bannedBy = buf.readString(),
            bannedAt = buf.readLong(),
            expiresAt = if (buf.readBoolean()) buf.readLong() else null,
            durationDisplay = buf.readString(),
            reason = buf.readString()
        )
    }
}

// ── C2S: 封禁 ──

data class AdminBanPayload(val playerName: String, val duration: String, val reason: String) : CustomPayload {
    override fun getId() = ID
    companion object {
        val ID = CustomPayload.Id<AdminBanPayload>(CobbleMarket.id("admin_ban"))
        val CODEC: PacketCodec<PacketByteBuf, AdminBanPayload> = PacketCodec.of(
            { p, b -> b.writeString(p.playerName); b.writeString(p.duration); b.writeString(p.reason) },
            { b -> AdminBanPayload(b.readString(), b.readString(), b.readString()) }
        )
    }
}

// ── C2S: 解封 ──

data class AdminUnbanPayload(val playerUuid: UUID) : CustomPayload {
    override fun getId() = ID
    companion object {
        val ID = CustomPayload.Id<AdminUnbanPayload>(CobbleMarket.id("admin_unban"))
        val CODEC: PacketCodec<PacketByteBuf, AdminUnbanPayload> = PacketCodec.of(
            { p, b -> b.writeUuid(p.playerUuid) },
            { b -> AdminUnbanPayload(b.readUuid()) }
        )
    }
}

// ── C2S: 请求封禁列表 ──

class RequestBanListPayload : CustomPayload {
    override fun getId() = ID
    companion object {
        val ID = CustomPayload.Id<RequestBanListPayload>(CobbleMarket.id("request_ban_list"))
        val CODEC: PacketCodec<PacketByteBuf, RequestBanListPayload> = PacketCodec.of(
            { _, b -> b.writeInt(0) },
            { b -> b.readInt(); RequestBanListPayload() }
        )
    }
}

// ── S2C: 封禁列表 ──

data class BanListDataPayload(val entries: List<BanEntry>) : CustomPayload {
    override fun getId() = ID
    companion object {
        val ID = CustomPayload.Id<BanListDataPayload>(CobbleMarket.id("ban_list_data"))
        val CODEC: PacketCodec<PacketByteBuf, BanListDataPayload> = PacketCodec.of(
            { p, b -> b.writeVarInt(p.entries.size); p.entries.forEach { it.write(b) } },
            { b -> BanListDataPayload((0 until b.readVarInt()).map { BanEntry.read(b) }) }
        )
    }
}

object BanNetwork {

    fun register() {
        PayloadTypeRegistry.playC2S().register(AdminBanPayload.ID, AdminBanPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(AdminUnbanPayload.ID, AdminUnbanPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(RequestBanListPayload.ID, RequestBanListPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(BanListDataPayload.ID, BanListDataPayload.CODEC)

        ServerPlayNetworking.registerGlobalReceiver(AdminBanPayload.ID) { payload, context ->
            val player = context.player()
            if (!player.hasPermissionLevel(2)) return@registerGlobalReceiver
            val server = player.server
            server.execute {
                val target = BanState.resolvePlayer(server, payload.playerName)
                if (target == null) {
                    ServerPlayNetworking.send(player, MarketResultPayload(false, Text.translatable("cobblemarket.ban.player_not_found", payload.playerName)))
                    return@execute
                }

                val expiresAt = if (payload.duration.isBlank()) {
                    null
                } else {
                    val ms = BanState.parseDurationMs(payload.duration)
                    if (ms == null) {
                        ServerPlayNetworking.send(player, MarketResultPayload(false, Text.translatable("cobblemarket.ban.invalid_duration")))
                        return@execute
                    }
                    System.currentTimeMillis() + ms
                }

                BanState.get(server).ban(target.first, target.second, player.name.string, expiresAt, payload.reason)

                val msg = if (expiresAt == null)
                    Text.translatable("cobblemarket.ban.banned", target.second)
                else
                    Text.translatable("cobblemarket.ban.banned_until", target.second, payload.duration)
                ServerPlayNetworking.send(player, MarketResultPayload(true, msg))

                server.playerManager.getPlayer(target.first)?.sendMessage(
                    buildBanNotice(expiresAt, payload.reason),
                    false
                )
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(AdminUnbanPayload.ID) { payload, context ->
            val player = context.player()
            if (!player.hasPermissionLevel(2)) return@registerGlobalReceiver
            val server = player.server
            server.execute {
                val removed = BanState.get(server).unban(payload.playerUuid)
                if (removed != null) {
                    ServerPlayNetworking.send(player, MarketResultPayload(true, Text.translatable("cobblemarket.ban.unbanned", removed.playerName)))
                } else {
                    ServerPlayNetworking.send(player, MarketResultPayload(false, Text.translatable("cobblemarket.ban.not_banned", payload.playerUuid.toString())))
                }
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(RequestBanListPayload.ID) { _, context ->
            val player = context.player()
            if (!player.hasPermissionLevel(2)) return@registerGlobalReceiver
            val server = player.server
            server.execute {
                val bans = BanState.get(server).getAllBans(System.currentTimeMillis())
                val now = System.currentTimeMillis()
                val entries = bans.map { info ->
                    BanEntry(
                        playerUuid = info.playerUuid,
                        playerName = info.playerName,
                        bannedBy = info.bannedBy,
                        bannedAt = info.bannedAt,
                        expiresAt = info.expiresAt,
                        // permanent 发空串，客户端按 expiresAt == null 用翻译键渲染"永久"
                        durationDisplay = if (info.isPermanent) "" else BanState.formatRemaining(info.expiresAt!! - now),
                        reason = info.reason
                    )
                }
                ServerPlayNetworking.send(player, BanListDataPayload(entries))
            }
        }
    }

    private fun buildBanNotice(expiresAt: Long?, reason: String): MutableText {
        // 保留 Text 对象：嵌套翻译在客户端语言下渲染
        val timeDesc: Text = if (expiresAt == null)
            Text.translatable("cobblemarket.ban.permanent")
        else
            Text.translatable("cobblemarket.ban.remaining", BanState.formatRemaining(expiresAt - System.currentTimeMillis()))
        val msg = if (reason.isNotBlank())
            Text.translatable("cobblemarket.ban.banned_msg_time_reason", timeDesc, reason)
        else
            Text.translatable("cobblemarket.ban.banned_msg_time", timeDesc)
        return msg.formatted(Formatting.RED)
    }
}
