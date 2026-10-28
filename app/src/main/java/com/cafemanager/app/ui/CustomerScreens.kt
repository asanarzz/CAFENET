package com.cafemanager.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.cafemanager.app.AppViewModel
import com.cafemanager.app.PasswordDraft
import com.cafemanager.app.R
import com.cafemanager.app.data.AppDatabase
import com.cafemanager.app.data.Customer
import com.cafemanager.app.util.fa
import com.cafemanager.app.util.normDigits

@Composable
fun CustomersScreen(vm: AppViewModel, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    val list by vm.customers.collectAsState()
    val q by vm.query.collectAsState()
    var sort by remember { mutableStateOf(0) }
    // شمارهٔ ثابت هر مشتری = ترتیب ثبت (اولین مشتری ۱)؛ با جستجو یا مرتب‌سازی عوض نمی‌شود
    val ctx = LocalContext.current
    val all by remember { AppDatabase.get(ctx).customers().search("") }.collectAsState(emptyList())
    val numbers = remember(all) { all.sortedBy { it.id }.mapIndexed { i, c -> c.id to i + 1 }.toMap() }
    val now = System.currentTimeMillis()
    val sorts = listOf("جدیدترین", "قدیمی‌ترین", "الفبا (الف تا ی)", "الفبا (ی تا الف)")
    val shown = when (sort) {
        0 -> list.sortedByDescending { it.id }
        1 -> list.sortedBy { it.id }
        2 -> list.sortedWith { a, b -> faCollator.compare("${a.lastName} ${a.firstName}", "${b.lastName} ${b.firstName}") }
        else -> list.sortedWith { a, b -> faCollator.compare("${b.lastName} ${b.firstName}", "${a.lastName} ${a.firstName}") }
    }

    Column(modifier.padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ImportCustomersButton()
            Box(Modifier.weight(1f)) { SortMenu(sorts, sort) { sort = it } }
        }
        Text("تعداد کل مشتریان: ${all.size.fa()}", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = q, onValueChange = vm::setQuery,
            label = { Text("جستجو (نام، موبایل، کد ملی)") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (q.isBlank()) "هنوز مشتری ثبت نشده است" else "موردی پیدا نشد")
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(shown, key = { it.id }) { c ->
                    // مشتری‌ای که کمتر از ۲۴ ساعت از ثبتش گذشته رنگ متفاوت دارد
                    val isNew = now - c.createdAt < 24 * 60 * 60 * 1000L
                    Card(
                        onClick = { onOpen(c.id) },
                        colors = if (isNew) CardDefaults.cardColors(containerColor = Color(0xFF2E9E5B).copy(alpha = 0.25f))
                        else CardDefaults.cardColors(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${(numbers[c.id] ?: 0).fa()}.", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${c.firstName} ${c.lastName}", style = MaterialTheme.typography.titleMedium)
                                if (c.mobile.isNotBlank()) Text(c.mobile.fa(), style = MaterialTheme.typography.bodyMedium)
                            }
                            if (isNew) Text("جدید", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

// ---------------- حالت فرم ----------------
private class PwItem(s: String = "", u: String = "", p: String = "") {
    var system by mutableStateOf(s)
    var username by mutableStateOf(u)
    var password by mutableStateOf(p)
}

private class CustomerForm {
    var first by mutableStateOf("")
    var last by mutableStateOf("")
    var father by mutableStateOf("")
    var nid by mutableStateOf("")
    var mobile by mutableStateOf("")
    var eitaa by mutableStateOf("")
    var cityCode by mutableStateOf("")
    var phone by mutableStateOf("")
    var birth by mutableStateOf("")
    var birthCity by mutableStateOf("")
    var serialLetter by mutableStateOf("")
    var serialSeries by mutableStateOf("")
    var serialNumber by mutableStateOf("")
    var issueCity by mutableStateOf("")
    var issueDate by mutableStateOf("")
    var postal by mutableStateOf("")
    var address by mutableStateOf("")
    var job by mutableStateOf("")
    var education by mutableStateOf("")
    var bankName by mutableStateOf("")
    var bankBranch by mutableStateOf("")
    var branchCode by mutableStateOf("")
    var notes by mutableStateOf("")
    val ibanParts = mutableStateListOf("", "", "", "", "", "")
    val pws = mutableStateListOf<PwItem>()

    fun iban(): String = ibanParts.joinToString("")

    fun load(c: Customer) {
        first = c.firstName; last = c.lastName; father = c.fatherName; nid = c.nationalId
        mobile = c.mobile; eitaa = c.eitaaId; cityCode = c.cityCode; phone = c.phone
        birth = c.birthDate; birthCity = c.birthCity
        serialLetter = c.idSerialLetter; serialSeries = c.idSerialSeries; serialNumber = c.idSerialNumber
        issueCity = c.idIssueCity; issueDate = c.idIssueDate
        postal = c.postalCode; address = c.address; job = c.job; education = c.education
        bankName = c.bankName; bankBranch = c.bankBranch; branchCode = c.branchCode; notes = c.notes
        val d = c.iban.filter { it.isDigit() }
        for (i in 0 until 6) ibanParts[i] = d.drop(i * 4).take(4)
    }

    fun toCustomer(base: Customer?): Customer =
        (base ?: Customer(firstName = "", lastName = "")).copy(
            firstName = first.trim(), lastName = last.trim(), fatherName = father.trim(),
            nationalId = nid.normDigits(), mobile = mobile.normDigits(), eitaaId = eitaa.trim(),
            cityCode = cityCode.normDigits(), phone = phone.normDigits(),
            birthDate = birth, birthCity = birthCity.trim(),
            idSerialLetter = serialLetter.trim(), idSerialSeries = serialSeries.normDigits(),
            idSerialNumber = serialNumber.normDigits(), idIssueCity = issueCity.trim(), idIssueDate = issueDate,
            postalCode = postal.normDigits(), address = address.trim(), job = job.trim(), education = education.trim(),
            iban = iban(), bankName = bankName.trim(), bankBranch = bankBranch.trim(),
            branchCode = branchCode.normDigits(), notes = notes.trim()
        )
}

// ---------------- اجزای فرم ----------------
@Composable
private fun CField(
    label: String, value: String, onChange: (String) -> Unit, readOnly: Boolean,
    kb: KeyboardType = KeyboardType.Text, lines: Int = 1, secret: Boolean = false
) {
    val clip = LocalClipboardManager.current
    val ctx = LocalContext.current
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        readOnly = readOnly,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = kb),
        singleLine = lines == 1, minLines = lines,
        trailingIcon = {
            IconButton(onClick = { copyText(ctx, clip, value) }) {
                Icon(painterResource(R.drawable.ic_copy), "کپی")
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun DateField(label: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    Box {
        OutlinedTextField(
            value = value.fa(), onValueChange = {}, readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.DateRange, "تقویم") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        if (enabled) Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

@Composable
private fun SectionTitle(text: String) {
    HorizontalDivider()
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun IbanBox(f: CustomerForm, i: Int, editing: Boolean, modifier: Modifier) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = f.ibanParts[i],
        onValueChange = { raw ->
            val d = raw.normDigits().filter { it.isDigit() }
            if (d.length <= 4) {
                f.ibanParts[i] = d
                if (d.length == 4 && i < 5) focus.moveFocus(FocusDirection.Next)
            } else {
                // چسباندن کل شماره شبا: بین کادرها پخش می‌شود
                var k = i
                d.chunked(4).forEach {
                    if (k < 6) { f.ibanParts[k] = it; k++ }
                }
            }
        },
        readOnly = !editing, singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

@Composable
private fun IbanField(f: CustomerForm, editing: Boolean) {
    val clip = LocalClipboardManager.current
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("شماره شبا")
            IconButton(onClick = { copyText(ctx, clip, if (f.iban().isEmpty()) "" else "IR" + f.iban()) }) {
                Icon(painterResource(R.drawable.ic_copy), "کپی")
            }
        }
        // شماره شبا همیشه چپ‌به‌راست نوشته می‌شود: IR 0000 0000 ...
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("IR", style = MaterialTheme.typography.titleMedium)
                for (i in 0..2) IbanBox(f, i, editing, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("IR", style = MaterialTheme.typography.titleMedium, color = Color.Transparent)
                for (i in 3..5) IbanBox(f, i, editing, Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomerFormScreen(vm: AppViewModel, id: Long, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val f = remember { CustomerForm() }
    var base by remember { mutableStateOf<Customer?>(null) }
    var editing by remember { mutableStateOf(id == 0L) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var datePicker by remember { mutableIntStateOf(0) } // 1 تولد، 2 صدور شناسنامه
    var showPw by remember { mutableStateOf(false) }

    LaunchedEffect(id, reloadKey) {
        if (id != 0L) {
            vm.getCustomer(id)?.let { c -> base = c; f.load(c) }
            f.pws.clear()
            vm.getPasswords(id).forEach { f.pws.add(PwItem(it.system, it.username, it.password)) }
        }
    }

    fun save() {
        if (id != 0L && base == null) return
        val n = f.nid.normDigits()
        val m = f.mobile.normDigits()
        val ib = f.iban()
        error = when {
            f.first.isBlank() || f.last.isBlank() -> "نام و نام خانوادگی الزامی است"
            m.isNotEmpty() && !Regex("\\d{10,11}").matches(m) -> "شماره موبایل باید ۱۰ یا ۱۱ رقم باشد"
            n.isNotEmpty() && !Regex("\\d{10}").matches(n) -> "کد ملی باید ۱۰ رقم باشد"
            ib.isNotEmpty() && ib.length != 24 -> "شماره شبا باید ۲۴ رقم باشد (۶ کادر ۴ رقمی بعد از IR)"
            else -> null
        }
        if (error != null) return
        val drafts = f.pws.map { PasswordDraft(it.system, it.username, it.password) }
        vm.saveCustomer(f.toCustomer(base), drafts) { onClose() }
    }

    val m = f.mobile.normDigits()
    val ro = !editing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id == 0L) "مشتری جدید" else "مشتری") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") }
                },
                actions = {
                    if (base != null && !editing) TextButton(onClick = { editing = true }) {
                        Icon(Icons.Default.Edit, null)
                        Spacer(Modifier.width(4.dp))
                        Text("ویرایش")
                    }
                    if (base != null && editing) IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, "حذف")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (ro) Text("برای تغییر اطلاعات، دکمه «ویرایش» بالای صفحه را بزنید.", style = MaterialTheme.typography.bodySmall)

            CField("نام *", f.first, { f.first = it }, ro)
            CField("نام خانوادگی *", f.last, { f.last = it }, ro)
            CField("نام پدر", f.father, { f.father = it }, ro)
            CField("کد ملی", f.nid, { f.nid = it }, ro, KeyboardType.Number)

            CField("شماره موبایل", f.mobile, { f.mobile = it }, ro, KeyboardType.Phone)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = m.isNotBlank(), onClick = { dialNumber(ctx, m) }) {
                    Icon(Icons.Default.Call, null)
                    Spacer(Modifier.width(4.dp))
                    Text("تماس")
                }
                OutlinedButton(enabled = m.isNotBlank(), onClick = { smsNumber(ctx, m) }) {
                    Icon(painterResource(R.drawable.ic_sms), null)
                    Spacer(Modifier.width(4.dp))
                    Text("پیامک")
                }
                OutlinedButton(
                    enabled = m.isNotBlank() || f.eitaa.isNotBlank(),
                    onClick = { openEitaa(ctx, clip, m, f.eitaa) }
                ) { Text("ایتا") }
            }
            CField("آیدی ایتا (بدون @) برای ورود به پیوی", f.eitaa, { f.eitaa = it }, ro)

            CField("کد شهر", f.cityCode, { f.cityCode = it }, ro, KeyboardType.Number)
            CField("شماره تلفن", f.phone, { f.phone = it }, ro, KeyboardType.Phone)

            DateField("تاریخ تولد (شمسی)", f.birth, editing) { datePicker = 1 }
            CField("شهر محل تولد", f.birthCity, { f.birthCity = it }, ro)

            SectionTitle("اطلاعات شناسنامه")
            CField("حرف سریال شناسنامه", f.serialLetter, { f.serialLetter = it }, ro)
            CField("سری شناسنامه", f.serialSeries, { f.serialSeries = it }, ro, KeyboardType.Number)
            CField("سریال شناسنامه", f.serialNumber, { f.serialNumber = it }, ro, KeyboardType.Number)
            CField("شهر محل صدور شناسنامه", f.issueCity, { f.issueCity = it }, ro)
            DateField("تاریخ صدور شناسنامه (شمسی)", f.issueDate, editing) { datePicker = 2 }

            SectionTitle("آدرس و اطلاعات شغلی")
            CField("کد پستی", f.postal, { f.postal = it }, ro, KeyboardType.Number)
            CField("آدرس", f.address, { f.address = it }, ro, lines = 2)
            CField("شغل", f.job, { f.job = it }, ro)
            CField("مدرک تحصیلی", f.education, { f.education = it }, ro)

            SectionTitle("اطلاعات بانکی")
            IbanField(f, editing)
            CField("نام بانک", f.bankName, { f.bankName = it }, ro)
            CField("نام شعبه بانک", f.bankBranch, { f.bankBranch = it }, ro)
            CField("کد شعبه", f.branchCode, { f.branchCode = it }, ro, KeyboardType.Number)

            SectionTitle("رمز عبور سامانه‌ها")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { showPw = !showPw }) { Text(if (showPw) "پنهان کردن رمزها" else "نمایش رمزها") }
                if (editing) FilledTonalButton(onClick = { f.pws.add(PwItem()) }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("رمز جدید")
                }
            }
            if (f.pws.isEmpty()) Text("هنوز رمزی ثبت نشده است.", style = MaterialTheme.typography.bodySmall)
            f.pws.forEach { p ->
                key(p) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (editing) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                IconButton(onClick = { f.pws.remove(p) }) { Icon(Icons.Default.Close, "حذف این رمز") }
                            }
                            CField("نام سامانه", p.system, { p.system = it }, ro)
                            CField("نام کاربری", p.username, { p.username = it }, ro)
                            CField("رمز عبور", p.password, { p.password = it }, ro, secret = !showPw)
                        }
                    }
                }
            }

            CField("توضیحات", f.notes, { f.notes = it }, ro, lines = 3)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (editing) {
                Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text("ذخیره") }
                if (base != null) OutlinedButton(
                    onClick = { editing = false; error = null; reloadKey++ },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("انصراف از ویرایش") }
            }
        }
    }

    if (datePicker != 0) {
        val isBirth = datePicker == 1
        JalaliDatePickerDialog(
            initial = if (isBirth) f.birth else f.issueDate,
            onPick = { if (isBirth) f.birth = it else f.issueDate = it; datePicker = 0 },
            onClear = { if (isBirth) f.birth = "" else f.issueDate = ""; datePicker = 0 },
            onDismiss = { datePicker = 0 }
        )
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("حذف مشتری") },
        text = { Text("مشتری از فهرست حذف می‌شود. اطلاعات او در سطل زباله نگهداری می‌شود و پاک نمی‌شود.") },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false
                base?.let { vm.trashCustomer(it) { onClose() } }
            }) { Text("حذف") }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("انصراف") } }
    )
}
