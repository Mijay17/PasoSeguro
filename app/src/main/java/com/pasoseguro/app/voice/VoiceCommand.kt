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

    /**
     * OJO: ninguna de estas frases debe contener la palabra gatillo de su
     * propia [Feature] (`navegar`, `explorar`, `ruta`, `contacto`,
     * `notificacion`/`alerta`, `configuracion`) — el micrófono de la
     * pantalla destino empieza a escuchar casi al mismo tiempo que esta
     * frase suena, y si la contiene, la app puede "escucharse a sí misma"
     * y volver a disparar el mismo comando (causa raíz del ciclo de
     * auto-reconocimiento detectado en pruebas de dispositivo real).
     */
    data class OpenFeature(val feature: Feature) : VoiceCommand {
        override val confirmationSpeech: String = when (feature) {
            Feature.NAVIGATE -> "Modo activado. Aquí puedes iniciar el monitoreo de tu entorno."
            Feature.SCAN     -> "Modo activado. Analicemos el espacio que te rodea."
            Feature.ROUTE    -> "Aquí puedes indicar tu destino."
            Feature.CONTACTS -> "Aquí tienes a las personas de confianza para emergencias."
            Feature.ALERTS   -> "Aquí está el historial de actividad reciente."
            Feature.CONFIG   -> "Aquí puedes personalizar la aplicación."
        }
    }

    data object GoBack : VoiceCommand {
        override val confirmationSpeech = "Volviendo a la pantalla anterior."
    }

    /** Mismo cuidado que en [OpenFeature]: no debe contener "inicio". */
    data object GoHome : VoiceCommand {
        override val confirmationSpeech = "Volviendo a la pantalla principal."
    }

    data object Cancel : VoiceCommand {
        override val confirmationSpeech = "Cancelado."
    }

    data object Accept : VoiceCommand {
        override val confirmationSpeech = "Confirmado."
    }

    /**
     * Repite el último mensaje hablado. [confirmationSpeech] es solo un
     * valor por defecto — [NavigationVoiceController] lo reemplaza por
     * `TextToSpeechManager.lastSpokenUtterance` en tiempo de ejecución.
     * No debe contener "repetir" por el mismo motivo que [OpenFeature].
     */
    data object Repeat : VoiceCommand {
        override val confirmationSpeech = "Todavía no dije nada."
    }

    /**
     * Pide ayuda. [confirmationSpeech] cubre los comandos globales;
     * [NavigationVoiceController] le añade el `helpHint` de la pantalla
     * activa (si existe) antes de hablarlo.
     *
     * RIESGO CONOCIDO Y PENDIENTE (no resuelto en esta pasada): esta frase
     * necesariamente enumera las 5 palabras gatillo globales, así que no se
     * puede "resolver por redacción" como las demás — si el micrófono la
     * escucha mientras suena, cualquiera de esas 5 palabras podría
     * autodispararse. Requiere la mitigación en tiempo de ejecución
     * (gating/echo-detection) que se evaluará en la Fase 2 con evidencia de
     * los logs, no un cambio de texto.
     */
    data object Help : VoiceCommand {
        override val confirmationSpeech =
            "Puedes decir Atrás, Inicio, Cancelar, Repetir, o Ayuda, en cualquier momento."
    }

    /** El texto reconocido no coincide con ningún comando disponible. */
    data object Unknown : VoiceCommand {
        override val confirmationSpeech = "No pude comprender la instrucción. Inténtalo nuevamente."
    }
}
