package com.pasoseguro.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.pasoseguro.app.navigation.Feature
import com.pasoseguro.app.ui.LocalUserPreferences
import com.pasoseguro.app.ui.theme.ContactGreen
import com.pasoseguro.app.ui.theme.ContactGreen50
import com.pasoseguro.app.ui.theme.TextOnDark
import com.pasoseguro.app.utils.HapticHelper
import com.pasoseguro.app.utils.TtsHelper

// ── Sample data ─────────────────────────────────────────────────────────────

private data class Contact(
    val name: String,
    val phone: String,
    val relation: String,
    val avatarColor: Color,
)

private val sampleContacts = listOf(
    Contact("María González",   "+51 987 654 321", "Familiar", Color(0xFF1565C0)),
    Contact("Carlos Ramírez",   "+51 912 345 678", "Cuidador", Color(0xFF2E7D32)),
    Contact("Ana Torres",       "+51 998 877 665", "Amiga",    Color(0xFF6A1B9A)),
    Contact("Luis Fernández",   "+51 945 612 378", "Familiar", Color(0xFFE65100)),
    Contact("Emergencias SAMU", "106",             "Servicio", Color(0xFFC62828)),
)

// ── Screen ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs   = LocalUserPreferences.current
    val tts     = remember { TtsHelper(context) }
    val feature = Feature.CONTACTS

    DisposableEffect(Unit) {
        tts.enabled = prefs.ttsEnabled
        tts.setSpeed(prefs.ttsSpeed)
        tts.speak("Pantalla de contactos de confianza.")
        onDispose { tts.shutdown() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = "Contactos",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 20.sp,
                        color      = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector        = Icons.Filled.ArrowBackIosNew,
                            contentDescription = "Volver al inicio",
                            tint               = feature.tint,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    HapticHelper.vibrate(context, prefs.hapticEnabled)
                    tts.speak("Agregar nuevo contacto. Esta función estará disponible próximamente.")
                },
                containerColor = feature.tint,
                contentColor   = TextOnDark,
                modifier       = Modifier
                    .height(60.dp)
                    .semantics { contentDescription = "Agregar nuevo contacto de confianza" },
            ) {
                Icon(
                    imageVector        = Icons.Filled.PersonAdd,
                    contentDescription = null,
                    modifier           = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text       = "Agregar",
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 16.sp,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text     = "Personas que recibirán tus alertas de emergencia",
                    fontSize = 14.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp, start = 4.dp),
                )
            }
            items(sampleContacts) { contact ->
                ContactCard(
                    contact = contact,
                    onTap   = {
                        HapticHelper.vibrate(context, prefs.hapticEnabled)
                        tts.speak("${contact.name}. ${contact.relation}. Teléfono ${contact.phone}.")
                    },
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ── Contact card ──────────────────────────────────────────────────────────

@Composable
private fun ContactCard(contact: Contact, onTap: () -> Unit) {
    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription =
                    "${contact.name}, ${contact.relation}, teléfono ${contact.phone}"
            },
        shape     = RoundedCornerShape(18.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick   = onTap,
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(contact.avatarColor),
            ) {
                Text(
                    text       = contact.name.split(" ")
                        .take(2)
                        .mapNotNull { it.firstOrNull()?.uppercase() }
                        .joinToString(""),
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 20.sp,
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = contact.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 17.sp,
                    color      = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text     = contact.phone,
                    fontSize = 15.sp,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text       = contact.relation,
                    fontSize   = 12.sp,
                    color      = contact.avatarColor,
                    fontWeight = FontWeight.Medium,
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(ContactGreen50),
            ) {
                Icon(
                    imageVector        = Icons.Filled.Call,
                    contentDescription = "Llamar a ${contact.name}",
                    tint               = ContactGreen,
                    modifier           = Modifier.size(22.dp),
                )
            }
        }
    }
}
