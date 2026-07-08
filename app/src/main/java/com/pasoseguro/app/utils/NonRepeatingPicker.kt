package com.pasoseguro.app.utils

/**
 * Elige un mensaje al azar de un conjunto, evitando repetir el mismo dos
 * veces consecutivas. Pensado para las variaciones del Asistente IA (Ruta,
 * y más adelante las instrucciones dinámicas de navegación asistida).
 */
class NonRepeatingPicker(private val messages: List<String>) {
    private var lastIndex = -1

    fun next(): String {
        if (messages.size <= 1) return messages.first()
        var index: Int
        do {
            index = messages.indices.random()
        } while (index == lastIndex)
        lastIndex = index
        return messages[index]
    }
}
