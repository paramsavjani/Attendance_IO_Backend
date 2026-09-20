package com.attendanceio.api.service.app

import com.attendanceio.api.model.app.WebUpdateRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebBundleServiceTest {
    @TempDir
    lateinit var dir: Path

    private fun service() = WebBundleService(dir.toString(), "https://api.example.com/api/app/bundles/", JsonMapper.builder().build())

    private fun publish(version: String = "1.0.312", minNative: Int = 300) {
        Files.writeString(
            dir.resolve("latest.json"),
            """{"version":"$version","file":"web-$version.zip","sha256":"abc123","minNativeVersionCode":$minNative,"commit":"deadbeef"}"""
        )
        Files.writeString(dir.resolve("web-$version.zip"), "zip")
    }

    private fun phone(running: String, nativeCode: String = "305") =
        WebUpdateRequest(platform = "android", versionName = running, versionBuild = "1.0.$nativeCode", versionCode = nativeCode)

    @Test
    fun `offers the bundle to a phone on builtin or an older bundle`() {
        publish()
        val fresh = service().check(phone("builtin"))
        assertEquals("1.0.312", fresh.version)
        assertEquals("https://api.example.com/api/app/bundles/web-1.0.312.zip", fresh.url)
        assertEquals("abc123", fresh.checksum)
        assertNull(fresh.error)
        assertTrue(service().check(phone("1.0.300")).url != null)
    }

    @Test
    fun `says up to date when the phone already runs the latest bundle`() {
        publish()
        val res = service().check(phone("1.0.312"))
        assertNull(res.url)
        assertEquals(WebBundleService.UP_TO_DATE, res.error)
    }

    @Test
    fun `withholds a bundle from native versions that are too old`() {
        publish(minNative = 310)
        val res = service().check(phone("builtin", nativeCode = "305"))
        assertNull(res.url)
        assertEquals(WebBundleService.NATIVE_TOO_OLD, res.error)
        assertEquals(true, res.breaking)
    }

    @Test
    fun `reads the plugin's snake_case request and writes a compact response`() {
        val mapper = JsonMapper.builder().findAndAddModules().build()
        val req = mapper.readValue(
            """{"platform":"android","app_id":"com.attendanceio.app","version_build":"1.0.305","version_code":"305","version_name":"builtin","is_prod":true,"unknown":1}""",
            WebUpdateRequest::class.java
        )
        assertEquals("305", req.versionCode)
        assertEquals("builtin", req.versionName)
        publish()
        val json = mapper.writeValueAsString(service().check(req))
        assertEquals("""{"version":"1.0.312","url":"https://api.example.com/api/app/bundles/web-1.0.312.zip","checksum":"abc123"}""", json)
    }

    @Test
    fun `no manifest means nothing to do`() {
        val res = service().check(phone("builtin"))
        assertNull(res.url)
        assertEquals(WebBundleService.NO_BUNDLE, res.error)
    }

    @Test
    fun `serves only plain zip names inside the directory`() {
        publish()
        assertTrue(service().bundleFile("web-1.0.312.zip") != null)
        assertNull(service().bundleFile("missing.zip"))
        assertNull(service().bundleFile("../latest.json"))
        assertNull(service().bundleFile("latest.json"))
    }
}
