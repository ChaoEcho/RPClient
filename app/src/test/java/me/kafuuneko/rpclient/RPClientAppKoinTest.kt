package me.kafuuneko.rpclient

import me.kafuuneko.rpclient.libs.groupchat.GroupChatPromptBuilder
import me.kafuuneko.rpclient.libs.prompt.ChatPromptBuilder
import me.kafuuneko.rpclient.libs.story.StoryPromptBuilder
import me.kafuuneko.rpclient.libs.story.StorySummaryPromptBuilder
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.koin.dsl.koinApplication

class RPClientAppKoinTest {
    @Test
    fun promptBuildersResolveFromApplicationModule() {
        val application = koinApplication {
            modules(appModules)
        }
        try {
            assertNotNull(application.koin.get<ChatPromptBuilder>())
            assertNotNull(application.koin.get<GroupChatPromptBuilder>())
            assertNotNull(application.koin.get<StoryPromptBuilder>())
            assertNotNull(application.koin.get<StorySummaryPromptBuilder>())
        } finally {
            application.close()
        }
    }
}
