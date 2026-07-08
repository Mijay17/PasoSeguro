package com.pasoseguro.app.voice

import com.pasoseguro.app.navigation.Feature
import java.text.Normalizer

/**
 * Interpreta el texto reconocido por voz y lo convierte en un [VoiceCommand].
 * Toda la lógica de interpretación vive aquí — punto único de extensión para
 * reemplazarla más adelante por un modelo de lenguaje (Gemini, GPT, un modelo
 * local) sin tocar [VoiceCommandManager] ni las pantallas, que solo conocen
 * el resultado ([VoiceCommand]).
 *
 * Tolerante a mayúsculas/minúsculas, espacios extra y acentos, y reconoce
 * varias formas equivalentes de pedir lo mismo ("abrir navegar", "abrir
 * navegación", "ir a navegar" → la misma intención).
 */
object VoiceCommandParser {

    fun parse(rawText: String): VoiceCommand {
        val text = normalize(rawText)
        if (text.isBlank()) return VoiceCommand.Unknown

        OPEN_FEATURE_KEYWORDS.forEach { (feature, keywords) ->
            if (keywords.any { text.contains(it) }) return VoiceCommand.OpenFeature(feature)
        }
        NAV_COMMAND_KEYWORDS.forEach { (command, keywords) ->
            if (keywords.any { text.contains(it) }) return command
        }

        // TODO(Fase futura): antes de devolver Unknown, integrar aquí un
        // modelo de lenguaje (Gemini/GPT/NLP local) para interpretar comandos
        // libres — destinos ("llévame a la universidad"), preguntas ("¿dónde
        // estoy?"), o repetir la última indicación. La interpretación seguirá
        // centralizada en este parser.
        return VoiceCommand.Unknown
    }

    private fun normalize(text: String): String =
        stripAccents(text.trim().lowercase()).replace(Regex("\\s+"), " ")

    private fun stripAccents(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}"), "")

    private val OPEN_FEATURE_KEYWORDS: List<Pair<Feature, List<String>>> = listOf(
        Feature.NAVIGATE to listOf("navegar", "navegacion"),
        Feature.SCAN     to listOf("explorar", "exploracion", "escanear"),
        Feature.ROUTE    to listOf("ruta"),
        Feature.CONTACTS to listOf("contacto"),
        Feature.ALERTS   to listOf("notificacion", "alerta"),
        Feature.CONFIG   to listOf("configuracion", "ajuste"),
    )

    private val NAV_COMMAND_KEYWORDS: List<Pair<VoiceCommand, List<String>>> = listOf(
        VoiceCommand.GoHome to listOf("ir al inicio", "ir a inicio", "al inicio", "inicio"),
        VoiceCommand.GoBack to listOf("volver", "regresar", "atras"),
        VoiceCommand.Cancel to listOf("cancelar"),
        VoiceCommand.Accept to listOf("aceptar", "confirmar"),
    )
}
