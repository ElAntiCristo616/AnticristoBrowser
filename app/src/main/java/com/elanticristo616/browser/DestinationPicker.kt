package com.elanticristo616.browser

import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * CuteNovna Browser v1.1.0 — HOJA DE DESTINOS.
 *
 * Lista las teles encontradas en el Wi-Fi (Roku, Chromecast o
 * DLNA) con su protocolo y su nombre o IP. Al tocar una, esa tele
 * queda vinculada. "Buscar teles" vuelve a descubrir.
 */
object DestinationPicker {

    fun show(
        activity: AppCompatActivity,
        targets: List<CastTarget>,
        onPick: (CastTarget) -> Unit,
        onSearch: () -> Unit
    ) {
        val dialog = BottomSheetDialog(activity)
        val view = activity.layoutInflater.inflate(R.layout.bottom_sheet_destinos, null)
        val container = view.findViewById<LinearLayout>(R.id.sheetDeviceList)
        val btnSearch = view.findViewById<Button>(R.id.btnBuscarTeles)
        val btnClose = view.findViewById<Button>(R.id.btnSheetCerrar)
        if (targets.isEmpty()) {
            val empty = TextView(activity).apply {
                text = "No encontré teles en este Wi-Fi. Revisa que la tele " +
                    "esté prendida y en la misma red que el teléfono, " +
                    "o escribe su IP en la puerta."
                setTextColor(AppStyle.MUTED)
                textSize = 14f
                setPadding(0, 8, 0, 8)
            }
            container.addView(empty)
        }
        for (target in targets) {
            val row = activity.layoutInflater.inflate(R.layout.item_destino, container, false)
            row.findViewById<TextView>(R.id.tvDestinoNombre).text = target.name
            row.findViewById<TextView>(R.id.tvDestinoDetalle).text =
                "${target.protocol} • ${target.detail.ifBlank { "misma red Wi-Fi" }}"
            val params = row.layoutParams as LinearLayout.LayoutParams
            params.bottomMargin = 8
            row.layoutParams = params
            row.setOnClickListener {
                dialog.dismiss()
                onPick(target)
            }
            container.addView(row)
        }
        btnSearch.setOnClickListener {
            dialog.dismiss()
            onSearch()
        }
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(view)
        dialog.show()
    }
}
