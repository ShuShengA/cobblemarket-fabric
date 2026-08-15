package com.shusheng.cobblemarket.event

import com.shusheng.cobblemarket.CobbleMarket
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TransactionFileLogger {

    private val DANGEROUS_PREFIXES = setOf('=', '+', '-', '@')

    // DateTimeFormatter 线程安全（SimpleDateFormat 不是），系统时区与旧行为一致
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private var currentDate = ""
    private var currentZhFile: File? = null
    private var currentEnFile: File? = null

    fun log(record: TransactionRecord) {
        try {
            val instant = Instant.ofEpochMilli(record.timestamp).atZone(ZoneId.systemDefault())
            val date = dateFormat.format(instant)
            if (date != currentDate) {
                currentDate = date
                currentZhFile = resolveFile(date, "zh_cn", "时间,类型,分类,卖家,买家,精灵,价格,手续费")
                currentEnFile = resolveFile(date, "en_us", "Time,Type,Category,Seller,Buyer,Species,Price,Fee")
            }
            val zhFile = currentZhFile ?: return
            val enFile = currentEnFile ?: return
            Files.writeString(zhFile.toPath(), buildCsvLine(record, "zh_cn") + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
            Files.writeString(enFile.toPath(), buildCsvLine(record, "en_us") + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        } catch (e: Exception) {
            CobbleMarket.LOGGER.warn("Failed to write transaction log: ${e.message}")
        }
    }

    private fun resolveFile(date: String, lang: String, header: String): File {
        val dir = FabricLoader.getInstance().configDir.resolve("cobblemarket/history").toFile()
        dir.mkdirs()
        val file = File(dir, "history_${date}_${lang}.csv")
        if (!file.exists()) {
            file.writeText(header + "\n")
        }
        return file
    }

    private fun buildCsvLine(record: TransactionRecord, lang: String): String {
        val time = timeFormat.format(Instant.ofEpochMilli(record.timestamp).atZone(ZoneId.systemDefault()))
        val type = typeName(record.type, lang)
        val category = record.category.name
        val seller = csvEscape(record.sellerName)
        val buyer = csvEscape(record.buyerName)
        val species = csvEscape(speciesDisplay(record.species))
        return "$time,$type,$category,$seller,$buyer,$species,${record.price},${record.fee}"
    }

    private fun typeName(type: TransactionType, lang: String): String = when (type) {
        TransactionType.ADD -> if (lang == "zh_cn") "上架" else "Listed"
        TransactionType.PURCHASE -> if (lang == "zh_cn") "卖出" else "Sold"
        TransactionType.CANCEL -> if (lang == "zh_cn") "下架" else "Cancelled"
        TransactionType.RETURN -> if (lang == "zh_cn") "退还" else "Returned"
    }

    private fun speciesDisplay(key: String): String {
        val marker = ".species."
        val idx = key.indexOf(marker)
        return if (idx >= 0) key.substring(idx + marker.length).removeSuffix(".name") else key
    }

    private fun csvEscape(s: String): String {
        // 防 CSV 公式注入：Excel/WPS 会把以 = + - @ 开头的单元格当公式执行，
        // 前缀单引号强制按文本处理（Excel 中单引号不显示）
        val guarded = if (s.isNotEmpty() && s[0] in DANGEROUS_PREFIXES) "'$s" else s
        return if (guarded.contains(",") || guarded.contains("\"") || guarded.contains("\n") || guarded.contains("\r")) {
            "\"" + guarded.replace("\"", "\"\"") + "\""
        } else guarded
    }
}
