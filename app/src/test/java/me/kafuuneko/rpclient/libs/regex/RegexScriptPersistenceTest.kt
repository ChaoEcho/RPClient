package me.kafuuneko.rpclient.libs.regex

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class RegexScriptPersistenceTest {
    @Test
    fun roomEntityRoundTripsEverySupportedField() {
        val script = RegexScript(
            id = "script",
            scriptName = "Script",
            findRegex = "/x/g",
            replaceString = "y",
            trimStrings = listOf("a", "b"),
            placement = listOf(
                RegexPlacement.UserInput.value,
                RegexPlacement.AiResponse.value
            ),
            disabled = true,
            markdownOnly = true,
            promptOnly = true,
            runOnEdit = true,
            substituteRegex = RegexFindMacroMode.Escaped.value,
            minDepth = 2,
            maxDepth = 8,
            rawJson = """{"third_party":{"kept":true}}"""
        )

        val entity = script.toEntity(characterId = 42L, sortOrder = 3, gson = Gson())

        assertEquals(42L, entity.characterId)
        assertEquals(3, entity.sortOrder)
        assertEquals(script, entity.toDomain())
    }
}
