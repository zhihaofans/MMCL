package dev.mmcl.app.java

/** 设置键：Java 运行时策略。 */
const val JAVA_POLICY_KEY: String = "java.runtime.policy"

/** 系统优先：系统 JDK 够用就用，缺失才下载（默认）。 */
const val JAVA_POLICY_SYSTEM: String = "system"

/** 自带优先：始终使用启动器管理的 JDK，缺失时自动下载。 */
const val JAVA_POLICY_BUNDLED: String = "bundled"

/** 归一化策略值；未知值按系统优先处理。 */
fun javaRuntimePolicy(raw: String?): String =
    if (raw == JAVA_POLICY_BUNDLED) JAVA_POLICY_BUNDLED else JAVA_POLICY_SYSTEM
