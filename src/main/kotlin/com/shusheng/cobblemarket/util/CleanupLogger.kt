package com.shusheng.cobblemarket.util

import com.shusheng.cobblemarket.CobbleMarket
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID

/**
 * 退回清理明细日志：清理是资产删除（按服主配置的保留期），记录每一条明细，
 * 写入 config/cobblemarket/history/cleaned_YYYY-MM-DD.csv，便于事后追查。
 */
object CleanupLogger {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd")
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    fun log(
        category: String,
        playerUuid: UUID,
        listingId: UUID,
        detail: String,
        price: Int,
        retainedDays: Long
    ) {
        try {
            val date = dateFormat.format(Date())
            val dir = FabricLoader.getInstance().configDir.resolve("cobblemarket/history").toFile()
            dir.mkdirs()
            val file = File(dir, "cleaned_$date.csv")
            if (!file.exists()) {
                file.writeText("Time,Category,PlayerUUID,ListingID,Detail,Price,RetainedDays\n")
            }
            val safeDetail = "\"" + detail.replace("\"", "\"\"") + "\""
            val line = "${timeFormat.format(Date())},$category,$playerUuid,$listingId,$safeDetail,$price,$retainedDays"
            Files.writeString(file.toPath(), line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        } catch (e: Exception) {
            CobbleMarket.LOGGER.warn("Failed to write cleanup log: {}", e.message)
        }
    }
}
