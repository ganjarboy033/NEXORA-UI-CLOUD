package dev.gsprasada.nexoracloud

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.MultiFormatWriter

private val LoginBackground = Color(0xFF171D1D)
private val LoginSurface = Color(0xFF244247)
private val LoginAccent = Color(0xFF55D2EE)
private val LoginText = Color(0xFFE6ECEC)
private val LoginMuted = Color(0xFF9AA4A5)

/** Login akun Telegram nyata (TDLib): nomor HP, kode OTP, password 2FA, registrasi, dan QR. */
class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TdClient.start(applicationContext)
        setContent { LoginPage(onClose = { finish() }) }
    }
}

@Composable
private fun LoginPage(onClose: () -> Unit, vm: TelegramAuthViewModel = viewModel()) {
    val state by vm.authState.collectAsState()
    val ui by vm.ui.collectAsState()
    val busy = ui.busy
    val error = ui.error
    val notice = ui.notice
    val target = ui.storageTarget
    var editingPhone by remember { mutableStateOf(false) }
    var countryCode by remember { mutableStateOf("62") }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var confirmLogout by remember { mutableStateOf(false) }
    var chatRef by remember { mutableStateOf("") }
    var showApiDialog by remember { mutableStateOf(false) }
    var apiIdText by remember { mutableStateOf("") }
    var apiHashText by remember { mutableStateOf("") }

    // Setiap perpindahan state login membersihkan input sensitif dan kesalahan sebelumnya.
    LaunchedEffect(state::class) {
        code = ""; password = ""; editingPhone = false; vm.clearError()
    }
    LaunchedEffect(state is TdAuthState.Ready) {
        if (state is TdAuthState.Ready) vm.loadStorageTarget()
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = LoginAccent, background = LoginBackground, surface = LoginBackground)) {
        Column(
            modifier = Modifier.fillMaxSize().background(LoginBackground).systemBarsPadding()
                .padding(horizontal = 22.dp, vertical = 12.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = LoginText) }
                Text("Telegram", color = LoginText, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            }
            LoginSteps(state)
            Spacer(Modifier.height(20.dp))

            val current = state
            when {
                current is TdAuthState.Starting || current is TdAuthState.LoggingOut -> {
                    Title("Menyiapkan Telegram…")
                    Spacer(Modifier.height(24.dp))
                    CircularProgressIndicator(color = LoginAccent)
                }

                current is TdAuthState.MissingCredentials -> {
                    Title("API Telegram belum diisi")
                    Hint("Login membutuhkan API ID dan API Hash dari my.telegram.org/apps. Isi lewat Pengaturan > Konfigurasi aplikasi Telegram, atau berikan variabel build TELEGRAM_API_ID dan TELEGRAM_API_HASH, lalu buka kembali halaman ini.")
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { apiIdText = vm.currentApiId(); apiHashText = vm.currentApiHash(); showApiDialog = true }) { Text("Isi API ID / API Hash") }
                    OutlinedButton(onClick = { vm.retryStart() }) { Text("Periksa ulang") }
                }

                current is TdAuthState.Failed -> {
                    Title("Telegram tidak dapat dimulai")
                    Hint(current.message)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { vm.retryStart() }) { Text("Coba lagi") }
                }

                current is TdAuthState.WaitPhone || (editingPhone && (current is TdAuthState.WaitCode || current is TdAuthState.WaitPassword)) -> {
                    Title("Nomor telepon Anda")
                    Hint("Masuk menggunakan akun Telegram Anda")
                    Spacer(Modifier.height(28.dp))
                    Row(
                        Modifier.fillMaxWidth().background(LoginSurface, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("+", color = LoginText, fontSize = 18.sp)
                        LoginField(countryCode, { countryCode = it.filter(Char::isDigit).take(4) }, "Kode", KeyboardType.Number, Modifier.width(72.dp))
                        Spacer(Modifier.width(8.dp))
                        LoginField(phone, { phone = it.filter(Char::isDigit).take(15) }, "Nomor telepon", KeyboardType.Phone, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(24.dp))
                    OutlinedButton(
                        onClick = { vm.requestQr() },
                        enabled = !busy && current is TdAuthState.WaitPhone,
                        modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(40.dp)
                    ) { Icon(Icons.Default.QrCode2, null); Spacer(Modifier.width(10.dp)); Text("Login dengan kode QR") }
                    Spacer(Modifier.height(24.dp))
                    val digits = phone.trimStart('0')
                    Button(
                        onClick = { vm.submitPhone(countryCode, phone) },
                        enabled = !busy && countryCode.isNotEmpty() && digits.length >= 6,
                        modifier = Modifier.align(Alignment.End).size(72.dp), shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LoginAccent, contentColor = Color(0xFF07323C))
                    ) { Icon(Icons.Default.ArrowForward, contentDescription = "Lanjut", modifier = Modifier.size(28.dp)) }
                    if (current is TdAuthState.WaitPhone) {
                        TextButton(onClick = { apiIdText = vm.currentApiId(); apiHashText = vm.currentApiHash(); showApiDialog = true }) { Text("Ubah API ID / API Hash") }
                    }
                }

                current is TdAuthState.WaitCode -> {
                    Title("Masukkan kode")
                    Hint("Kode dikirim ke ${current.phone.ifBlank { "nomor Anda" }} melalui ${current.delivery}.")
                    Spacer(Modifier.height(24.dp))
                    LoginBox { LoginField(code, { code = it.filter(Char::isDigit).take(8) }, "Kode verifikasi", KeyboardType.Number, Modifier.fillMaxWidth()) }
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { vm.submitCode(code) }, enabled = !busy && code.length >= 4, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Verifikasi") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { editingPhone = true }, enabled = !busy) { Text("Ganti nomor") }
                        TextButton(onClick = { vm.resendCode() }, enabled = !busy) { Text("Kirim ulang kode") }
                    }
                }

                current is TdAuthState.WaitPassword -> {
                    Title("Verifikasi dua langkah")
                    Hint(if (current.hint.isNotBlank()) "Petunjuk password: ${current.hint}" else "Akun ini dilindungi password tambahan.")
                    Spacer(Modifier.height(24.dp))
                    LoginBox { LoginField(password, { password = it }, "Password", KeyboardType.Password, Modifier.fillMaxWidth(), secret = true) }
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { vm.submitPassword(password) }, enabled = !busy && password.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Masuk") }
                    TextButton(onClick = { editingPhone = true }, enabled = !busy) { Text("Ganti nomor") }
                }

                current is TdAuthState.WaitRegistration -> {
                    Title("Buat akun Telegram")
                    Hint("Nomor ini belum terdaftar. Isi nama untuk membuat akun baru.")
                    Spacer(Modifier.height(20.dp))
                    LoginBox { LoginField(firstName, { firstName = it.take(64) }, "Nama depan", KeyboardType.Text, Modifier.fillMaxWidth()) }
                    Spacer(Modifier.height(10.dp))
                    LoginBox { LoginField(lastName, { lastName = it.take(64) }, "Nama belakang (opsional)", KeyboardType.Text, Modifier.fillMaxWidth()) }
                    if (current.terms.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(current.terms, color = LoginMuted, fontSize = 11.sp) }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { vm.submitRegistration(firstName, lastName) }, enabled = !busy && firstName.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Setuju dan lanjut") }
                }

                current is TdAuthState.WaitEmailAddress -> {
                    Title("Alamat email")
                    Hint("Telegram meminta email untuk mengamankan login.")
                    Spacer(Modifier.height(20.dp))
                    LoginBox { LoginField(email, { email = it.trim() }, "Email", KeyboardType.Email, Modifier.fillMaxWidth()) }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { vm.submitEmail(email) }, enabled = !busy && email.contains("@"), modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Kirim kode") }
                }

                current is TdAuthState.WaitEmailCode -> {
                    Title("Kode dari email")
                    Hint("Masukkan kode yang dikirim Telegram ke email Anda.")
                    Spacer(Modifier.height(20.dp))
                    LoginBox { LoginField(code, { code = it.filter(Char::isDigit).take(10) }, "Kode email", KeyboardType.Number, Modifier.fillMaxWidth()) }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { vm.submitEmailCode(code) }, enabled = !busy && code.length >= 4, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Verifikasi") }
                }

                current is TdAuthState.WaitQr -> {
                    Title("Login dengan kode QR")
                    Hint("Di ponsel lain buka Telegram > Pengaturan > Perangkat > Hubungkan perangkat desktop, lalu pindai kode ini.")
                    Spacer(Modifier.height(20.dp))
                    val bitmap = remember(current.link) { makeQrBitmap(current.link) }
                    Box(Modifier.size(260.dp).background(Color.White, RoundedCornerShape(16.dp)).padding(10.dp), contentAlignment = Alignment.Center) {
                        if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = "QR login Telegram", modifier = Modifier.fillMaxSize())
                        else Text("QR gagal dibuat", color = Color.Black)
                    }
                    Spacer(Modifier.height(16.dp))
                    Hint("Kode QR diperbarui otomatis oleh Telegram bila kedaluwarsa.")
                    TextButton(onClick = { vm.leaveQr() }) { Text("Gunakan nomor telepon") }
                }

                current is TdAuthState.Ready -> {
                    Title("Terhubung")
                    Hint(current.displayName.ifBlank { "Akun Telegram" })
                    Spacer(Modifier.height(24.dp))
                    Column(Modifier.fillMaxWidth().background(LoginSurface, RoundedCornerShape(14.dp)).padding(16.dp)) {
                        Text("Chat penyimpanan", color = LoginMuted, fontSize = 12.sp)
                        Text(target?.title ?: "Memuat…", color = LoginText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(
                            if (target?.isSavedMessages == false) "File disimpan sebagai dokumen di chat/channel ini."
                            else "File disimpan sebagai dokumen di Saved Messages akun Anda.",
                            color = LoginMuted, fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(onClick = { vm.useSavedMessages() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Pakai Saved Messages") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { vm.createChannel() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Buat channel privat \"Nexora Cloud\"") }
                    Spacer(Modifier.height(8.dp))
                    LoginBox { LoginField(chatRef, { chatRef = it.trim() }, "ID chat atau @username channel", KeyboardType.Text, Modifier.fillMaxWidth()) }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { vm.useChat(chatRef) }, enabled = !busy && chatRef.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Pakai chat ini") }
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Selesai") }
                    TextButton(onClick = { confirmLogout = true }, enabled = !busy) { Text("Keluar dari akun Telegram", color = Color(0xFFFF8A80)) }
                }
            }

            if (busy) { Spacer(Modifier.height(16.dp)); CircularProgressIndicator(color = LoginAccent, modifier = Modifier.size(24.dp)) }
            if (error.isNotBlank()) { Spacer(Modifier.height(12.dp)); Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, textAlign = TextAlign.Center) }
            if (notice.isNotBlank()) { Spacer(Modifier.height(12.dp)); Text(notice, color = LoginAccent, fontSize = 13.sp, textAlign = TextAlign.Center) }
        }

        if (showApiDialog) {
            AlertDialog(
                onDismissRequest = { showApiDialog = false },
                title = { Text("Kredensial aplikasi Telegram") },
                text = { Column {
                    Text("Dari my.telegram.org/apps. Menyimpan akan memulai ulang sesi login.", fontSize = 12.sp)
                    OutlinedTextField(value = apiIdText, onValueChange = { apiIdText = it.filter(Char::isDigit).take(10) }, singleLine = true,
                        label = { Text("API ID") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = apiHashText, onValueChange = { apiHashText = it.trim().take(32) }, singleLine = true, label = { Text("API Hash") })
                } },
                confirmButton = { TextButton(onClick = { vm.saveCredentials(apiIdText, apiHashText); showApiDialog = false }) { Text("Simpan") } },
                dismissButton = { TextButton(onClick = { showApiDialog = false }) { Text("Batal") } }
            )
        }
        if (confirmLogout) {
            AlertDialog(
                onDismissRequest = { confirmLogout = false },
                title = { Text("Keluar dari Telegram?") },
                text = { Text("Sesi di perangkat ini dihapus. File Anda tetap aman di Telegram dan muncul lagi saat Anda login kembali.") },
                confirmButton = {
                    TextButton(onClick = { confirmLogout = false; vm.logOut() }) { Text("Keluar", color = Color(0xFFFF8A80)) }
                },
                dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Batal") } }
            )
        }
    }
}

