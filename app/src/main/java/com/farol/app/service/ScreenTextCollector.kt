package com.farol.app.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Percorre a árvore de acessibilidade das janelas dos apps monitorados e junta
 * todo texto visível (text + contentDescription) em ordem de leitura.
 *
 * Olha todas as janelas, não só a ativa, porque a Uber/99 desenham a oferta numa
 * janela sobreposta ao mapa.
 */
object ScreenTextCollector {

    data class Snapshot(val pkg: String, val text: String)

    private const val MAX_NODES = 1500
    private const val MAX_DEPTH = 60

    fun collect(svc: AccessibilityService, isTarget: (String) -> Boolean): Snapshot? {
        val parts = mutableListOf<String>()
        var foundPkg: String? = null

        val windows = runCatching { svc.windows }.getOrNull().orEmpty()
        val dm = svc.resources.displayMetrics
        val screenArea = dm.widthPixels.toLong() * dm.heightPixels
        // Janela de app que está na frente (maior camada). Um app de corrida só conta se
        // ele estiver na frente, ou se for uma sobreposição grande (cartão de oferta por cima
        // de outro app). Isso ignora a "bolinha" flutuante da 99 enquanto você usa o WhatsApp.
        val topApp = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.maxByOrNull { it.layer }

        val roots = buildList {
            for (w in windows.sortedByDescending { it.layer }) {
                val r = Rect().also { w.getBoundsInScreen(it) }
                val big = r.width().toLong() * r.height() > screenArea / 4
                val visible = w.isActive || w.isFocused || w.id == topApp?.id ||
                    (w.type != AccessibilityWindowInfo.TYPE_APPLICATION && big)
                if (visible) w.root?.let { add(it) }
            }
            if (windows.isEmpty()) svc.rootInActiveWindow?.let { add(it) }
        }

        // Só o app de entrega que está mais na frente: evita misturar o texto de dois apps
        // (ex.: oferta do Keeta por cima da tela do iFood).
        for (root in roots) {
            val pkg = root.packageName?.toString() ?: continue
            if (!isTarget(pkg)) continue
            if (foundPkg == null) foundPkg = pkg
            if (pkg != foundPkg) continue
            val counter = intArrayOf(0)
            walk(root, 0, parts, counter)
        }
        val pkg = foundPkg ?: return null
        return Snapshot(pkg, parts.joinToString(" | "))
    }

    private fun walk(node: AccessibilityNodeInfo?, depth: Int, out: MutableList<String>, count: IntArray) {
        if (node == null || depth > MAX_DEPTH || count[0] > MAX_NODES) return
        count[0]++
        if (node.isVisibleToUser) {
            node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { add(out, it) }
            node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { add(out, it) }
        }
        for (i in 0 until node.childCount) {
            walk(runCatching { node.getChild(i) }.getOrNull(), depth + 1, out, count)
        }
    }

    private fun add(out: MutableList<String>, s: String) {
        val clean = s.replace('\n', ' ')
        if (out.lastOrNull() != clean) out += clean
    }
}
