package me.kafuuneko.rpclient.feature.main.presentation

import me.kafuuneko.rpclient.libs.prompt.SummaryInjectionPosition
import me.kafuuneko.rpclient.libs.prompt.SummaryInjectionRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MainSettingsStateTest {
    @Test
    fun summaryInjectionPositionBuildsSpecializedState() {
        assertSame(
            MainSummaryInjectionState.None,
            SummaryInjectionPosition.None.toMainSummaryInjectionState(
                depth = 3,
                role = SummaryInjectionRole.User
            )
        )
        assertSame(
            MainSummaryInjectionState.BeforeMain,
            SummaryInjectionPosition.BeforeMain.toMainSummaryInjectionState(
                depth = 3,
                role = SummaryInjectionRole.User
            )
        )
        assertSame(
            MainSummaryInjectionState.AfterMain,
            SummaryInjectionPosition.AfterMain.toMainSummaryInjectionState(
                depth = 3,
                role = SummaryInjectionRole.User
            )
        )
        assertEquals(
            MainSummaryInjectionState.InChat(
                depth = 3,
                role = SummaryInjectionRole.User
            ),
            SummaryInjectionPosition.InChat.toMainSummaryInjectionState(
                depth = 3,
                role = SummaryInjectionRole.User
            )
        )
    }
}
