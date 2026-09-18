package com.satcop.smartvisitor.kiosk

import com.satcop.smartvisitor.kiosk.data.model.GateListResponse
import com.satcop.smartvisitor.kiosk.data.model.InsideListResponse
import com.satcop.smartvisitor.kiosk.data.model.LoginResponse
import com.satcop.smartvisitor.kiosk.data.model.StaffListResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class LiveDecodeSmokeTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun pranayGateLoginAndDirectoryDecode() {
        val base = "https://valley-questions-poultry-kid.trycloudflare.com/v1"
        val loginBody = """{"username":"pranay.gate","password":"PranayGate@2026"}"""
        val loginRaw = post(base + "/auth/login", loginBody, null)
        val login = json.decodeFromString(LoginResponse.serializer(), loginRaw)
        assertTrue(login.accessToken.isNotBlank())
        val token = login.accessToken
        for (path in listOf("/gates", "/staff?active=true", "/visits/inside")) {
            val raw = get(base + path, token)
            when {
                path.startsWith("/gates") -> {
                    val g = json.decodeFromString(GateListResponse.serializer(), raw)
                    assertTrue(g.data.isNotEmpty())
                }
                path.startsWith("/staff") -> {
                    val s = json.decodeFromString(StaffListResponse.serializer(), raw)
                    assertTrue(s.data.isNotEmpty())
                }
                else -> {
                    val i = json.decodeFromString(InsideListResponse.serializer(), raw)
                    assertTrue(i.data.isNotEmpty())
                }
            }
        }
    }

    private fun post(url: String, body: String, token: String?): String {
        val c = (URL(url).openConnection() as HttpURLConnection)
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
        c.outputStream.use { it.write(body.toByteArray()) }
        return c.inputStream.bufferedReader().readText()
    }

    private fun get(url: String, token: String): String {
        val c = (URL(url).openConnection() as HttpURLConnection)
        c.requestMethod = "GET"
        c.setRequestProperty("Authorization", "Bearer $token")
        return c.inputStream.bufferedReader().readText()
    }
}
