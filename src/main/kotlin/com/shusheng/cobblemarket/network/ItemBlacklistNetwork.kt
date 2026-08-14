package com.shusheng.cobblemarket.network

import com.shusheng.cobblemarket.CobbleMarket
import com.shusheng.cobblemarket.market.ItemBlacklistState
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.registry.Registries

// ── C2S: 请求物品黑名单 ──

class RequestItemBlacklistPayload : CustomPayload {
    override fun getId() = ID
    companion object {
        val ID = CustomPayload.Id<RequestItemBlacklistPayload>(CobbleMarket.id("request_item_blacklist"))
        val CODEC: PacketCodec<PacketByteBuf, RequestItemBlacklistPayload> = PacketCodec.of(
            { _, b -> b.writeInt(0) },
            { b -> b.readInt(); RequestItemBlacklistPayload() }
        )
    }
}

// ── C2S: 添加物品黑名单 ──

data class AddItemBlacklistPayload(val itemName: String) : CustomPayload {
    override fun getId() = ID
    companion object {
        val ID = CustomPayload.Id<AddItemBlacklistPayload>(CobbleMarket.id("add_item_blacklist"))
        val CODEC: PacketCodec<PacketByteBuf, AddItemBlacklistPayload> = PacketCodec.of(
            { p, b -> b.writeString(p.itemName) },
            { b -> AddItemBlacklistPayload(b.readString()) }
        )
    }
}

// ── C2S: 删除物品黑名单 ──

data class RemoveItemBlacklistPayload(val itemId: String) : CustomPayload {
    override fun getId() = ID
    companion object {
        val ID = CustomPayload.Id<RemoveItemBlacklistPayload>(CobbleMarket.id("remove_item_blacklist"))
        val CODEC: PacketCodec<PacketByteBuf, RemoveItemBlacklistPayload> = PacketCodec.of(
            { p, b -> b.writeString(p.itemId) },
            { b -> RemoveItemBlacklistPayload(b.readString()) }
        )
    }
}

// ── S2C: 物品黑名单列表 ──

data class ItemBlacklistDataPayload(val entries: List<String>) : CustomPayload {
    override fun getId() = ID
    companion object {
        val ID = CustomPayload.Id<ItemBlacklistDataPayload>(CobbleMarket.id("item_blacklist_data"))
        val CODEC: PacketCodec<PacketByteBuf, ItemBlacklistDataPayload> = PacketCodec.of(
            { p, b -> b.writeVarInt(p.entries.size); p.entries.forEach { b.writeString(it) } },
            { b -> ItemBlacklistDataPayload((0 until b.readVarInt()).map { b.readString() }) }
        )
    }
}

object ItemBlacklistNetwork {

    fun register() {
        PayloadTypeRegistry.playC2S().register(RequestItemBlacklistPayload.ID, RequestItemBlacklistPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(AddItemBlacklistPayload.ID, AddItemBlacklistPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(RemoveItemBlacklistPayload.ID, RemoveItemBlacklistPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(ItemBlacklistDataPayload.ID, ItemBlacklistDataPayload.CODEC)

        ServerPlayNetworking.registerGlobalReceiver(RequestItemBlacklistPayload.ID) { _, context ->
            val player = context.player()
            if (!player.hasPermissionLevel(2)) return@registerGlobalReceiver
            val server = player.server
            server.execute {
                val entries = ItemBlacklistState.get(server).getAll()
                ServerPlayNetworking.send(player, ItemBlacklistDataPayload(entries))
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(AddItemBlacklistPayload.ID) { payload, context ->
            val player = context.player()
            if (!player.hasPermissionLevel(2)) return@registerGlobalReceiver
            val server = player.server
            server.execute {
                val itemId = resolveItemId(payload.itemName) ?: return@execute
                ItemBlacklistState.get(server).add(itemId)
                val entries = ItemBlacklistState.get(server).getAll()
                ServerPlayNetworking.send(player, ItemBlacklistDataPayload(entries))
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(RemoveItemBlacklistPayload.ID) { payload, context ->
            val player = context.player()
            if (!player.hasPermissionLevel(2)) return@registerGlobalReceiver
            val server = player.server
            server.execute {
                ItemBlacklistState.get(server).remove(payload.itemId)
                val entries = ItemBlacklistState.get(server).getAll()
                ServerPlayNetworking.send(player, ItemBlacklistDataPayload(entries))
            }
        }
    }

    private fun resolveItemId(input: String): String? {
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
}
