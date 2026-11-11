package com.farol.app.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.farol.app.data.Settings

/**
 * Botão "Farol" nas Configurações rápidas (puxe a barra de notificações para baixo).
 * Liga/desliga a leitura sem abrir o app — útil quando você está com o carro parado
 * ou não quer o cartão por um tempo.
 */
class FarolTileService : TileService() {

    override fun onStartListening() = render()

    override fun onClick() {
        val s = Settings(this)
        s.enabled = !s.enabled
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        val on = Settings(this).enabled
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Farol"
        if (android.os.Build.VERSION.SDK_INT >= 29) tile.subtitle = if (on) "Lendo ofertas" else "Pausado"
        tile.updateTile()
    }
}
