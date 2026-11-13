package com.farol.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cofre: cifra com uma chave AES guardada no Android Keystore (não sai do aparelho,
 * nem vai para backup). Usado para o token da sessão.
 */
object SecureStore {
    private const val ALIAS = "farol_conta_v1"
    private const val PREFS = "farol_conta"   // fora do Backup (que só exporta "farol" e "farol_carro")

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return gen.generateKey()
    }

    fun put(ctx: Context, name: String, value: String?) {
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (value == null) { p.edit().remove(name).apply(); return }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = c.iv + c.doFinal(value.toByteArray())
        p.edit().putString(name, Base64.encodeToString(out, Base64.NO_WRAP)).apply()
    }

    fun get(ctx: Context, name: String): String? = runCatching {
        val raw = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(name, null) ?: return null
        val b = Base64.decode(raw, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b, 0, 12)) }
        String(c.doFinal(b, 12, b.size - 12))
    }.getOrNull()

    fun plain(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Conta do usuário no servidor do Farol (e-mail e senha). */
object Account {
    const val DEFAULT_SERVER = "https://farol-logs.carlos-pjjj.workers.dev"

    data class User(val id: Long, val email: String, val name: String, val role: String)

    fun token(ctx: Context): String? = SecureStore.get(ctx, "token")
    fun isLoggedIn(ctx: Context) = !token(ctx).isNullOrBlank()

    fun user(ctx: Context): User? {
        val p = SecureStore.plain(ctx)
        val email = p.getString("email", null) ?: return null
        return User(p.getLong("id", 0), email, p.getString("nome", "") ?: "", p.getString("papel", "user") ?: "user")
    }

    fun server(ctx: Context): String =
        Settings(ctx).serverUrl.trim().trimEnd('/').removeSuffix("/v1/logs").ifBlank { DEFAULT_SERVER }

    /** Entrar ou criar conta. Roda fora da thread de tela. Lança erro com a mensagem do servidor. */
    fun authenticate(ctx: Context, register: Boolean, email: String, password: String, name: String, deviceId: String): User {
        val body = JSONObject().put("email", email.trim()).put("senha", password).put("device", deviceId).put("origem", "app")
        if (register) body.put("nome", name.trim())
        val res = post(server(ctx) + if (register) "/v1/auth/register" else "/v1/auth/login", body, null)
        val u = res.getJSONObject("usuario")
        val user = User(u.getLong("id"), u.getString("email"), u.optString("nome"), u.optString("papel", "user"))
        save(ctx, res.getString("token"), user)
        return user
    }

    private fun save(ctx: Context, token: String, u: User) {
        SecureStore.put(ctx, "token", token)
        SecureStore.plain(ctx).edit().putLong("id", u.id).putString("email", u.email)
            .putString("nome", u.name).putString("papel", u.role).apply()
        val s = Settings(ctx)
        // Com conta, os logs vão pelo servidor; o token do GitHub não precisa mais ficar no celular.
        s.remoteMode = "server"
        if (s.serverUrl.isBlank()) s.serverUrl = DEFAULT_SERVER
        s.ghToken = ""
        if (s.userName.isBlank() && u.name.isNotBlank()) s.userName = u.name
    }

    fun changePassword(ctx: Context, current: String, new: String) {
        post(server(ctx) + "/v1/auth/password", JSONObject().put("atual", current).put("nova", new), token(ctx))
    }

    /** Sai da conta (avisa o servidor se der; apaga o token de qualquer jeito). */
    fun logout(ctx: Context) {
        val t = token(ctx)
        if (t != null) runCatching { post(server(ctx) + "/v1/auth/logout", JSONObject(), t) }
        clear(ctx)
    }

    /** Sessão recusada pelo servidor (senha trocada, conta desativada...): pede login de novo. */
    fun clear(ctx: Context) {
        SecureStore.put(ctx, "token", null)
    }

    private fun post(url: String, body: JSONObject, token: String?): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.connectTimeout = 15_000; c.readTimeout = 20_000; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.setRequestProperty("User-Agent", "Farol-app")
            if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (code !in 200..299) error(json.optString("erro").ifBlank { "erro $code" })
            return json
        } catch (e: java.net.UnknownHostException) {
            error("sem internet")
        } catch (e: java.net.SocketTimeoutException) {
            error("o servidor demorou para responder")
        } finally { c.disconnect() }
    }
}
