package com.pasoseguro.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewList
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
import com.pasoseguro.app.components.AssistantMicButton
import com.pasoseguro.app.components.BarAction
import com.pasoseguro.app.components.ProceduralBottomBar
import com.pasoseguro.app.navigation.Feature
import com.pasoseguro.app.ui.LocalUserPreferences
import com.pasoseguro.app.ui.theme.ContactGreen
import com.pasoseguro.app.ui.theme.ContactGreen50
import com.pasoseguro.app.utils.HapticHelper
import com.pasoseguro.app.voice.ScreenVoiceCommand
import com.pasoseguro.app.voice.ScreenVoiceContext
import com.pasoseguro.app.voice.VoiceInteractionState
import com.pasoseguro.app.voice.rememberAutoListenVoice
import kotlinx.coroutines.launch

// ── Sample data ─────────────────────────────────────────────────────────────

private data class Contact(
    val name: String,
    val phone: String,
    val relation: String,
    val avatarColor: Color,
    val isFavorite: Boolean = false,
)

private val sampleContacts = listOf(
    Contact("María González",   "+51 987 654 321", "Familiar", Color(0xFF1565C0), isFavorite = true),
    Contact("Carlos Ramírez",   "+51 912 345 678", "Cuidador", Color(0xFF2E7D32), isFavorite = true),
    Contact("Ana Torres",       "+51 998 877 665", "Amiga",    Color(0xFF6A1B9A)),
    Contact("Luis Fernández",   "+51 945 612 378", "Familiar", Color(0xFFE65100)),
    Contact("Emergencias SAMU", "106",             "Servicio", Color(0xFFC62828)),
)

private enum class ContactViewMode { LIST, CAROUSEL }

// ── Screen ──────────────────────────────────────────────────────────────────

