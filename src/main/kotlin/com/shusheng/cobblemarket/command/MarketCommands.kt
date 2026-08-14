package com.shusheng.cobblemarket.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.shusheng.cobblemarket.market.BanState
import com.shusheng.cobblemarket.network.MarketNetwork
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Formatting

object MarketCommands {

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                CommandManager.literal("market")
                    .then(CommandManager.literal("gui")
                        .executes(::openGui)
                    )
                    .then(CommandManager.literal("ban")
                        .requires { it.hasPermissionLevel(2) }
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .executes(::banPlayer)
                            .then(CommandManager.argument("duration", StringArgumentType.word())
                                .executes(::banPlayer)
                                .then(CommandManager.argument("reason", StringArgumentType.greedyString())
                                    .executes(::banPlayer)
                                )
                            )
                        )
                    )
                    .then(CommandManager.literal("unban")
                        .requires { it.hasPermissionLevel(2) }
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .executes(::unbanPlayer)
                        )
                    )
                    .then(CommandManager.literal("banlist")
                        .requires { it.hasPermissionLevel(2) }
                        .executes(::banList)
                    )
            )
        }
    }

    private fun openGui(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.playerOrThrow
        MarketNetwork.openScreen(player)
        return 1
    }

    private fun banPlayer(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val server = source.server
        val name = StringArgumentType.getString(context, "player")
        val durationStr = try { StringArgumentType.getString(context, "duration") } catch (_: Exception) { null }
        val reason = try { StringArgumentType.getString(context, "reason") } catch (_: Exception) { "" }

        val target = BanState.resolvePlayer(server, name)
        if (target == null) {
            source.sendError(Text.translatable("cobblemarket.ban.player_not_found", name))
            return 0
        }

        val expiresAt = if (durationStr.isNullOrBlank()) {
            null
        } else {
            val ms = BanState.parseDurationMs(durationStr)
            if (ms == null) {
                source.sendError(Text.translatable("cobblemarket.ban.invalid_duration"))
                return 0
            }
            System.currentTimeMillis() + ms
        }

        BanState.get(server).ban(target.first, target.second, source.name, expiresAt, reason)

        if (expiresAt == null) {
            source.sendFeedback({ Text.translatable("cobblemarket.ban.banned", target.second).formatted(Formatting.GREEN) }, false)
        } else {
            source.sendFeedback({ Text.translatable("cobblemarket.ban.banned_until", target.second, durationStr).formatted(Formatting.GREEN) }, false)
        }

        // 若目标在线，即时通知
        server.playerManager.getPlayer(target.first)?.sendMessage(
            if (reason.isNotBlank())
                Text.translatable("cobblemarket.ban.banned_msg_reason", reason).formatted(Formatting.RED)
            else
                Text.translatable("cobblemarket.ban.banned_msg").formatted(Formatting.RED),
            false
        )
        return Command.SINGLE_SUCCESS
    }

    private fun unbanPlayer(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val server = source.server
        val name = StringArgumentType.getString(context, "player")

        val target = BanState.resolvePlayer(server, name)
        if (target == null) {
            source.sendError(Text.translatable("cobblemarket.ban.player_not_found", name))
            return 0
        }

        if (BanState.get(server).unban(target.first) != null) {
            source.sendFeedback({ Text.translatable("cobblemarket.ban.unbanned", target.second).formatted(Formatting.GREEN) }, false)
        } else {
            source.sendError(Text.translatable("cobblemarket.ban.not_banned", target.second))
            return 0
        }
        return Command.SINGLE_SUCCESS
    }

    private fun banList(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val server = source.server
        val bans = BanState.get(server).getAllBans(System.currentTimeMillis())

        if (bans.isEmpty()) {
            source.sendFeedback({ Text.translatable("cobblemarket.ban.banlist_empty") }, false)
            return Command.SINGLE_SUCCESS
        }

        source.sendFeedback({ Text.translatable("cobblemarket.ban.banlist_title").formatted(Formatting.GOLD) }, false)
        bans.forEach { info ->
            val duration = if (info.isPermanent)
                Text.translatable("cobblemarket.ban.permanent").string
            else
                BanState.formatRemaining(info.expiresAt!! - System.currentTimeMillis())
            source.sendFeedback({
                Text.translatable("cobblemarket.ban.banlist_entry", info.playerName, info.bannedBy, duration)
            }, false)
        }
        return Command.SINGLE_SUCCESS
    }

}
