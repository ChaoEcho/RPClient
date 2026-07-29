package me.kafuuneko.rpclient.libs.llm.catalog.adapter

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogJsonUtilsTest {
    @Test
    fun stringCollectionsIgnoreNonStringMembers() {
        val values = JsonParser.parseString(
            """["temperature", 1, true, null, {"name":"top_p"}, ["max_tokens"], "temperature", " "]"""
        ).asJsonArray

        assertEquals(setOf("temperature"), values.toStringSet())
        assertTrue(values.containsString("temperature"))
        assertFalse(values.containsString("1"))
        assertFalse(values.containsString("true"))
    }
}
