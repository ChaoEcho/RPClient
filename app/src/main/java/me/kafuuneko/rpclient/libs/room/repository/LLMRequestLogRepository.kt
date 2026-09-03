package me.kafuuneko.rpclient.libs.room.repository

import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.room.RequestLogDatabase
import me.kafuuneko.rpclient.libs.room.entity.LLMRequestLog
import me.kafuuneko.rpclient.libs.room.model.LLMRequestLogOverview

/**
 * 调试请求日志仓库。
 *
 * 原始请求/响应包含完整提示词，隐私敏感度远高于脱敏后的运行日志，因此它是
 * [AppModel.developerLoggingEnabled] 之下的子开关：主开关关闭时一律不记录。
 */
class LLMRequestLogRepository(
    private val mRequestLogDatabase: RequestLogDatabase
) {
    private val mLLMRequestLogDao = mRequestLogDatabase.getLLMRequestLogDao()

    /** 按最新优先读取列表摘要，不把完整原始载荷带入页面状态。 */
    suspend fun getLogOverviews(
        previewLength: Int,
        limit: Int,
        offset: Int
    ): List<LLMRequestLogOverview> {
        return mLLMRequestLogDao.getLogOverviews(previewLength, limit, offset)
    }

    /** 导出用：分页读取完整行，调用方逐页写盘。 */
    suspend fun getLogs(limit: Int, offset: Int): List<LLMRequestLog> {
        return mLLMRequestLogDao.getLogs(limit, offset)
    }

    /** 按需读取单条日志的完整请求 JSON。 */
    suspend fun getRequestJson(id: Long): String? {
        return mLLMRequestLogDao.getRequestJson(id)
    }

    /** 按需读取单条日志的完整响应 JSON。 */
    suspend fun getResponseJson(id: Long): String? {
        return mLLMRequestLogDao.getResponseJson(id)
    }

    /** 条件写入一次完整请求/响应；非调试模式直接返回 0。 */
    suspend fun saveLog(
        provider: LLMProviderConfig,
        model: String,
        isStreaming: Boolean,
        requestJson: String,
        responseJson: String
    ): Long {
        if (!AppModel.developerLoggingEnabled || !AppModel.debugModeEnabled) return 0L
        val id = mLLMRequestLogDao.insertOrReplace(
            LLMRequestLog(
                providerName = provider.name,
                providerType = provider.providerType,
                protocol = provider.protocol,
                model = model,
                isStreaming = isStreaming,
                requestJson = requestJson,
                responseJson = responseJson
            )
        )
        // 每条日志都带完整请求与响应 JSON，没有上限会把调试库撑到几百 MB。
        mLLMRequestLogDao.trimToMostRecent(MAX_RETAINED_LOGS)
        return id
    }

    /** 清空本地调试日志。 */
    suspend fun deleteAll() {
        mLLMRequestLogDao.deleteAll()
    }

    private companion object {
        /**
         * minimal-debt: 固定保留条数；若需要按天或按体积保留，再引入配置项。
         */
        const val MAX_RETAINED_LOGS = 500
    }
}
