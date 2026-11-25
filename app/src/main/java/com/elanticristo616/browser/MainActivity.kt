package com.elanticristo616.browser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.elanticristo616.browser.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * Anticristo Browser v1.0.0: navegador simple con buscador DuckDuckGo,
 * lista de las 1,731 páginas de yt-dlp y transmisión de videos a la
 * Roku por el canal receptor «Web Video Caster - Receiver».
 * Sin cuentas, sin sincronización, sin configuraciones: solo el
 * buscador crudo y entrar a cualquier página. Sin ajustes salvo
 * «Acerca de».
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var client: RokuClient? = null
    private var connectedIp: String? = null
    private var connectedName: String? = null
    private lateinit var sitesAdapter: SitesAdapter

    private val prefs by lazy { getSharedPreferences("roku", Context.MODE_PRIVATE) }

    private val quickSites = listOf(
        "YouTube" to "youtube.com",
        "TikTok" to "tiktok.com",
        "Instagram" to "instagram.com",
        "X" to "x.com",
        "Facebook" to "facebook.com"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Accesos rápidos
        for ((name, domain) in quickSites) {
            binding.quickRow.addView(quickItem(name, domain))
        }

        // Buscador
        binding.btnBuscarWeb.setOnClickListener { buscar(binding.etBuscar.text.toString()) }
        binding.etBuscar.setOnEditorActionListener { _, _, _ ->
            buscar(binding.etBuscar.text.toString()); true
        }

        // Conectar a la tele
        binding.btnBuscarTeles.setOnClickListener { buscarTeles() }
        binding.btnConectarIp.setOnClickListener { conectarPorIp() }

        // Transmitir enlace (calidad más alta por defecto)
        binding.btnCastLink.setOnClickListener { transmitirEnlace() }

        // Sitios (1,731 páginas de yt-dlp)
        sitesAdapter = SitesAdapter(lifecycleScope) { site ->
            if (site.domain.isBlank()) {
                Toast.makeText(
                    this,
                    "Ese es el descargador genérico: pega un enlace en el navegador.",
                    Toast.LENGTH_LONG
                ).show()
                return@SitesAdapter
            }
            abrirNavegador(site.siteUrl)
        }
        binding.rvSites.layoutManager = GridLayoutManager(this, 3)
        binding.rvSites.adapter = sitesAdapter
        val sites = SitesRepository.load(this)
        sitesAdapter.setSites(sites)
        binding.tvSitesTitle.text = "${"%,d".format(sites.size)} páginas para transmitir"
        binding.etBuscarSitio.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                sitesAdapter.filter(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnAcercaDe.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Acerca de")
                .setMessage(getString(R.string.about))
                .setPositiveButton("Cerrar", null)
                .show()
        }

        // Reconectar con la última tele guardada (IP manual o descubierta)
        val lastIp = prefs.getString("last_ip", null)
        val lastName = prefs.getString("last_name", null)
        if (lastIp != null) {
            conectar(lastIp, lastName ?: "Roku TV", silencioso = true)
        } else {
            binding.tvStatusTele.text = "Sin tele conectada. Busca teles o escribe su IP."
        }
    }

    override fun onResume() {
        super.onResume()
        CastSession.onReceiverEvent = { event, body ->
            runOnUiThread {
                CastHelper.describeReceiverEvent(event, body)?.let {
                    binding.tvCastStatus.text = it
                }
            }
        }
    }

    override fun onPause() {
        if (CastSession.onReceiverEvent != null) CastSession.onReceiverEvent = null
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        CastSession.reset()
    }

    // ---------- Navegador ----------

    private fun buscar(text: String) {
        val t = text.trim()
        if (t.isBlank()) return
        val url = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            t.contains(".") && !t.contains(" ") -> "https://$t"
            else -> "https://duckduckgo.com/?q=" +
                java.net.URLEncoder.encode(t, "UTF-8")
        }
        abrirNavegador(url)
    }

    private fun abrirNavegador(url: String) {
        startActivity(
            Intent(this, BrowserActivity::class.java).putExtra("url", url)
        )
    }

    private fun quickItem(name: String, domain: String): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val icon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(112, 112)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        SitesRepository.loadFavicon(lifecycleScope, domain, icon)
        val label = TextView(this).apply {
            text = name
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            gravity = Gravity.CENTER
        }
        col.addView(icon)
        col.addView(label)
        col.setOnClickListener { abrirNavegador("https://$domain") }
        return col
    }

    // ---------- Conexión con la tele ----------

    private fun buscarTeles() {
        binding.tvStatusTele.text = "Buscando teles en tu Wi-Fi…"
        binding.devicesContainer.removeAllViews()
        lifecycleScope.launch {
            val devices = try {
                RokuClient.discover(this@MainActivity)
            } catch (e: Exception) {
                emptyList()
            }
            binding.devicesContainer.removeAllViews()
            if (devices.isEmpty()) {
                binding.tvStatusTele.text = "No encontré ninguna Roku en este Wi-Fi.\n" +
                    "Revisa que la tele esté prendida y en el mismo Wi-Fi que tu celular. " +
                    "Si sigue sin aparecer, escribe su IP a mano abajo."
                return@launch
            }
            binding.tvStatusTele.text = "Toca tu tele para conectar:"
            for (d in devices) {
                val b = Button(this@MainActivity).apply {
                    text = if (d.model.isNotBlank()) "${d.name} (${d.model})" else d.name
                    setOnClickListener { conectar(d.ip, d.name) }
                }
                binding.devicesContainer.addView(b)
            }
        }
    }

    /** Conecta escribiendo la IP a mano (Ajustes → Red → Acerca de en la tele).
     *  La IP se guarda y se usa primero la próxima vez que se abra la app. */
    private fun conectarPorIp() {
        val ip = binding.etIpManual.text.toString().trim()
        if (!Regex("""^\d{1,3}(\.\d{1,3}){3}$""").matches(ip)) {
            Toast.makeText(this, "Esa IP no se ve bien. Ejemplo: 192.168.1.5", Toast.LENGTH_LONG).show()
            return
        }
        binding.tvStatusTele.text = "Conectando con $ip…"
        conectar(ip, "Roku TV")
    }

    private fun conectar(ip: String, name: String, silencioso: Boolean = false) {
        lifecycleScope.launch {
            val c = RokuClient(ip)
            val ok = try { c.ping() } catch (e: Exception) { false }
            if (ok) {
                val info = try { c.queryDeviceInfo() } catch (e: Exception) { null }
                val realName = info?.friendlyName?.ifBlank { null } ?: name
                client = c
                connectedIp = ip
                connectedName = realName
                CastSession.rokuIp = ip
                CastSession.rokuName = realName
                prefs.edit().putString("last_ip", ip).putString("last_name", realName).apply()
                binding.tvStatusTele.text = "Conectada: $realName ($ip)"
            } else {
                if (!silencioso) {
                    binding.tvStatusTele.text = "No pude conectar con $name ($ip). " +
                        "Revisa que esté prendida, en el mismo Wi-Fi, y que en la tele esté " +
                        "activado Control por apps móviles."
                } else {
                    binding.tvStatusTele.text = "Sin tele conectada. Busca teles o escribe su IP."
                }
            }
        }
    }

    private fun requiereConexion(): RokuClient? {
        val c = client
        if (c == null) {
            Toast.makeText(
                this,
                "Primero conecta tu tele: busca teles o escribe su IP.",
                Toast.LENGTH_LONG
            ).show()
        }
        return c
    }

    // ---------- Transmitir enlace ----------

    private fun transmitirEnlace() {
        val c = requiereConexion() ?: return
        val link = binding.etCastLink.text.toString().trim()
        if (link.isBlank()) {
            Toast.makeText(this, "Pega un enlace primero.", Toast.LENGTH_SHORT).show()
            return
        }
        binding.tvCastStatus.text = "Extrayendo video…"
        lifecycleScope.launch {
            val res = CastHelper.castLink(
                this@MainActivity, c, link,
                height = null, // sin elegir: la más alta disponible
                headers = mapOf(
                    "User-Agent" to DEFAULT_UA,
                    "Referer" to link
                )
            ) { msg -> binding.tvCastStatus.text = msg }
            binding.tvCastStatus.text = res.message
            if (!res.ok) {
                Toast.makeText(this@MainActivity, res.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"
    }
}
