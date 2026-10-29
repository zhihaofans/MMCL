package dev.mmcl.api.service

import kotlin.reflect.KClass

/**
 * 服务注册表：插件用它注册“可被消费的能力”，其它插件用它查找能力。
 *
 * 插件之间不允许直接引用对方的类，只能通过这里的接口类型通信（见 `AGENTS.md` §2）。
 */
interface ServiceRegistry {
    /**
     * 注册一个能力实例。同一个类型可以注册多个实现。
     *
     * @param type 能力接口类型
     * @param instance 实现实例
     */
    fun <T : Any> register(type: KClass<T>, instance: T)

    /**
     * 取某个类型最近注册的实例。
     *
     * @return 未注册时返回 `null`
     */
    fun <T : Any> get(type: KClass<T>): T?

    /**
     * 取某个类型的全部实例，按注册顺序返回。
     */
    fun <T : Any> getAll(type: KClass<T>): List<T>
}
