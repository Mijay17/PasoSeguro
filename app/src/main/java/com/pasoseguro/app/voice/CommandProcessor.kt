package com.pasoseguro.app.voice

import com.pasoseguro.app.navigation.Feature
import java.text.Normalizer

/**
 * Resultado de interpretar un texto reconocido.
 *
 * [Unresolved] es, a propósito, el único punto de la arquitectura que
 * todavía no sabe qué hacer con una frase — hoy [VoiceInteractionManager]
 * solo la anuncia como no comprendida; una fase futura podría interceptar
 * este caso y enviarlo a un motor conversacional (Gemini Live) sin tocar
 * nada más del sistema de voz.
 *
 * [NoInput] es distinto de [Unresolved]: significa que, tras descartar el
 * eco del propio TTS (ver [CommandProcessor.stripEcho]), no quedó ningún
 * texto nuevo — el usuario no dijo nada, el micrófono solo captó a la app
 * hablando. No corresponde anunciar "no entendí" para algo que nadie dijo.
 */
sealed interface Resolution {
    data class Global(val command: VoiceCommand) : Resolution
    data class Screen(val entry: ScreenVoiceCommand) : Resolution
    data class Unresolved(val rawText: String) : Resolution
    data object NoInput : Resolution
}

/**
 * Interpreta el texto reconocido por voz. Separando explícitamente dos
 * niveles que nunca dependen de un servicio externo:
 *
 * 1. Comandos propios de la pantalla activa (si se provee [ScreenVoiceContext])
 *    — se comprueban primero, por ser más específicos. Esto importa porque
 *    varias frases de pantalla contienen, como subcadena de palabras, una
 *    palabra global con un significado distinto (p. ej. "cancelar ruta"
 *    contiene "cancelar"; "leer notificaciones" contiene "notificacion"):
 *    deben resolver a la intención de la pantalla, no a la global.
 * 2. Comandos globales (Atrás/Inicio/Cancelar/Repetir/Ayuda + abrir función) —
 *    se comprueban después, como resguardo disponible en cualquier pantalla.
 *
 * Si no matchea nada, el resultado es [Resolution.Unresolved] — el punto de
 * enchufe para una futura interpretación conversacional.
 *
 * ## Matching por tokens (no por substring crudo)
 *
 * Verificado en dispositivo real (ver logcat de sesión de depuración): el
 * `SpeechRecognizer` sigue grabando mientras el TTS suena, así que un
 * resultado reconocido con eco propio tiene casi siempre la forma
 * `"<eco de lo que dijo la app> <comando real del usuario>"` — el comando
 * real queda al final, nunca al principio. Por eso:
 *
 * - En modo `strict=true` (TTS hablando, barge-in) se exige que las
 *   ÚLTIMAS palabras del texto reconocido sean, en orden, las de la palabra
 *   clave — no que el texto completo sea igual a la palabra clave. Esto es
 *   el modelo correcto del fenómeno observado, no una heurística arbitraria.
 * - En modo `strict=false` (uso normal) la palabra clave puede aparecer
 *   como ventana de tokens contigua en cualquier posición del texto.
 *
 * Cada palabra (token) se compara con `actual == keyword || actual.startsWith(keyword)`
 * — tolera plurales/conjugaciones simples del español ("contacto" ⊂ "contactos",
 * "notificacion" ⊂ "notificaciones") sin necesitar un lematizador, evitando a
 * la vez los falsos positivos del substring crudo de antes (que podía matchear
 * dentro de una palabra completamente distinta). Un comando de varias palabras
 * ("cancelar ruta", "agregar contacto") es solo una ventana más ancha del mismo
 * mecanismo — no hay lógica separada para comandos largos.
 *
 * ## Recorte de eco (Fase 2)
 *
 * El matching por tokens de arriba tolera que el comando real quede pegado
 * detrás del eco, pero no elimina el eco del texto — dos residuos quedaban:
 * (a) el eco puro (sin comando real) igual disparaba un "No pude comprender"
 * hablado por la app en respuesta a su propia voz; (b) cualquier comando
 * futuro más largo seguiría chocando con el mismo problema de raíz.
 *
 * [stripEcho] compara el texto reconocido, tokenizado, contra
 * [ScreenVoiceContext]-independiente `lastSpoken` (la última locución de la
 * app, vía `TextToSpeechManager.lastSpokenUtterance`) y descarta el prefijo
 * más largo de tokens que coincide, en orden, con alguna subsecuencia
 * contigua de lo que la app dijo — sin exigir que el eco empiece desde la
 * primera palabra (el reconocedor suele engancharse a mitad de frase). Lo
 * que sobra es el candidato real a comando del usuario, y sobre eso corre
 * el mismo matching de siempre, sin cambios. Si no sobra nada, el resultado
 * es [Resolution.NoInput] en vez de [Resolution.Unresolved].
 */
