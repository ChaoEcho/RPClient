package me.kafuuneko.rpclient.feature

import android.content.Context
import androidx.annotation.StringRes
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.llm.GenerationFailure
import me.kafuuneko.rpclient.libs.llm.classifyGenerationFailure

/** 将生成异常转换为不包含供应商响应、请求内容或凭据的本地化消息。 */
internal fun Throwable.toGenerationFailureMessage(
    context: Context,
    @StringRes fallbackMessageResId: Int
): String? = when (val failure = classifyGenerationFailure(this)) {
    null -> null
    GenerationFailure.NoProvider -> context.getString(R.string.generation_error_no_provider)
    GenerationFailure.CharacterProviderUnavailable -> context.getString(
        R.string.generation_error_character_provider_unavailable
    )
    GenerationFailure.SummaryProviderUnavailable -> context.getString(
        R.string.generation_error_summary_provider_unavailable
    )
    is GenerationFailure.PromptBudget -> context.getString(
        R.string.generation_error_prompt_budget,
        failure.requiredTokens,
        failure.promptBudget
    )
    GenerationFailure.Unauthorized -> context.getString(R.string.generation_error_unauthorized)
    GenerationFailure.Forbidden -> context.getString(R.string.generation_error_forbidden)
    GenerationFailure.RateLimited -> context.getString(R.string.generation_error_rate_limited)
    is GenerationFailure.HttpFailure -> context.getString(
        R.string.generation_error_http,
        failure.statusCode
    )
    GenerationFailure.Network -> context.getString(R.string.generation_error_network)
    GenerationFailure.EmptyResponse -> context.getString(R.string.generation_error_empty_response)
    GenerationFailure.Unknown -> context.getString(fallbackMessageResId)
}
