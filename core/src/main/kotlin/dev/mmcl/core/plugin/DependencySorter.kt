package dev.mmcl.core.plugin

/**
 * 按硬依赖做拓扑排序，并把不可用的插件分拣出来。
 *
 * 规则（对应 `ARCHITECTURE.md` §10）：
 * - 硬依赖缺失 → 标记 [Result.missing]，不参与排序；
 * - 依赖的插件本身不可用（缺失/循环）→ 递归标记为不可用；
 * - 循环依赖 → 参与循环的插件全部标记 [Result.cyclic]；
 * - 软依赖只用于声明协作关系，不参与排序，也不影响可用性；
 * - 输出顺序稳定：同层节点按 id 升序。
 */
class DependencySorter {
    /**
     * 排序结果。
     *
     * @property order 可加载插件的顺序，依赖一定排在被依赖者之前
     * @property cyclic 处于循环依赖（或依赖了循环插件）的插件 id
     * @property missing 不可用的插件 id → 缺失/不可用的依赖 id 列表
     */
    data class Result(
        val order: List<String>,
        val cyclic: Set<String>,
        val missing: Map<String, List<String>>,
    )

    /**
     * @param descriptors 待排序插件；同一 id 出现多次时行为未定义，调用方需先去重
     */
    fun sort(descriptors: Collection<PluginDescriptor>): Result {
        val byId = descriptors.associateBy { it.id }

        // 1) 直接缺失：依赖的 id 根本不存在
        val missing = sortedMapOf<String, List<String>>()
        for (id in byId.keys.sorted()) {
            val absent = byId.getValue(id).dependencies.filter { it !in byId }
            if (absent.isNotEmpty()) missing[id] = absent
        }

        // 2) 传递缺失：依赖了一个不可用的插件
        var changed = true
        while (changed) {
            changed = false
            for (id in byId.keys.sorted()) {
                if (id in missing) continue
                val blocked = byId.getValue(id).dependencies.filter { it in missing }
                if (blocked.isNotEmpty()) {
                    missing[id] = blocked
                    changed = true
                }
            }
        }

        val candidates = byId.keys.filter { it !in missing }

        // 3) 自依赖直接算循环
        val cyclic = linkedSetOf<String>()
        for (id in candidates) {
            if (id in byId.getValue(id).dependencies) cyclic += id
        }
        val nodes = candidates.filter { it !in cyclic }

        // 4) Kahn 拓扑排序
        val dependents = mutableMapOf<String, MutableList<String>>()
        val inDegree = nodes.associateWith { 0 }.toMutableMap()
        for (id in nodes) {
            for (dependency in byId.getValue(id).dependencies.distinct()) {
                if (dependency !in nodes) continue
                dependents.getOrPut(dependency) { mutableListOf() }.add(id)
                inDegree[id] = inDegree.getValue(id) + 1
            }
        }

        val ready = java.util.PriorityQueue<String>()
        nodes.filter { inDegree.getValue(it) == 0 }.forEach(ready::add)
        val order = mutableListOf<String>()
        while (ready.isNotEmpty()) {
            val id = ready.poll()
            order += id
            for (dependent in dependents[id].orEmpty().sorted()) {
                val remaining = inDegree.getValue(dependent) - 1
                inDegree[dependent] = remaining
                if (remaining == 0) ready += dependent
            }
        }

        // 5) 排序后剩下的节点就是循环依赖
        cyclic += nodes.filter { it !in order.toSet() }

        // 6) 依赖了循环插件的，同样不可用
        changed = true
        while (changed) {
            changed = false
            for (id in order.toList()) {
                if (byId.getValue(id).dependencies.any { it in cyclic }) {
                    order.remove(id)
                    cyclic += id
                    changed = true
                }
            }
        }

        return Result(order = order, cyclic = cyclic, missing = missing)
    }
}