object CommandProcessor {

    fun interpret(rawText: String, screen: ScreenVoiceContext?, strict: Boolean, lastSpoken: String?): Resolution {
        val allTokens = tokenize(rawText)
        if (allTokens.isEmpty()) return Resolution.Unresolved(rawText)

        val tokens = stripEcho(allTokens, lastSpoken)
        if (tokens.isEmpty()) return Resolution.NoInput

        screen?.commands?.forEach { entry ->
            if (matchesAny(tokens, entry.keywords, strict)) return Resolution.Screen(entry)
        }

        matchGlobal(tokens, strict)?.let { return Resolution.Global(it) }

        return Resolution.Unresolved(rawText)
    }

    /**
     * Descarta el prefijo de [textTokens] que coincide con una subsecuencia
     * contigua de los tokens de [lastSpoken] — ver KDoc de la sección
     * "Recorte de eco" más arriba para el razonamiento completo.
     */
    private fun stripEcho(textTokens: List<String>, lastSpoken: String?): List<String> {
        if (lastSpoken.isNullOrBlank()) return textTokens
        val echoTokens = tokenize(lastSpoken)
        if (echoTokens.isEmpty()) return textTokens

        var longestEchoPrefix = 0
        for (start in echoTokens.indices) {
            var matched = 0
            while (matched < textTokens.size &&
                start + matched < echoTokens.size &&
                tokensSimilar(textTokens[matched], echoTokens[start + matched])
            ) {
                matched++
            }
            if (matched > longestEchoPrefix) longestEchoPrefix = matched
        }
        return textTokens.drop(longestEchoPrefix)
    }

    /** Comparación tolerante en ambos sentidos — aquí ambos lados son transcripciones/locuciones variables, no una keyword fija. */
    private fun tokensSimilar(a: String, b: String): Boolean =
        a == b || a.startsWith(b) || b.startsWith(a)

    private fun matchGlobal(tokens: List<String>, strict: Boolean): VoiceCommand? {
        OPEN_FEATURE_KEYWORDS.forEach { (feature, keywords) ->
            if (matchesAny(tokens, keywords, strict)) return VoiceCommand.OpenFeature(feature)
        }
        GLOBAL_COMMAND_KEYWORDS.forEach { (command, keywords) ->
            if (matchesAny(tokens, keywords, strict)) return command
        }
        return null
    }

    private fun matchesAny(textTokens: List<String>, keywords: List<String>, strict: Boolean): Boolean =
        keywords.any { keyword ->
            val keywordTokens = tokenize(keyword)
            keywordTokens.isNotEmpty() &&
                if (strict) endsWithTokens(textTokens, keywordTokens) else containsTokens(textTokens, keywordTokens)
        }

    /** [strict]: la ventana de tokens de la keyword debe ser, exactamente, el final del texto reconocido. */
    private fun endsWithTokens(text: List<String>, keyword: List<String>): Boolean {
        if (keyword.size > text.size) return false
        return windowMatches(text.subList(text.size - keyword.size, text.size), keyword)
    }

    /** No-strict: la ventana de tokens de la keyword puede estar en cualquier posición contigua del texto. */
    private fun containsTokens(text: List<String>, keyword: List<String>): Boolean {
        if (keyword.size > text.size) return false
        for (start in 0..(text.size - keyword.size)) {
            if (windowMatches(text.subList(start, start + keyword.size), keyword)) return true
        }
        return false
    }

    private fun windowMatches(textWindow: List<String>, keywordTokens: List<String>): Boolean =
        textWindow.size == keywordTokens.size &&
            textWindow.zip(keywordTokens).all { (actual, kw) -> actual == kw || actual.startsWith(kw) }

    private fun tokenize(text: String): List<String> =
        normalize(text).split(" ").filter { it.isNotBlank() }

    private fun normalize(text: String): String =
        stripAccents(text.trim().lowercase())
            .replace(Regex("[^a-z0-9\\s]"), " ") // quita puntuación (p. ej. "atrás." -> "atras")
            .replace(Regex("\\s+"), " ")
            .trim()

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

    private val GLOBAL_COMMAND_KEYWORDS: List<Pair<VoiceCommand, List<String>>> = listOf(
        VoiceCommand.GoHome to listOf("ir al inicio", "ir a inicio", "al inicio", "inicio"),
        VoiceCommand.GoBack to listOf("volver", "regresar", "atras"),
        VoiceCommand.Cancel to listOf("cancelar"),
        VoiceCommand.Accept to listOf("aceptar", "confirmar"),
        VoiceCommand.Repeat to listOf("repetir", "repite"),
        VoiceCommand.Help   to listOf("ayuda", "auxilio"),
    )
}
