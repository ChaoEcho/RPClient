package me.kafuuneko.rpclient.libs.prompt.model

/** 示例对话在 Prompt 预算中的保留策略。 */
enum class ExampleDialogueBehavior(val persistedValue: Int) {
    /** 优先保留真实历史，示例对话先被裁剪。 */
    Normal(0),

    /** 优先保留示例对话，之后才裁剪可丢弃的历史。 */
    Pinned(1),

    /** 不向模型发送角色卡和世界书的示例对话。 */
    Disabled(2);

    companion object {
        val default: ExampleDialogueBehavior = Normal

        fun fromPersistedValue(value: Int): ExampleDialogueBehavior {
            return entries.firstOrNull { it.persistedValue == value } ?: default
        }
    }
}

/** 为一次 Prompt 构建提供当前示例对话策略。 */
fun interface ExampleDialogueBehaviorProvider {
    fun current(): ExampleDialogueBehavior
}
