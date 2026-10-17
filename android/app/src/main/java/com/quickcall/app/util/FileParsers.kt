package com.quickcall.app.util

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.util.zip.ZipInputStream

object FileParsers {
    fun readText(context: Context, uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.use { ins ->
            ins.bufferedReader().readText()
        } ?: ""
    }

    fun parseDocx(context: Context, uri: Uri): String {
        val sb = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { ins ->
            val zis = ZipInputStream(ins)
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xml = zis.readBytes().toString(Charsets.UTF_8)
                    // paragraphs
                    val paragraphs = xml.split(Regex("<w:p[ >]"))
                    for (p in paragraphs) {
                        val texts = Regex("<w:t[^>]*>(.*?)</w:t>", RegexOption.DOT_MATCHES_ALL).findAll(p).map { it.groupValues[1] }.joinToString("")
                        if (texts.isNotBlank()) sb.append(texts).append("\n")
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return sb.toString()
    }

    fun parseXlsx(context: Context, uri: Uri): String {
        val sb = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { ins ->
            val zis = ZipInputStream(ins)
            // First collect shared strings
            val shared = mutableListOf<String>()
            var sheetXml: String? = null
            var entry = zis.nextEntry
            while (entry != null) {
                when (entry.name) {
                    "xl/sharedStrings.xml" -> {
                        val xml = zis.readBytes().toString(Charsets.UTF_8)
                        val matches = Regex("<si>.*?</si>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
                        for (m in matches) {
                            val t = Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL).findAll(m.value).map { it.groupValues[1] }.joinToString("")
                            shared.add(unescapeXml(t))
                        }
                    }
                    else -> {
                        if (entry.name.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) && sheetXml == null) {
                            sheetXml = zis.readBytes().toString(Charsets.UTF_8)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            // parse sheet
            if (sheetXml != null) {
                val rows = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL).findAll(sheetXml)
                for (row in rows) {
                    val cells = Regex("<c\\s+([^>]*)>(.*?)</c>", RegexOption.DOT_MATCHES_ALL).findAll(row.groupValues[1])
                    val vals = mutableListOf<String>()
                    for (cell in cells) {
                        val attrs = cell.groupValues[1]
                        val body = cell.groupValues[2]
                        val type = Regex("t=\"([^\"]*)\"").find(attrs)?.groupValues?.get(1) ?: ""
                        val vMatch = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL).find(body)
                        val raw = vMatch?.groupValues?.get(1) ?: ""
                        val value = if (type == "s") {
                            val idx = raw.toIntOrNull() ?: -1
                            if (idx in shared.indices) shared[idx] else raw
                        } else unescapeXml(raw)
                        vals.add(value)
                    }
                    sb.append(vals.joinToString("\t")).append("\n")
                }
            }
        }
        return sb.toString()
    }

    private fun unescapeXml(s: String): String {
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&apos;", "'")
    }
}
