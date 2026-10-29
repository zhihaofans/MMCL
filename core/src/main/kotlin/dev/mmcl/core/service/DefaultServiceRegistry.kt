package dev.mmcl.core.service

import dev.mmcl.api.service.ServiceRegistry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

/**
 * [ServiceRegistry] 的默认实现：按接口类型保存多个实例，注册顺序稳定。
 *
 * 同一个类型允许注册多个实现（例如多个 [dev.mmcl.api.capability.DownloadSource]）；
 * [get] 返回最后注册的那个，[getAll] 返回全部。
 */
class DefaultServiceRegistry : ServiceRegistry {
    private val services = ConcurrentHashMap<KClass<*>, CopyOnWriteArrayList<Any>>()

    override fun <T : Any> register(type: KClass<T>, instance: T) {
        services.computeIfAbsent(type) { CopyOnWriteArrayList() }.add(instance)
    }

    override fun <T : Any> get(type: KClass<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return services[type]?.lastOrNull() as T?
    }

    override fun <T : Any> getAll(type: KClass<T>): List<T> {
        val registered = services[type] ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        return registered.map { it as T }
    }
}
