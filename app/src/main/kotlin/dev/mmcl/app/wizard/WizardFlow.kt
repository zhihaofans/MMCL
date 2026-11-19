package dev.mmcl.app.wizard

/** 首次启动向导的四个步骤。 */
enum class WizardStep {
    /** 下载源（没有它就无法安装游戏）。 */
    SOURCE,

    /** Java 运行时来源（可选）。 */
    JAVA,

    /** 选择游戏版本。 */
    VERSION,

    /** 安装并建实例。 */
    INSTALL,
}

/**
 * 向导的推进规则，纯逻辑便于单元测试。
 *
 * - 没有下载源时**不允许**离开 [WizardStep.SOURCE]（装不了游戏），但用户仍可整体跳过向导
 * - Java 运行时来源只是可选项：没有提供者也能继续
 */
object WizardFlow {
    /** 步骤顺序。 */
    val order: List<WizardStep> = WizardStep.entries

    /**
     * 下一步；返回 `null` 表示当前步不允许前进。
     *
     * @param hasSource 是否已注册版本源插件
     */
    fun next(step: WizardStep, hasSource: Boolean): WizardStep? =
        when (step) {
            WizardStep.SOURCE -> if (hasSource) WizardStep.JAVA else null
            WizardStep.JAVA -> WizardStep.VERSION
            WizardStep.VERSION -> WizardStep.INSTALL
            WizardStep.INSTALL -> null
        }

    /** 上一步；第一步返回自身。 */
    fun previous(step: WizardStep): WizardStep =
        when (step) {
            WizardStep.SOURCE -> WizardStep.SOURCE
            WizardStep.JAVA -> WizardStep.SOURCE
            WizardStep.VERSION -> WizardStep.JAVA
            WizardStep.INSTALL -> WizardStep.VERSION
        }

    /** 当前是第几步（从 1 开始），用于界面上的「2 / 4」。 */
    fun position(step: WizardStep): Int = order.indexOf(step) + 1

    /** 总步数。 */
    fun total(): Int = order.size
}
