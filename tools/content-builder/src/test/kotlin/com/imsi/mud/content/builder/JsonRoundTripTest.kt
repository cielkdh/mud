package com.imsi.mud.content.builder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule

class JsonRoundTripTest {
    @get:Rule val phase1Fixture = Phase1FixtureRule()
    @Test
    fun `P1-CT-002 canonical json round trips empty values and escaped strings`() {
        val value = JsonObject(mapOf(
            "empty" to JsonString(""),
            "object" to JsonObject(emptyMap()),
            "array" to JsonArray(emptyList()),
            "escaped" to JsonString("<tag>&\"quote\"")
        ))
        val rendered = value.render()
        assertEquals("{\"array\":[],\"empty\":\"\",\"escaped\":\"<tag>&\\\"quote\\\"\",\"object\":{}}", rendered)
        assertEquals(rendered, JsonParser.parse(rendered).render())
    }

    @Test
    fun `P1-CT-001 JSON parser rejects unpaired surrogate`() {
        val error = runCatching { JsonParser.parse("\"\uD800\"") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message.orEmpty().contains("surrogate"))
    }
}
