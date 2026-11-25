package com.elanticristo616.browser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.elanticristo616.browser.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * CuteNovna Browser v1.0.2 — PANTALLA 1: LA PUERTA.
 *
 * La IP de la tele es el requisito para entrar al portal: esta
 * pantalla es SOLO «Conectar a la tele», nada más. Sin buscador,
 * sin accesos rápidos, sin barra inferior y sin descubrimiento:
 * la conexión es solo por IP manual.
 *
 * Al pulsar «Conectar por IP» se valida la IPv4 y se comprueba la
 * tele con RokuClient.checkReachable: GET
 * http://<ip>:8060/query/device-info, timeout 10 s, hasta 3
 * intentos, y CUALQUIER código HTTP cuenta como «tele alcanzable».
 *
 * v1.0.2 — LA PUERTA YA NO BLOQUEA: si el chequeo falla tras los
 * reintentos, la IP se guarda IGUAL y se entra al portal con un
 * aviso visible («La tele no respondió en esa IP, revisa que esté
 * prendida. Podrás transmitir cuando responda»). La v1.0.1 dejaba
 * fuera al usuario con un falso negativo: parseDeviceInfo borraba
 * el nombre/modelo con el texto en blanco entre etiquetas del XML
 * (ver RokuClient), así que queryDeviceInfo siempre devolvía null
 * aunque la tele respondiera 200. La IP sigue siendo obligatoria
 * para entrar; lo que cambia es que un chequeo que falle ya no
 * deja al usuario fuera de su propia app. Al transmitir se
 * reintenta la conexión en ese momento (CastHelper).
 *
 * Al abrir la app, si hay una IP guardada se intenta conectar
 * automáticamente con la misma comprobación; si la tele responde
 * se entra directo, y si no, se entra igual con el aviso.
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

        if (savedIp != null) {
            binding.tvStatusTele.text = "Conectando con $savedIp…"
            conectar(savedIp)
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
