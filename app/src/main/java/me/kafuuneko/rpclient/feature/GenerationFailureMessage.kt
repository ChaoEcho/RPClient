package me.kafuuneko.rpclient.feature

import android.content.Context
import androidx.annotation.StringRes
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.llm.GenerationFailure
import me.kafuuneko.rpclient.libs.llm.classifyGenerationFailure

/** 模型配置引导对话框所需的脱敏展示内容。 */
internal data class ModelSettingsGuideContent(
    val title: String,
    val message: String
)

/** 一次生成失败的脱敏提示及可选模型配置引导。 */
internal data class GenerationFailurePresentation(
    val message: String,
    val modelSettingsGuide: ModelSettingsGuideContent?
)

/** 将生成异常转换为不包含模型服务响应、请求内容或凭据的本地化展示信息。 */
internal fun Throwable.toGenerationFailurePresentation(
    context: Context,
    @StringRes fallbackMessageResId: Int
): GenerationFailurePresentation? {
    val failure = classifyGenerationFailure(this) ?: return null
    return failure.toGenerationFailurePresentation(context, fallbackMessageResId)
}

/** 创建当前没有可用模型配置时的标准引导内容。 */
internal fun noProviderModelSettingsGuide(context: Context): ModelSettingsGuideContent {
    return ModelSettingsGuideContent(
        title = context.getString(R.string.no_model_provider_title),
        message = context.getString(R.string.no_model_provider_desc)
    )
}

/** 将安全失败分类转换为页面可以直接渲染的本地化展示信息。 */
private fun GenerationFailure.toGenerationFailurePresentation(
    context: Context,
    @StringRes fallbackMessageResId: Int
): GenerationFailurePresentation {
    // 页面正文始终使用固定本地化文案，不读取供应商原始响应
    val message = toGenerationFailureMessage(context, fallbackMessageResId)
    // 只有模型配置或 HTTP 访问类错误提供配置入口，避免误导网络和空响应问题
    val guide = when (this) {
        GenerationFailure.NoProvider -> noProviderModelSettingsGuide(context)
        GenerationFailure.CharacterProviderUnavailable,
        GenerationFailure.SummaryProviderUnavailable,
        GenerationFailure.Unauthorized,
        GenerationFailure.Forbidden,
        GenerationFailure.RateLimited,
        GenerationFailure.RequestFailure,
        is GenerationFailure.HttpFailure -> ModelSettingsGuideContent(
            title = context.getString(R.string.model_request_failed_title),
            message = context.getString(R.string.model_request_failed_desc, message)
        )
        is GenerationFailure.PromptBudget,
        GenerationFailure.Network,
        GenerationFailure.EmptyResponse,
        GenerationFailure.Unknown -> null
    }
    return GenerationFailurePresentation(message, guide)
}

/** 将安全失败分类转换为不包含原始响应的本地化错误消息。 */
private fun GenerationFailure.toGenerationFailureMessage(
    context: Context,
    @StringRes fallbackMessageResId: Int
): String = when (this) {
    GenerationFailure.NoProvider -> context.getString(R.string.generation_error_no_provider)
    GenerationFailure.CharacterProviderUnavailable -> context.getString(
        R.string.generation_error_character_provider_unavailable
    )
    GenerationFailure.SummaryProviderUnavailable -> context.getString(
        R.string.generation_error_summary_provider_unavailable
    )
    is GenerationFailure.PromptBudget -> context.getString(
        R.string.generation_error_prompt_budget,
        requiredTokens,
        promptBudget
    )
    GenerationFailure.Unauthorized -> context.getString(R.string.generation_error_unauthorized)
    GenerationFailure.Forbidden -> context.getString(R.string.generation_error_forbidden)
    GenerationFailure.RateLimited -> context.getString(R.string.generation_error_rate_limited)
    GenerationFailure.RequestFailure -> context.getString(fallbackMessageResId)
    is GenerationFailure.HttpFailure -> context.getString(
        R.string.generation_error_http,
        statusCode
    )
    GenerationFailure.Network -> context.getString(R.string.generation_error_network)
    GenerationFailure.EmptyResponse -> context.getString(R.string.generation_error_empty_response)
    GenerationFailure.Unknown -> context.getString(fallbackMessageResId)
}
