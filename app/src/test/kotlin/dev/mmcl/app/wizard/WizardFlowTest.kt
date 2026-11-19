package dev.mmcl.app.wizard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WizardFlowTest {
    @Test
    fun `没有下载源时不能离开第一步`() {
        assertNull(WizardFlow.next(WizardStep.SOURCE, hasSource = false))
    }

    @Test
    fun `有下载源时依次推进`() {
        assertEquals(WizardStep.JAVA, WizardFlow.next(WizardStep.SOURCE, hasSource = true))
        assertEquals(WizardStep.VERSION, WizardFlow.next(WizardStep.JAVA, hasSource = true))
        assertEquals(WizardStep.INSTALL, WizardFlow.next(WizardStep.VERSION, hasSource = true))
        assertNull(WizardFlow.next(WizardStep.INSTALL, hasSource = true))
    }

    @Test
    fun `没有 Java 运行时来源也能继续`() {
        assertEquals(WizardStep.VERSION, WizardFlow.next(WizardStep.JAVA, hasSource = false))
    }

    @Test
    fun `上一步回到正确的步骤`() {
        assertEquals(WizardStep.SOURCE, WizardFlow.previous(WizardStep.JAVA))
        assertEquals(WizardStep.JAVA, WizardFlow.previous(WizardStep.VERSION))
        assertEquals(WizardStep.VERSION, WizardFlow.previous(WizardStep.INSTALL))
        assertEquals(WizardStep.SOURCE, WizardFlow.previous(WizardStep.SOURCE))
    }

    @Test
    fun `步骤计数从 1 开始`() {
        assertEquals(1, WizardFlow.position(WizardStep.SOURCE))
        assertEquals(4, WizardFlow.position(WizardStep.INSTALL))
        assertEquals(4, WizardFlow.total())
    }
}
