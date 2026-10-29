package dev.mmcl.api.event

import kotlin.reflect.KClass

/** 事件订阅句柄；调用 [cancel] 取消订阅，重复取消必须无副作用。 */
fun interface Subscription {
    /** 取消订阅。 */
    fun cancel()
}

/**
 * 事件总线：用于“通知已经发生的事”，不用于提供能力（能力走服务注册）。
 *
 * 约定：
 * - 订阅按事件的具体类型精确匹配；
 * - 单个处理器的异常由 core 捕获并记录，不影响其它处理器；
 * - 处理器应当尽快返回，长任务请交给协程。
 */
interface EventBus {
    /**
     * 广播一个事件。
     *
     * @param event 事件实例，不能为 `null`
     */
    fun <T : Any> publish(event: T)

    /**
     * 订阅某类型的事件。
     *
     * @param type 事件类型
     * @param handler 处理器；抛出的异常会被 core 隔离
     * @return 可用于取消订阅的句柄
     */
    fun <T : Any> subscribe(type: KClass<T>, handler: (T) -> Unit): Subscription
}
