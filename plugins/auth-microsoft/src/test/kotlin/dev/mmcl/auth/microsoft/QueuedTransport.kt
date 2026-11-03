package dev.mmcl.auth.microsoft

/** 脚本化的假 HTTP：按调用顺序返回预设响应，测试不碰真实网络。 */
internal class QueuedTransport(vararg replies: Reply) : HttpTransport {
    private val queue = ArrayDeque(replies.toList())

    /** 记录调用，便于断言"走了哪条链"。 */
    val calls = mutableListOf<String>()

    override suspend fun postForm(url: String, form: Map<String, String>): String {
        calls += "FORM $url"
        return next()
    }

    override suspend fun postJson(url: String, json: String, headers: Map<String, String>): String {
        calls += "JSON $url"
        return next()
    }

    override suspend fun getJson(url: String, bearerToken: String): String {
        calls += "GET $url"
        return next()
    }

    private fun next(): String {
        val reply = queue.removeFirstOrNull() ?: error("没有更多脚本响应，最后一次调用：${calls.lastOrNull()}")
        return when (reply) {
            is Reply.Body -> reply.text
            is Reply.Failure -> throw HttpStatusException(reply.status, reply.body)
        }
    }
}

/** 一次预设响应。 */
internal sealed interface Reply {
    /** 2xx 响应体。 */
    data class Body(val text: String) : Reply

    /** 非 2xx；[body] 用于 XSTS 的 XErr 等错误体。 */
    data class Failure(val status: Int, val body: String = "") : Reply
}
