package com.elanticristo616.browser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.elanticristo616.browser.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * CuteNovna Browser v1.1.0 — PANTALLA 1: LA PUERTA.
 *
 * La tele vinculada es el requisito para entrar al portal. Dos
 * caminos:
 *  1) Conectar por IP (Roku): se valida la IPv4 y se comprueba la
 *     tele con RokuClient.checkReachable (GET
 *     http://<ip>:8060/query/device-info, timeout 10 s, hasta 3
 *     intentos; CUALQUIER código HTTP cuenta como «tele
 *     alcanzable»). LA PUERTA YA NO BLOQUEA: si el chequeo falla
 *     tras los reintentos, la IP se guarda IGUAL y se entra al
 *     portal con un aviso («La tele no respondió en esa IP, revisa
 *     que esté prendida. Podrás transmitir cuando responda»).
 *  2) «Buscar teles»: descubre Roku, Chromecast y DLNA en el
 *     Wi-Fi y se elige una de la hoja; esa tele queda vinculada
 *     (TargetStore) y se entra directo.
 *
 * Al abrir la app, si hay una tele vinculada guardada se intenta
 * restaurarla (descubrimiento rápido y coincidencia por id); si
 * no se encuentra, se queda en la puerta. Si no hay tele guardada
 * pero sí una IP de Roku, se auto-conecta con la misma
 * comprobación de antes.
 *
 * Look fijo neón de la referencia (v1.1.0): sin temas ni
 * partículas.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val prefs by lazy { getSharedPreferences("roku", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnConectarIp.setOnClickListener { conectarPorIp() }
        binding.etIpManual.setOnEditorActionListener { _, _, _ ->
            conectarPorIp(); true
        }
        binding.btnBuscarTeles.setOnClickListener { buscarTeles() }

        val savedIp = prefs.getString("last_ip", null)
        if (savedIp != null) {
            binding.etIpManual.setText(savedIp)
        }

        // «Cambiar» desde el portal: se muestra la puerta con la IP
        // guardada ya escrita, SIN auto-conectar, para meter otra.
        val changingTv = intent.getBooleanExtra("change_tv", false)
        if (changingTv) {
            binding.tvStatusTele.text =
                "Escribe la IP de la tele nueva para entrar."
            return
        }

        // Tele vinculada guardada (cualquier protocolo): restaurarla.
        val savedTarget = TargetStore.load(this)
        if (savedTarget != null) {
            binding.tvStatusTele.text = "Conectando con ${savedTarget.name}…"
            restaurarTeleGuardada(savedTarget)
            return
        }

        if (savedIp != null) {
            binding.tvStatusTele.text = "Conectando con $savedIp…"
            conectar(savedIp)
        }
    }

    /** Busca la tele guardada entre las teles del Wi-Fi y entra
     *  directo si aparece; si no, se queda en la puerta. */
    private fun restaurarTeleGuardada(saved: TargetStore.SavedTarget) {
        lifecycleScope.launch {
            val targets = try {
                DeviceDiscovery.discoverAll(this@MainActivity)
            } catch (e: Exception) {
                emptyList()
            }
            val found = targets.firstOrNull { it.id == saved.id }
            if (found != null) {
                vincular(found, entrar = true)
                return@launch
            }
            // La Roku guardada por IP se reintenta por IP aunque el
            // SSDP no la haya visto.
            if (saved.protocol == "Roku" && saved.detail.isNotBlank()) {
                conectar(saved.detail)
                return@launch
            }
            binding.tvStatusTele.text =
                "No encontré «${saved.name}». Elige otra tele o escribe su IP."
            TargetStore.clear(this@MainActivity)
        }
    }

    /** Descubre Roku + Chromecast + DLNA y muestra la hoja. */
    private fun buscarTeles() {
        binding.tvBuscando.visibility = View.VISIBLE
        binding.btnBuscarTeles.isEnabled = false
        lifecycleScope.launch {
            val targets = try {
                DeviceDiscovery.discoverAll(this@MainActivity)
            } catch (e: Exception) {
                emptyList()
            }
            binding.tvBuscando.visibility = View.GONE
            binding.btnBuscarTeles.isEnabled = true
            DestinationPicker.show(
                this@MainActivity, targets,
                onPick = { target -> vincular(target, entrar = true) },
                onSearch = { buscarTeles() }
            )
        }
    }

    /** Vincula una tele: queda como activa, se guarda y se entra
     *  al portal. Para Roku también se guarda la IP. */
    private fun vincular(target: CastTarget, entrar: Boolean) {
        CastSession.activeTarget = target
        TargetStore.save(this, target)
        if (target is RokuCastTarget) {
            CastSession.rokuIp = target.ip
            CastSession.rokuName = target.name
            CastSession.tvWarning = null
            prefs.edit()
                .putString("last_ip", target.ip)
                .putString("last_name", target.name)
                .apply()
            binding.tvStatusTele.text = "Conectado con ${target.name} (${target.ip})."
        } else {
            binding.tvStatusTele.text =
                "Conectado con ${target.name} (${target.protocol})."
        }
        if (entrar) {
            startActivity(Intent(this, PortalActivity::class.java))
            finish()
        }
    }

    /** Valida la IP escrita y comprueba la tele. */
    private fun conectarPorIp() {
        val ip = binding.etIpManual.text.toString().trim()
        if (!isValidIpv4(ip)) {
            binding.tvStatusTele.text =
                "Esa IP no se ve bien. Ejemplo: 192.168.1.5"
            Toast.makeText(
                this,
                "Esa IP no se ve bien. Ejemplo: 192.168.1.5",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        binding.tvStatusTele.text = "Conectando con $ip…"
        conectar(ip)
    }

    /**
     * Comprueba la tele (checkReachable: 10 s, hasta 3 intentos,
     * cualquier HTTP vale) y SIEMPRE entra al portal: la IP se
     * guarda igual aunque el chequeo falle, y en ese caso el portal
     * muestra el aviso y al transmitir se reintenta la conexión.
     * Solo una IP inválida (formato) impide entrar.
     */
    private fun conectar(ip: String) {
        binding.btnConectarIp.isEnabled = false
        lifecycleScope.launch {
            val client = RokuClient(ip)
            val check = try {
                client.checkReachable()
            } catch (e: Exception) {
                RokuClient.CheckResult(false, null, false, false, null)
            }
            val name = check.deviceInfo?.friendlyName?.ifBlank { null }
                ?: prefs.getString("last_name", null)?.ifBlank { null }
                ?: "Roku TV"
            CastSession.rokuIp = ip
            CastSession.rokuName = name
            CastSession.tvWarning = if (check.reachable) {
                null
            } else {
                "La tele no respondió en esa IP, revisa que esté prendida. " +
                    "Podrás transmitir cuando responda"
            }
            prefs.edit()
                .putString("last_ip", ip)
                .putString("last_name", name)
                .apply()
            if (check.reachable) {
                binding.tvStatusTele.text = "Conectado con $name ($ip)."
            } else {
                binding.tvStatusTele.text =
                    "La tele no respondió en esa IP (${check.detalle()}). " +
                        "Entrando al portal…"
                Toast.makeText(
                    this@MainActivity,
                    "La tele no respondió en esa IP (${check.detalle()}). " +
                        "Podrás transmitir cuando responda.",
                    Toast.LENGTH_LONG
                ).show()
            }
            CastSession.activeTarget = RokuCastTarget(ip, name)
            TargetStore.save(this@MainActivity, CastSession.activeTarget!!)
            startActivity(Intent(this@MainActivity, PortalActivity::class.java))
            finish()
        }
    }
}

/**
 * IPv4 válida: cuatro octetos de 0 a 255 separados por tres puntos.
 * El campo es de texto normal (inputType text, sin filtros): el
 * teclado no limita los puntos; la validación es solo este regex
 * más el rango de cada octeto.
 *
 * Casos: «192.168.1.5» VÁLIDA, «1921681.78» INVÁLIDA,
 * «192.168.1.300» INVÁLIDA, «192.168.1» INVÁLIDA.
 */
fun isValidIpv4(ip: String): Boolean {
    val m = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""").matchEntire(ip.trim())
        ?: return false
    return m.groupValues.drop(1).all { part ->
        val n = part.toIntOrNull() ?: return false
        n in 0..255
    }
}
