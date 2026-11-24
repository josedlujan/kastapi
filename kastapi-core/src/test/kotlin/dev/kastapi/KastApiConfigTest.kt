package dev.kastapi

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KastApiConfigTest {
    @Test
    fun `no arguments gives the defaults`() {
        val config = KastApiConfig()
        assertEquals("/docs", config.docsPath)
        assertEquals("/openapi.json", config.openApiPath)
        assertTrue(config.validateOnStartup)
        assertEquals(1_048_576L, config.maxBodyBytes)
        assertEquals(false, config.json.configuration.ignoreUnknownKeys)
        assertEquals(false, config.json.configuration.explicitNulls)
        assertEquals(true, config.json.configuration.encodeDefaults)
    }

    @Test
    fun `an empty builder block gives the defaults`() {
        assertEquals(KastApiConfig(), KastApiConfig { })
    }

    @Test
    fun `the builder sets every property`() {
        val json = Json { ignoreUnknownKeys = true }
        val config =
            KastApiConfig {
                docsPath = "/help"
                openApiPath = "/schema.json"
                this.json = json
                validateOnStartup = false
                maxBodyBytes = 10
            }
        assertEquals("/help", config.docsPath)
        assertEquals("/schema.json", config.openApiPath)
        assertEquals(json, config.json)
        assertEquals(false, config.validateOnStartup)
        assertEquals(10L, config.maxBodyBytes)
    }

    @Test
    fun `the builder can disable the docs and the schema`() {
        val config =
            KastApiConfig {
                docsPath = null
                openApiPath = null
            }
        assertNull(config.docsPath)
        assertNull(config.openApiPath)
    }

    @Test
    fun `a non-positive limit is rejected by the builder`() {
        assertFailsWith<IllegalArgumentException> { KastApiConfig { maxBodyBytes = 0 } }
        assertFailsWith<IllegalArgumentException> { KastApiConfig { maxBodyBytes = -1 } }
        assertTrue(runCatching { KastApiConfig { maxBodyBytes = 1 } }.isSuccess)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `the deprecated named-argument form still works`() {
        val config = KastApiConfig(docsPath = "/help", maxBodyBytes = 10)
        assertEquals("/help", config.docsPath)
        assertEquals("/openapi.json", config.openApiPath)
        assertEquals(10L, config.maxBodyBytes)
        assertTrue(config.validateOnStartup)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `a non-positive limit is rejected by the deprecated form`() {
        assertFailsWith<IllegalArgumentException> { KastApiConfig(maxBodyBytes = 0) }
        assertFailsWith<IllegalArgumentException> { KastApiConfig(maxBodyBytes = -5) }
    }

    @Test
    @Suppress("DEPRECATION")
    fun `both forms build equal configurations`() {
        val json = Json { ignoreUnknownKeys = true }
        val old = KastApiConfig("/a", "/b", json, false, 7)
        val new =
            KastApiConfig {
                docsPath = "/a"
                openApiPath = "/b"
                this.json = json
                validateOnStartup = false
                maxBodyBytes = 7
            }
        assertEquals(old, new)
        assertEquals(old.hashCode(), new.hashCode())
    }

    @Test
    fun `equals and hashCode compare every property`() {
        val base = KastApiConfig()
        assertEquals(base, KastApiConfig())
        assertEquals(base.hashCode(), KastApiConfig().hashCode())
        assertNotEquals(base, KastApiConfig { docsPath = "/x" })
        assertNotEquals(base, KastApiConfig { openApiPath = "/x" })
        assertNotEquals(base, KastApiConfig { validateOnStartup = false })
        assertNotEquals(base, KastApiConfig { maxBodyBytes = 2 })
        assertNotEquals(base, KastApiConfig { json = Json { ignoreUnknownKeys = true } })
    }

    @Test
    fun `toString lists the properties`() {
        val text = KastApiConfig { docsPath = "/help" }.toString()
        assertTrue(text.startsWith("KastApiConfig("), text)
        assertTrue("docsPath=/help" in text, text)
        assertTrue("maxBodyBytes=1048576" in text, text)
    }
}
