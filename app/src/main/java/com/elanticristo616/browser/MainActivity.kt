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
 * Anticristo Browser v1.0.1 — PANTALLA 1: LA PUERTA.
 *
 * La IP de la tele es el requisito para entrar al portal: esta
 * pantalla es SOLO «Conectar a la tele», nada más. Sin buscador,
 * sin accesos rápidos, sin barra inferior y sin descubrimiento:
 * la conexión es solo por IP manual.
 *
 * Al pulsar «Conectar por IP» se valida la IPv4 y se comprueba que
 * la tele responde con GET http://<ip>:8060/query/device-info
 * (timeout 4 s, el que ya trae RokuClient.ping/queryDeviceInfo).
 * Si responde, la IP se guarda y se abre el portal; si no, error
 * en español y se queda en la puerta.
 *
 * Al abrir la app, si hay una IP guardada se intenta conectar
 * automáticamente con la misma comprobación; si la tele responde,
 * se entra directo al portal; si no, se muestra la puerta.
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

    /** Valida la IP escrita y comprueba que la tele responde. */
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
     * Comprueba la tele con GET http://<ip>:8060/query/device-info
     * (RokuClient.queryDeviceInfo, timeout 4 s). Si responde, guarda
     * la IP y abre el portal; si no, error en español en la puerta.
     */
    private fun conectar(ip: String) {
        binding.btnConectarIp.isEnabled = false
        lifecycleScope.launch {
            val client = RokuClient(ip)
            val info = try {
                client.queryDeviceInfo()
            } catch (e: Exception) {
                null
            }
            if (info != null) {
                val name = info.friendlyName.ifBlank { "Roku TV" }
                CastSession.rokuIp = ip
                CastSession.rokuName = name
                prefs.edit()
                    .putString("last_ip", ip)
                    .putString("last_name", name)
                    .apply()
                startActivity(Intent(this@MainActivity, PortalActivity::class.java))
                finish()
            } else {
                binding.btnConectarIp.isEnabled = true
                binding.tvStatusTele.text =
                    "No responde ninguna Roku en esa IP. Revisa que la tele " +
                        "esté prendida y la IP sea la correcta."
            }
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
