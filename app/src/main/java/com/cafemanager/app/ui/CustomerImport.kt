package com.cafemanager.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.room.withTransaction
import com.cafemanager.app.data.ActivityEntry
import com.cafemanager.app.data.AppDatabase
import com.cafemanager.app.data.Customer
import com.cafemanager.app.util.fa
import com.cafemanager.app.util.normDigits
import com.cafemanager.app.util.validNationalId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** نتیجهٔ خواندن فایل: مشتری‌های آمادهٔ ثبت + آمار برای پیش‌نمایش */
private class ImportPlan(
    val fresh: List<Customer>,
    val duplicates: Int,
    val noName: Int,
    val badNid: Int
)

/** خواندن CSV (UTF-8، جداکنندهٔ , یا ; یا Tab) */
private fun parseCsv(raw: String): List<List<String>> {
    val text = raw.removePrefix("﻿")
    val firstLine = text.lineSequence().firstOrNull().orEmpty()
    val delim = listOf(',', ';', '\t', '،', '؛').maxByOrNull { d -> firstLine.count { it == d } }
        ?.takeIf { d -> firstLine.contains(d) } ?: ','
    val rows = ArrayList<List<String>>()
    var row = ArrayList<String>()
    val cell = StringBuilder()
    var inQuotes = false
    var i = 0
    fun endCell() { row.add(cell.toString()); cell.setLength(0) }
    fun endRow() {
        endCell()
        if (row.any { it.isNotBlank() }) rows.add(row)
        row = ArrayList()
    }
    while (i < text.length) {
        val ch = text[i]
        when {
            inQuotes -> when {
                ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                ch == '"' -> inQuotes = false
                else -> cell.append(ch)
            }
            ch == '"' -> inQuotes = true
            ch == delim -> endCell()
            ch == '\n' -> endRow()
            ch == '\r' -> {}
            else -> cell.append(ch)
        }
        i++
    }
    if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
    return rows
}

private fun normHeader(s: String): String = s.trim()
    .replace('ي', 'ی').replace('ك', 'ک')
    .replace("‌", "").replace(" ", "").replace("_", "").replace("*", "")
    .lowercase()

/** عنوان ستون‌ها (ترتیب پیش‌فرض فایل نمونه) و نام‌های هم‌معنی */
private val columns: List<Pair<String, List<String>>> = listOf(
    "first" to listOf("نام", "firstname"),
    "last" to listOf("نامخانوادگی", "فامیل", "lastname"),
    "father" to listOf("نامپدر", "پدر", "father"),
    "nid" to listOf("کدملی", "شمارهملی", "nationalid"),
    "mobile" to listOf("موبایل", "شمارهموبایل", "همراه", "شمارههمراه", "mobile"),
    "eitaa" to listOf("ایتا", "آیدیایتا", "eitaa"),
    "cityCode" to listOf("کدشهر", "پیششماره"),
    "phone" to listOf("تلفن", "شمارهتلفن", "phone"),
    "birth" to listOf("تاریختولد", "تولد", "birthdate"),
    "birthCity" to listOf("شهرتولد", "محلتولد", "شهرمحلتولد"),
    "issueCity" to listOf("شهرصدور", "محلصدور"),
    "issueDate" to listOf("تاریخصدور"),
    "serialLetter" to listOf("حرفسریال", "حرفسریالشناسنامه"),
    "serialSeries" to listOf("سریسریال", "سری"),
    "serialNumber" to listOf("شمارهسریال", "سریال"),
    "job" to listOf("شغل"),
    "education" to listOf("تحصیلات", "مدرک"),
    "iban" to listOf("شبا", "شمارهشبا", "iban"),
    "bankName" to listOf("بانک", "نامبانک"),
    "bankBranch" to listOf("شعبه", "نامشعبه"),
    "branchCode" to listOf("کدشعبه"),
    "postal" to listOf("کدپستی"),
    "address" to listOf("آدرس", "نشانی"),
    "notes" to listOf("توضیحات", "یادداشت")
)

private fun fixMobile(s: String): String {
    var d = s.normDigits().filter { it.isDigit() }
    if (d.startsWith("0098")) d = "0" + d.drop(4)
    else if (d.startsWith("98") && d.length == 12) d = "0" + d.drop(2)
    if (d.length == 10 && d.startsWith("9")) d = "0$d"
    return d
}

private fun fixNid(s: String): String {
    val d = s.normDigits().filter { it.isDigit() }
    return if (d.length in 8..9) d.padStart(10, '0') else d
}

private fun fixDate(s: String): String = s.normDigits().trim().replace('-', '/').replace('.', '/')

