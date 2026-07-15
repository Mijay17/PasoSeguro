package com.pasoseguro.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.pasoseguro.app.data.TtsSpeed
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Único motor de TextToSpeech de toda la aplicación — propiedad exclusiva de
 * [VoiceInteractionManager]. Reemplaza a `TtsHelper`
 * (utils/HapticHelper.kt) y a las tres clases `*TtsHelper` que antes vivían
 * duplicadas dentro de NavigateViewModel/ExploreViewModel/RouteViewModel.
 * Al haber un solo motor para toda la app (en vez de uno por pantalla), la
 * serialización entre habla guionada (alertas simuladas, análisis) y el
 * Asistente IA queda garantizada en un único lugar.
 */
class TextToSpeechManager(context: Context) {

    private var tts: TextToSpeech? = null
    @Volatile private var ready = false

    var enabled: Boolean = true
    var speechRate: Float = TtsSpeed.NORMAL.rate

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    /** Último texto hablado por la app — usado por el comando global "Repetir". */
    var lastSpokenUtterance: String? = null
        private set

    // Una promesa de finalización POR utteranceId (no un canal único) — necesario
    // porque ahora pueden convivir en la cola una locución fire-and-forget
    // (speak) y una esperada (speakAndAwait, con flush=false para no
    // cortar a la anterior): con un solo canal compartido, speakAndAwait()
    // podía "despertarse" con el onDone de OTRA locución que no era la suya.
    // onStart/onDone corren en un hilo del motor TTS, no en el principal —
    // por eso ConcurrentHashMap en vez de un mutableMapOf plano.
    private val pendingCompletions = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    // Serializa speakAndAwait: la pantalla activa (loop de simulación, p. ej.)
    // y las respuestas del Asistente IA pueden intentar hablar casi a la vez;
    // sin este mutex se pisarían entre sí.
    private val speechMutex = Mutex()

    private val nextUtteranceId = AtomicInteger(0)

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("es", "PE")
                ready = true
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                VoiceDebugLog.d("tts: START hablando id=$utteranceId texto=\"$lastSpokenUtterance\"")
                _isSpeaking.value = true
            }
            override fun onDone(utteranceId: String?) {
                VoiceDebugLog.d("tts: DONE id=$utteranceId")
                _isSpeaking.value = false
                completeUtterance(utteranceId)
            }
            @Suppress("DEPRECATION")
            override fun onError(utteranceId: String?) {
                VoiceDebugLog.d("tts: ERROR (legacy) id=$utteranceId")
                _isSpeaking.value = false
                completeUtterance(utteranceId)
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                VoiceDebugLog.d("tts: ERROR id=$utteranceId code=$errorCode")
                _isSpeaking.value = false
                completeUtterance(utteranceId)
            }
        })
    }

    private fun completeUtterance(utteranceId: String?) {
        utteranceId?.let { pendingCompletions.remove(it)?.complete(Unit) }
    }

    /**
     * Habla [text] sin esperar — para prompts/diálogos disparados desde la UI o la navegación por voz.
     *
     * [flush]=true (por defecto) corta cualquier locución en curso — el
     * comportamiento de siempre, necesario para el barge-in. [flush]=false
     * encola [text] detrás de lo que esté sonando en vez de cortarlo — para
     * mensajes que deben sonar completos en secuencia (p. ej. la bienvenida
     * de una pantalla justo después de la confirmación de navegación que la
     * abrió), sin arriesgar que se corten entre sí.
     */
    fun speak(text: String, flush: Boolean = true) {
        if (!ready || !enabled) return
        VoiceDebugLog.d("tts: speak() solicitado texto=\"$text\" flush=$flush")
        lastSpokenUtterance = text
        tts?.setSpeechRate(speechRate)
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(text, mode, null, "voice-${nextUtteranceId.getAndIncrement()}")
    }

    /**
     * Habla [text] y suspende hasta que ESA locución (identificada por su
     * propio utteranceId) termine — o hasta que [stop] la interrumpa. Cae a
     * un delay fijo de [fallbackMs] si el TTS está deshabilitado o aún no
     * está listo. Timeout de seguridad de 30 s por si el motor nunca dispara
     * onDone/onError. Ver [speak] para el significado de [flush].
     */
    suspend fun speakAndAwait(text: String, fallbackMs: Long = 3500L, flush: Boolean = true) = speechMutex.withLock {
        if (!ready || !enabled) {
            delay(fallbackMs)
            return@withLock
        }
        VoiceDebugLog.d("tts: speakAndAwait() solicitado texto=\"$text\" flush=$flush")
        lastSpokenUtterance = text
        tts?.setSpeechRate(speechRate)
        val utteranceId = "voice-await-${nextUtteranceId.getAndIncrement()}"
        val completion = CompletableDeferred<Unit>()
        pendingCompletions[utteranceId] = completion
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(text, mode, null, utteranceId)
        withTimeoutOrNull(30_000L) { completion.await() }
        pendingCompletions.remove(utteranceId)
        Unit
    }

    /**
     * Interrumpe la locución en curso de inmediato — la pieza clave del
     * barge-in. `TextToSpeech.stop()` descarta toda la cola (no solo la
     * locución actual) pero no garantiza disparar onDone/onError para cada
     * una en todos los motores/dispositivos, así que se completan aquí
     * manualmente todas las promesas pendientes — sin esto, una
     * `speakAndAwait` en curso (p. ej. a mitad de una alerta simulada) se
     * quedaría colgada hasta su timeout de 30 s en vez de liberarse al instante.
     */
    fun stop() {
        VoiceDebugLog.d("tts: STOP (interrupción) — isSpeaking=${_isSpeaking.value} lastUtterance=\"$lastSpokenUtterance\"")
        tts?.stop()
        _isSpeaking.value = false
        pendingCompletions.keys.toList().forEach { completeUtterance(it) }
    }

    fun setSpeed(speed: TtsSpeed) {
        speechRate = speed.rate
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
