package com.farol.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppCatalogTest {

    /** Acha server/worker.js subindo as pastas (o Gradle roda os testes de dentro de app/). */
    private fun worker(): String {
        var d: File? = File("").absoluteFile
        while (d != null) {
            val f = File(d, "server/worker.js")
            if (f.exists()) return f.readText()
            d = d.parentFile
        }
        error("server/worker.js não encontrado")
    }

    @Test fun servidorConheceTodosOsPacotes() {
        val js = worker()
        for (app in AppCatalog.apps) for (pkg in app.packages)
            assertTrue("servidor não conhece $pkg (APP_NAMES)", js.contains("\"$pkg\": \"${app.name}\""))
    }

    @Test fun painelTemCorDeTodosOsApps() {
        val js = worker()
        for (app in AppCatalog.apps) {
            val hex = "#" + String.format("%06X", app.color and 0xFFFFFF)
            assertTrue("painel sem a cor de ${app.name} ($hex)", js.contains("\"${app.name}\":\"$hex\""))
        }
    }

    @Test fun listasPadraoDosModos() {
        assertEquals(listOf("Uber", "99", "inDrive", "iFood", "Keeta"),
            AppCatalog.defaultTargets(AppCatalog.Mode.MOTO).lines().map { it.substringAfter('=') })
        assertEquals(listOf("Uber", "99", "inDrive"),
            AppCatalog.defaultTargets(AppCatalog.Mode.CARRO).lines().map { it.substringAfter('=') })
        assertEquals("Keeta", AppCatalog.byPackage("com.sankuai.sailor.afooddelivery")?.name)
    }
}