private fun buildPlan(rows: List<List<String>>, existingNid: Set<String>, existingMobile: Set<String>): ImportPlan {
    if (rows.isEmpty()) return ImportPlan(emptyList(), 0, 0, 0)
    val keys = columns.map { it.first }
    // اگر ردیف اول عنوان ستون باشد از آن استفاده می‌شود، وگرنه ترتیب فایل نمونه فرض می‌شود
    val head = rows[0].map { normHeader(it) }
    val map = HashMap<String, Int>()
    columns.forEach { (key, names) ->
        val idx = head.indexOfFirst { h -> names.any { normHeader(it) == h } }
        if (idx >= 0) map[key] = idx
    }
    val hasHeader = map.size >= 2
    val data = if (hasHeader) rows.drop(1) else rows
    if (!hasHeader) keys.forEachIndexed { i, k -> map[k] = i }

    fun List<String>.col(key: String): String = map[key]?.let { getOrNull(it) }.orEmpty().trim()

    val seenNid = HashSet(existingNid)
    val seenMobile = HashSet(existingMobile)
    val fresh = ArrayList<Customer>()
    var dup = 0
    var noName = 0
    var bad = 0
    for (r in data) {
        val first = r.col("first")
        val last = r.col("last")
        if (first.isBlank() || last.isBlank()) { noName++; continue }
        val nid = fixNid(r.col("nid"))
        val mobile = fixMobile(r.col("mobile"))
        if ((nid.isNotEmpty() && nid in seenNid) || (mobile.isNotEmpty() && mobile in seenMobile)) { dup++; continue }
        if (nid.isNotEmpty()) seenNid.add(nid)
        if (mobile.isNotEmpty()) seenMobile.add(mobile)
        if (nid.isNotEmpty() && !validNationalId(nid)) bad++
        val ibanDigits = r.col("iban").normDigits().filter { it.isDigit() }
        fresh.add(
            Customer(
                firstName = first, lastName = last, fatherName = r.col("father"),
                nationalId = nid, mobile = mobile,
                eitaaId = r.col("eitaa").removePrefix("@"),
                cityCode = r.col("cityCode").normDigits(), phone = r.col("phone").normDigits(),
                birthDate = fixDate(r.col("birth")), birthCity = r.col("birthCity"),
                idIssueCity = r.col("issueCity"), idIssueDate = fixDate(r.col("issueDate")),
                idSerialLetter = r.col("serialLetter"), idSerialSeries = r.col("serialSeries").normDigits(),
                idSerialNumber = r.col("serialNumber").normDigits(),
                job = r.col("job"), education = r.col("education"),
                iban = ibanDigits.take(24), bankName = r.col("bankName"),
                bankBranch = r.col("bankBranch"), branchCode = r.col("branchCode").normDigits(),
                postalCode = r.col("postal").normDigits(), address = r.col("address"),
                notes = r.col("notes")
            )
        )
    }
    return ImportPlan(fresh, dup, noName, bad)
}

private suspend fun readPlan(ctx: Context, uri: Uri): ImportPlan = withContext(Dispatchers.IO) {
    val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        ?: throw IllegalStateException("فایل خوانده نشد")
    val db = AppDatabase.get(ctx)
    val old = db.customers().search("").first()
    buildPlan(
        parseCsv(text),
        old.map { it.nationalId }.filter { it.isNotEmpty() }.toSet(),
        old.map { it.mobile }.filter { it.isNotEmpty() }.toSet()
    )
}

/**
 * دکمهٔ «ورود از فایل» برای صفحهٔ مشتریان.
 * فایل CSV انتخاب می‌شود، پیش‌نمایش آمار نشان داده می‌شود و پس از تأیید همه یک‌جا ثبت می‌شوند.
 */
@Composable
fun ImportCustomersButton() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var plan by remember { mutableStateOf<ImportPlan?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                try {
                    plan = readPlan(ctx, uri)
                } catch (e: Exception) {
                    message = "خواندن فایل انجام نشد. فایل باید CSV با کدگذاری UTF-8 باشد."
                }
                busy = false
            }
        }
    }

    TextButton(enabled = !busy, onClick = { picker.launch(arrayOf("*/*")) }) {
        Text("ورود از فایل")
    }

    plan?.let { p ->
        AlertDialog(
            onDismissRequest = { plan = null },
            title = { Text("پیش‌نمایش ورود مشتری‌ها") },
            text = {
                Column {
                    Text("آمادهٔ ثبت: ${p.fresh.size.fa()} مشتری")
                    if (p.duplicates > 0) Text("تکراری (رد می‌شود): ${p.duplicates.fa()}")
                    if (p.noName > 0) Text("بدون نام یا نام خانوادگی (رد می‌شود): ${p.noName.fa()}")
                    if (p.badNid > 0) Text("کد ملی نامعتبر (ثبت می‌شود، بعداً اصلاح کنید): ${p.badNid.fa()}")
                }
            },
            confirmButton = {
                TextButton(
                    enabled = p.fresh.isNotEmpty() && !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                val db = AppDatabase.get(ctx)
                                db.withTransaction {
                                    p.fresh.forEach { db.customers().insert(it) }
                                    db.activity().insert(
                                        ActivityEntry(type = "customer_add", message = "ورود از فایل: ${p.fresh.size} مشتری اضافه شد")
                                    )
                                }
                                message = "${p.fresh.size.fa()} مشتری ثبت شد."
                            } catch (e: Exception) {
                                message = "ثبت انجام نشد. دوباره تلاش کنید."
                            }
                            plan = null
                            busy = false
                        }
                    }
                ) { Text("ثبت همه") }
            },
            dismissButton = { TextButton(onClick = { plan = null }) { Text("انصراف") } }
        )
    }

    message?.let { m ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(m) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("باشه") } }
        )
    }
}
