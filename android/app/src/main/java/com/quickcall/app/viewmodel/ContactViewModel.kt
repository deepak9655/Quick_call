package com.quickcall.app.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.quickcall.app.data.Contact
import com.quickcall.app.util.FileParsers
import com.quickcall.app.util.Parser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class ContactViewModel(app: Application) : AndroidViewModel(app) {
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _processing = MutableStateFlow(false)
    val processing: StateFlow<Boolean> = _processing.asStateFlow()

    fun addManual(name: String, phone: String, notes: String = "") {
        if (phone.isBlank()) return
        val c = Contact(name = name.ifBlank { "Unknown" }, phone = phone, notes = notes)
        _contacts.value = _contacts.value + c
        _status.value = "Added"
    }

    fun remove(id: String) { _contacts.value = _contacts.value.filter { it.id != id } }
    fun removeMany(ids: Set<String>) { _contacts.value = _contacts.value.filter { it.id !in ids } }

    fun update(contact: Contact) {
        _contacts.value = _contacts.value.map { if (it.id == contact.id) contact else it }
        _status.value = "Updated"
    }

    fun setStatus(id: String, status: String) {
        _contacts.value = _contacts.value.map { if (it.id == id) it.copy(status = status) else it }
    }

    fun toggleFavorite(id: String) {
        _contacts.value = _contacts.value.map { if (it.id == id) it.copy(favorite = !it.favorite) else it }
    }

    fun markCalled(id: String) {
        _contacts.value = _contacts.value.map { if (it.id == id) it.copy(status = "called", calledAt = System.currentTimeMillis(), callCount = it.callCount + 1) else it }
    }

    fun importText(text: String) {
        val parsed = Parser.parseText(text).map { it.copy(status = "new") }
        if (parsed.isEmpty()) { _status.value = "No contacts found"; return }
        merge(parsed)
        _status.value = "Imported ${parsed.size} contact(s)"
    }

    fun importFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _processing.value = true
            try {
                val name = queryName(context, uri).lowercase()
                val parsed = withContext(Dispatchers.IO) {
                    val text = when {
                        name.endsWith(".pdf") -> null // handled separately
                        name.endsWith(".xlsx") || name.endsWith(".xls") -> FileParsers.parseXlsx(context, uri)
                        name.endsWith(".csv") -> FileParsers.readText(context, uri)
                        name.endsWith(".docx") -> FileParsers.parseDocx(context, uri)
                        else -> FileParsers.readText(context, uri)
                    }
                    if (name.endsWith(".pdf")) parsePdf(context, uri)
                    else Parser.parseText(text ?: "")
                }
                if (parsed.isEmpty()) _status.value = "No contacts found"
                else { merge(parsed.map { it.copy(status = "new") }); _status.value = "Imported ${parsed.size} contact(s)" }
            } catch (e: Exception) {
                _status.value = "Error: ${e.message}"
            } finally {
                _processing.value = false
            }
        }
    }

    private fun merge(list: List<Contact>) {
        val seen = _contacts.value.map { Parser.cleanPhone(it.phone) }.toMutableSet()
        val toAdd = list.filter { Parser.cleanPhone(it.phone) !in seen }.also { it.forEach { c -> seen.add(Parser.cleanPhone(c.phone)) } }
        _contacts.value = _contacts.value + toAdd
    }

    private fun queryName(context: Context, uri: Uri): String {
        var result = uri.lastPathSegment ?: ""
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) result = c.getString(idx)
        }
        return result
    }

    private fun parsePdf(context: Context, uri: Uri): List<Contact> {
        val result = mutableListOf<Contact>()
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return result
        val renderer = PdfRenderer(pfd)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val sb = StringBuilder()
        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            val img = InputImage.fromBitmap(bmp, 0)
            try {
                val text = com.google.android.gms.tasks.Tasks.await(recognizer.process(img))
                sb.append(text.text).append("\n")
            } catch (e: Exception) { }
            bmp.recycle()
        }
        renderer.close()
        pfd.close()
        return Parser.parseText(sb.toString())
    }
}
