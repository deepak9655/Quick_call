package com.quickcall.app.util

import com.quickcall.app.data.Contact

object Parser {
    private val phoneRegex = Regex("(\\+?\\d[\\d\\s\\-().]{6,}\\d)")

    fun cleanPhone(p: String): String = p.replace(Regex("[^\\d+]"), "")

    fun extractPhone(s: String): String? = phoneRegex.find(s)?.value?.trim()

    fun parseText(text: String): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val seen = mutableSetOf<String>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            var name = ""
            var phone = ""
            val delims = listOf("\t", ",", ";", "|", " - ", " – ", ":", "  ")
            val split = delims.firstOrNull { line.contains(it) }?.let { line.split(it) }
            if (split != null && split.size >= 2) {
                val a = split[0].trim()
                val b = split.drop(1).joinToString(" ").trim()
                if (phoneRegex.containsMatchIn(a) && !phoneRegex.containsMatchIn(b)) {
                    phone = a; name = b
                } else if (phoneRegex.containsMatchIn(b)) {
                    phone = extractPhone(b) ?: ""; name = a
                } else {
                    phone = extractPhone(line) ?: ""; name = line.replace(phoneRegex, "").replace(Regex("[-:,]"), "").trim()
                }
            } else {
                phone = extractPhone(line) ?: ""
                name = if (phone.isNotEmpty()) line.replace(phoneRegex, "").replace(Regex("[-:,]"), "").trim() else line
            }
            if (phone.isNotEmpty()) {
                val p = extractPhone(phone) ?: phone
                val key = cleanPhone(p)
                if (key !in seen) {
                    seen.add(key)
                    contacts.add(Contact(name = name.ifEmpty { "Unknown" }, phone = p))
                }
            }
        }
        return contacts
    }
}
