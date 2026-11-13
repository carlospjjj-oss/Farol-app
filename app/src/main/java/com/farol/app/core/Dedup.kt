package com.farol.app.core

/** Evita gravar a mesma oferta várias vezes (cartão fechado e reaberto, releitura do OCR). */
object OfferDedup {
    const val WINDOW_MS = 3 * 60_000L

    /** Mesma oferta = mesmo app, mesmo preço (centavos) e km arredondado para 0,1. */
    fun key(app: String, price: Double, km: Double) = "$app|${Math.round(price * 100)}|${Math.round(km * 10)}"

    /** Recebe registros (hora, chave) do mais antigo para o mais novo e diz quais manter. */
    fun keepFlags(items: List<Pair<Long, String>>): List<Boolean> {
        val last = mutableMapOf<String, Long>()
        return items.map { (t, k) ->
            val prev = last[k]
            last[k] = t
            prev == null || t - prev > WINDOW_MS
        }
    }
}
