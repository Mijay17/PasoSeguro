package com.pasoseguro.app.voice

import com.pasoseguro.app.navigation.Feature

/**
 * Intención ya interpretada a partir de un comando de voz. Es lo único que
 * reciben las pantallas — nunca el texto crudo ni detalles de cómo se
 * reconoció (hoy [VoiceCommandParser] por palabras clave, mañana un modelo
 * de lenguaje). Cada comando trae consigo la frase de confirmación que el
 * Asistente IA debe anunciar antes de actuar.
 */
sealed interface VoiceCommand {

    /** Lo que el Asistente IA anuncia por voz antes de ejecutar la acción. */
    val confirmationSpeech: String

    data class OpenFeature(val feature: Feature) : VoiceCommand {
        override val confirmationSpeech: String = when (feature) {
            Feature.NAVIGATE -> "Abriendo modo Navegar."
            Feature.SCAN     -> "Abriendo modo Explorar."
            Feature.ROUTE    -> "Abriendo Ruta."
            Feature.CONTACTS -> "Abriendo Contactos."
            Feature.ALERTS   -> "Abriendo Notificaciones."
            Feature.CONFIG   -> "Abriendo Configuración."
        }
    }

    data object GoBack : VoiceCommand {
        override val confirmationSpeech = "Volviendo a la pantalla anterior."
    }

    data object GoHome : VoiceCommand {
        override val confirmationSpeech = "Regresando al inicio."
    }

    data object Cancel : VoiceCommand {
        override val confirmationSpeech = "Cancelado."
    }

    data object Accept : VoiceCommand {
        override val confirmationSpeech = "Confirmado."
    }

    /** El texto reconocido no coincide con ningún comando disponible. */
    data object Unknown : VoiceCommand {
        override val confirmationSpeech = "No pude comprender la instrucción. Inténtalo nuevamente."
    }
}
