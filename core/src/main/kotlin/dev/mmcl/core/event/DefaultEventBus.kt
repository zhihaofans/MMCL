package dev.mmcl.core.event

import dev.mmcl.api.event.EventBus
import dev.mmcl.api.event.Subscription
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass
import org.slf4j.Logger

/**
 * [EventBus] 的默认实现。
 *
 * 设计要点：
 * - 处理器异常逐个隔离：一个插件抛异常不会影响其它插件收到事件；
 * - 订阅/取消订阅线程安全，事件发布期间可以安全地增删订阅；
 * - 订阅按事件的具体类型精确匹配（不做父子类型派发）。
 *
 * @param logger 用于记录被隔离的处理器异常
 */
class DefaultEventBus(private val logger: Logger) : EventBus {
    private val handlers = ConcurrentHashMap<KClass<*>, CopyOnWriteArrayList<(Any) -> Unit>>()

    override fun <T : Any> publish(event: T) {
        val registered = handlers[event::class] ?: return
        for (handler in registered) {
            try {
                handler(event)
            } catch (t: Throwable) {
                logger.error("事件处理器抛出异常，已隔离：{}", event::class.qualifiedName, t)
            }
        }
    }

    override fun <T : Any> subscribe(type: KClass<T>, handler: (T) -> Unit): Subscription {
        val registered = handlers.computeIfAbsent(type) { CopyOnWriteArrayList() }
        val wrapper: (Any) -> Unit = { event ->
            @Suppress("UNCHECKED_CAST")
            handler(event as T)
        }
        registered.add(wrapper)
        return Subscription { registered.remove(wrapper) }
    }
}
