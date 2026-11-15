package dev.kastapi.validation

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmailRuleTest {
    /** Original v0.1 regex (quadratic): serves as the behavioral reference. */
    private val reference = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    private fun accepts(text: String): Boolean = EmailRule.check(JsonPrimitive(text)) == null

    @Test
    fun `accepts and rejects like the reference expression in known cases`() {
        val cases =
            listOf(
                "a@b.c",
                "demo@kastapi.dev",
                "a.b@c.d.e",
                "a@b.c.",
                "a@.c",
                "a@b.",
                "a@..",
                "a@...",
                "a@.b.",
                "@b.c",
                "a@",
                "a@b",
                "a@@b.c",
                "a@b@c.d",
                "a b@c.d",
                "a@b .c",
                " a@b.c",
                "a@b.c ",
                "a@b.c\n",
                "a@b.c\t",
                "",
                "@",
                "a@b.c.d",
                "a+tag@b.co",
                "ñ@b.c",
                "a@b\u000B.c",
                "a@b\u000C.c",
                "a@b.c\r",
            )
        for (case in cases) {
            assertEquals(reference.matches(case), accepts(case), "caso: ${case.replace("\n", "\\n")}")
        }
    }

    @Test
    fun `matches the reference expression on all short strings of a reduced alphabet`() {
        val alphabet = listOf('a', '.', '@', ' ')
        var strings = listOf("")
        repeat(7) {
            strings = strings.flatMap { s -> alphabet.map { s + it } }
            for (s in strings) assertEquals(reference.matches(s), accepts(s), "caso: '$s'")
        }
    }

    @Test
    fun `a hostile input of 200000 characters is evaluated in linear time`() {
        val hostile = "a@" + ".".repeat(200_000) + " "
        val start = System.nanoTime()
        val result = accepts(hostile)
        val millis = (System.nanoTime() - start) / 1_000_000
        assertEquals(false, result)
        assertTrue(millis < 1_000, "took $millis ms; the original expression takes tens of seconds")
    }
}