/** Indikator langkah login (visual saja): Nomor HP, OTP, 2FA, QR, Daftar, Target Chat. */
@Composable
private fun LoginSteps(state: TdAuthState) {
    val steps = listOf("Nomor HP", "OTP", "2FA", "QR", "Daftar", "Target Chat")
    val active = when (state) {
        is TdAuthState.WaitPhone -> 0
        is TdAuthState.WaitCode, is TdAuthState.WaitEmailAddress, is TdAuthState.WaitEmailCode -> 1
        is TdAuthState.WaitPassword -> 2
        is TdAuthState.WaitQr -> 3
        is TdAuthState.WaitRegistration -> 4
        is TdAuthState.Ready -> 5
        else -> -1
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        steps.forEachIndexed { index, label ->
            Text(
                label, fontSize = 11.sp, fontWeight = if (index == active) FontWeight.Bold else FontWeight.Normal,
                color = if (index == active) Color(0xFF07323C) else LoginMuted,
                modifier = Modifier.background(if (index == active) LoginAccent else LoginSurface, RoundedCornerShape(16.dp)).padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable private fun Title(text: String) = Text(text, color = LoginText, fontSize = 23.sp, textAlign = TextAlign.Center)

@Composable private fun Hint(text: String) =
    Text(text, color = LoginMuted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))

@Composable private fun LoginBox(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().background(LoginSurface, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) { content() }
}

@Composable
private fun LoginField(value: String, onChange: (String) -> Unit, label: String, type: KeyboardType, modifier: Modifier, secret: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier, singleLine = true,
        placeholder = { Text(label, color = LoginMuted) },
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = LoginText, unfocusedTextColor = LoginText,
            focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent, cursorColor = LoginAccent
        )
    )
}

private fun makeQrBitmap(value: String): Bitmap? = runCatching {
    val size = 600
    val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size)
    for (y in 0 until size) for (x in 0 until size) pixels[y * size + x] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}.getOrNull()
