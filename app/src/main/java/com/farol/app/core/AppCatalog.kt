package com.farol.app.core

/**
 * Catálogo único dos apps de corrida/entrega que o Farol conhece.
 * Tudo sai daqui: lista padrão de cada modo, filtro da acessibilidade, cores e nomes.
 * O servidor (server/worker.js) tem a mesma lista; o teste AppCatalogTest confere se bate.
 */
object AppCatalog {
    enum class Mode { MOTO, CARRO }

    data class AppInfo(
        val name: String,
        val packages: List<String>,     // o primeiro é o principal; os outros são variações vistas nos logs
        val color: Long,                // ARGB
        val modes: Set<Mode>,
    )

    val apps: List<AppInfo> = listOf(
        AppInfo("Uber", listOf("com.ubercab.driver"), 0xFFE5E7EB, setOf(Mode.MOTO, Mode.CARRO)),
        AppInfo("99", listOf("com.app99.driver"), 0xFFFFD000, setOf(Mode.MOTO, Mode.CARRO)),
        AppInfo("inDrive", listOf("sinet.startup.inDriver"), 0xFFB6F36A, setOf(Mode.MOTO, Mode.CARRO)),
        AppInfo("iFood", listOf("br.com.ifood.driver.app"), 0xFFEA1D2C, setOf(Mode.MOTO)),
        AppInfo("Keeta", listOf("com.sankuai.sailor.courier", "com.sankuai.sailor.afooddelivery"), 0xFF2BD49B, setOf(Mode.MOTO)),
    )

    /** Pedaços do nome do pacote que identificam um app mesmo com pacote diferente (o Keeta varia por país). */
    val namePatterns: List<Pair<String, String>> = listOf("keeta" to "Keeta", "sankuai" to "Keeta")

    const val OTHER_COLOR = 0xFF8B5CF6

    fun byName(name: String): AppInfo? = apps.firstOrNull { it.name == name }
    fun byPackage(pkg: String): AppInfo? = apps.firstOrNull { pkg in it.packages }
    fun color(name: String): Long = byName(name)?.color ?: OTHER_COLOR

    /** Todos os pacotes conhecidos (para o filtro da acessibilidade). */
    val allPackages: List<String> get() = apps.flatMap { it.packages }

    /** Texto padrão "pacote=Nome" de cada modo (o usuário pode editar nos ajustes). */
    fun defaultTargets(mode: Mode): String =
        apps.filter { mode in it.modes }.joinToString("\n") { "${it.packages.first()}=${it.name}" }
}
