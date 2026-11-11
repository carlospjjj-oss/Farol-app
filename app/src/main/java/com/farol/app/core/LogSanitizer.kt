package com.farol.app.core

/**
 * Limpa o texto antes de ele sair do celular nos logs remotos.
 * - Troca rua/número por "[endereço]", mantendo o bairro/cidade (útil para análise).
 * - Remove sequências longas de dígitos (telefone, CPF, códigos de pedido).
 * Não é perfeito (nome de passageiro, por exemplo, não é detectado): mantenha o repositório privado.
 */
object LogSanitizer {

    private val STREET = Regex(
        """^(r\.?|rua|av\.?|avenida|al\.?|alameda|tv\.?|trav\.?|travessa|estr\.?|estrada|rod\.?|rodovia|pra[cç]a|p[cç]a\.?|largo|lgo\.?|via|viela|beco|servid[aã]o)\s""",
        RegexOption.IGNORE_CASE
    )
    private val LEADING_NUMBER = Regex("""^\s*(n[º°o.]?\s*)?\d+[a-zA-Z]?\s*(-\s*)?""")
    private val LONG_DIGITS = Regex("""\(?\d[\d .\-/()]{7,}\d""")
    private val CEP = Regex("""\b\d{5}-?\d{3}\b""")

    fun mask(text: String): String = text.split(" | ").joinToString(" | ") { maskSegment(it) }

    private fun maskSegment(seg0: String): String {
        var seg = seg0.replace(CEP, "[cep]").replace(LONG_DIGITS, "[num]")
        val trimmed = seg.trim()
        if (STREET.containsMatchIn(trimmed)) {
            val rest = when {
                trimmed.contains(',') -> trimmed.substringAfter(',')
                trimmed.contains(" - ") -> trimmed.substringAfter(" - ")
                else -> ""
            }.replace(LEADING_NUMBER, "").trim().trimStart(',', '-').trim()
            seg = if (rest.isEmpty()) "[endereço]" else "[endereço], $rest"
        }
        return seg
    }
}
