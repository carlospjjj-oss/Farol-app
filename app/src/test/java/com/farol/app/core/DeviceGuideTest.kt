package com.farol.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceGuideTest {
    @Test fun detectaMarcas() {
        assertEquals(DeviceGuide.Brand.SAMSUNG, DeviceGuide.detect("samsung", "samsung"))
        assertEquals(DeviceGuide.Brand.MOTOROLA, DeviceGuide.detect("motorola", "motorola"))
        assertEquals(DeviceGuide.Brand.XIAOMI, DeviceGuide.detect("Xiaomi", "Redmi"))
        assertEquals(DeviceGuide.Brand.XIAOMI, DeviceGuide.detect("Xiaomi", "POCO"))
        assertEquals(DeviceGuide.Brand.OTHER, DeviceGuide.detect("Google", "google"))
    }

    @Test fun caminhoDaAcessibilidadePorMarca() {
        fun acc(b: DeviceGuide.Brand) = DeviceGuide.steps(b, 34).first { it.step == DeviceGuide.Step.ACCESSIBILITY }.path
        assertTrue(acc(DeviceGuide.Brand.SAMSUNG).contains("Apps instalados"))
        assertTrue(acc(DeviceGuide.Brand.XIAOMI).contains("Configurações adicionais"))
        assertTrue(acc(DeviceGuide.Brand.MOTOROLA).contains("Apps baixados"))
    }

    @Test fun passosExtrasSoOndePrecisa() {
        val x = DeviceGuide.steps(DeviceGuide.Brand.XIAOMI, 34).map { it.step }
        assertTrue(DeviceGuide.Step.AUTOSTART in x && DeviceGuide.Step.RECENTS in x)
        val s = DeviceGuide.steps(DeviceGuide.Brand.SAMSUNG, 34)
        assertTrue(s.none { it.step == DeviceGuide.Step.AUTOSTART })
        assertTrue(s.first { it.step == DeviceGuide.Step.INSTALL }.path.contains("Bloqueador automático"))
        // Android 12: sem configurações restritas nem pedido de notificação
        val old = DeviceGuide.steps(DeviceGuide.Brand.MOTOROLA, 31).map { it.step }
        assertTrue(DeviceGuide.Step.RESTRICTED !in old && DeviceGuide.Step.NOTIFICATIONS !in old)
    }
}