private const val ADD_CONTACT_STUB = "Agregar nuevo contacto. Esta función estará disponible próximamente."
private const val CALL_CONTACT_STUB =
    "Función de llamada por voz disponible próximamente. Toca el ícono de llamada junto a un contacto."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs   = LocalUserPreferences.current
    val feature = Feature.CONTACTS

    val contactsVoiceContext = remember {
        ScreenVoiceContext(
            screenName = "Contactos",
            commands = listOf(
                ScreenVoiceCommand(
                    keywords           = listOf("agregar contacto", "anadir contacto", "nuevo contacto"),
                    onRecognized       = {},
                    confirmationSpeech = ADD_CONTACT_STUB,
                ),
                ScreenVoiceCommand(
                    keywords           = listOf("llamar contacto", "llamar", "hacer una llamada"),
                    onRecognized       = {},
                    confirmationSpeech = CALL_CONTACT_STUB,
                ),
            ),
            helpHint = "En esta pantalla puedes decir: Agregar contacto, o Llamar contacto.",
        )
    }
    val voice = rememberAutoListenVoice(contactsVoiceContext)
    val voiceState by voice.state.collectAsState()

    LaunchedEffect(Unit) {
        // Sin "Contactos": el micrófono se arma casi al mismo tiempo que este
        // mensaje suena (ver VoiceCommand.kt).
        // flush=false: no cortar la confirmación de navegación ("Abriendo
        // Contactos"/"Aquí tienes...") que puede seguir sonando al entrar.
        voice.speak("Aquí puedes administrar a las personas de confianza.", flush = false)
    }

    var viewMode by remember { mutableStateOf(ContactViewMode.LIST) }
    var favoritesOnly by remember { mutableStateOf(false) }

    val visibleContacts = remember(favoritesOnly) {
        if (favoritesOnly) sampleContacts.filter { it.isFavorite } else sampleContacts
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
                actions = {
                    AssistantMicButton(
                        pending = voiceState != VoiceInteractionState.Idle,
                        onClick = voice::requestHelp,
                        tint    = feature.tint,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            ProceduralBottomBar(
                left = BarAction(
                    icon           = Icons.Filled.PersonAdd,
                    label          = "Añadir",
                    pendingMessage = "Has seleccionado Añadir contacto. Presiona dos veces para confirmar.",
                    onConfirm      = {
                        voice.speak(ADD_CONTACT_STUB)
                    },
                ),
                center = if (viewMode == ContactViewMode.LIST) {
                    BarAction(
                        icon           = Icons.Filled.ViewCarousel,
                        label          = "Carrusel",
                        pendingMessage = "Has seleccionado Modo Carrusel. Presiona dos veces para confirmar.",
                        onConfirm      = {
                            viewMode = ContactViewMode.CAROUSEL
                            voice.speak("Modo Carrusel seleccionado.")
                        },
                    )
                } else {
                    BarAction(
                        icon           = Icons.Filled.ViewList,
                        label          = "Lista",
                        pendingMessage = "Has seleccionado Modo Lista. Presiona dos veces para confirmar.",
                        onConfirm      = {
                            viewMode = ContactViewMode.LIST
                            voice.speak("Modo Lista seleccionado.")
                        },
                    )
                },
                right = BarAction(
                    icon           = Icons.Filled.Star,
                    label          = "Favoritos",
                    selected       = favoritesOnly,
                    pendingMessage = "Has seleccionado Favoritos. Presiona dos veces para confirmar.",
                    onConfirm      = {
                        favoritesOnly = !favoritesOnly
                        voice.speak(
                            if (favoritesOnly) "Mostrando solo contactos favoritos."
                            else "Mostrando todos los contactos."
                        )
                    },
                ),
                accentColor   = feature.tint,
                onSpeak       = voice::speak,
                hapticEnabled = prefs.hapticEnabled,
                vibrationIntensity = prefs.vibrationIntensity,
                modifier      = Modifier.fillMaxWidth().navigationBarsPadding(),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (viewMode) {
            ContactViewMode.LIST -> ContactsListView(
                contacts = visibleContacts,
                padding  = padding,
                onTap    = { contact ->
                    HapticHelper.vibrate(context, prefs.hapticEnabled, prefs.vibrationIntensity)
                    voice.speak("${contact.name}. ${contact.relation}. Teléfono ${contact.phone}.")
                },
            )
            ContactViewMode.CAROUSEL -> ContactsCarouselView(
                contacts = visibleContacts,
                padding  = padding,
                onSpeak  = voice::speak,
            )
        }
    }
}

// ── List view ───────────────────────────────────────────────────────────────

@Composable
private fun ContactsListView(
    contacts: List<Contact>,
    padding: PaddingValues,
    onTap: (Contact) -> Unit,
) {
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
        if (contacts.isEmpty()) {
            item { EmptyFavoritesState() }
        } else {
            items(contacts) { contact ->
                ContactCard(contact = contact, onTap = { onTap(contact) })
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun EmptyFavoritesState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector        = Icons.Filled.Star,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            modifier           = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text     = "Aún no tienes contactos favoritos.",
            fontSize = 15.sp,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Carousel view ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactsCarouselView(
    contacts: List<Contact>,
    padding: PaddingValues,
    onSpeak: (String) -> Unit,
) {
    if (contacts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            EmptyFavoritesState()
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { contacts.size })

    LaunchedEffect(pagerState.currentPage, contacts.size) {
        val contact = contacts.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        onSpeak(contact.name)
    }

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))

        HorizontalPager(
            state    = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            val contact = contacts[page]
            ContactCarouselCard(contact = contact)
        }

        CarouselNavRow(
            pagerState = pagerState,
            total      = contacts.size,
        )

        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarouselNavRow(
    pagerState: androidx.compose.foundation.pager.PagerState,
    total: Int,
) {
    val scope = rememberCoroutineScope()
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = {
                scope.launch {
                    val prev = (pagerState.currentPage - 1 + total) % total
                    pagerState.animateScrollToPage(prev)
                }
            },
            modifier = Modifier
                .size(56.dp)
                .semantics { contentDescription = "Contacto anterior" },
        ) {
            Icon(
                imageVector        = Icons.Filled.ChevronLeft,
                contentDescription = null,
                modifier           = Modifier.size(32.dp),
            )
        }

        Text(
            text     = "${pagerState.currentPage + 1} / $total",
            fontSize = 14.sp,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        IconButton(
            onClick = {
                scope.launch {
                    val next = (pagerState.currentPage + 1) % total
                    pagerState.animateScrollToPage(next)
                }
            },
            modifier = Modifier
                .size(56.dp)
                .semantics { contentDescription = "Contacto siguiente" },
        ) {
            Icon(
                imageVector        = Icons.Filled.ChevronRight,
                contentDescription = null,
                modifier           = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun ContactCarouselCard(contact: Contact) {
    Column(
        modifier             = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .semantics {
                contentDescription =
                    "${contact.name}, ${contact.relation}, teléfono ${contact.phone}"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(180.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(contact.avatarColor),
        ) {
            Text(
                text       = contact.name.split(" ")
                    .take(2)
                    .mapNotNull { it.firstOrNull()?.uppercase() }
                    .joinToString(""),
                color      = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize   = 56.sp,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text       = contact.name,
            fontWeight = FontWeight.Bold,
            fontSize   = 22.sp,
            color      = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text     = contact.phone,
            fontSize = 17.sp,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text       = contact.relation,
            fontSize   = 14.sp,
            color      = contact.avatarColor,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ── Contact card (list row) ──────────────────────────────────────────────────

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text       = contact.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 17.sp,
                        color      = MaterialTheme.colorScheme.onSurface,
                    )
                    if (contact.isFavorite) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector        = Icons.Filled.Star,
                            contentDescription = "Favorito",
                            tint               = MaterialTheme.colorScheme.primary,
                            modifier           = Modifier.size(16.dp),
                        )
                    }
                }
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
