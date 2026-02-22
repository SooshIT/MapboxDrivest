package com.drivest.navigation.subscription

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PaymentsCopyTest {

    @Test
    fun disclosureAndPriceStringsExist() {
        val xml = stringsXmlText()
        assertStringNonEmpty(xml, "payments_disclosure_auto_renew")
        assertStringNonEmpty(xml, "payments_disclosure_cancel")
        assertStringNonEmpty(xml, "payments_disclosure_refund")
        assertStringNonEmpty(xml, "payments_price_practice_monthly")
        assertStringNonEmpty(xml, "payments_price_global_annual")
    }

    private fun stringsXmlText(): String {
        val candidates = listOf(
            File("src/main/res/values/strings.xml"),
            File("android/app/src/main/res/values/strings.xml")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("strings.xml not found in expected paths")
        return file.readText()
    }

    private fun assertStringNonEmpty(xml: String, name: String) {
        val regex = Regex(
            "<string\\s+name=\"$name\">([\\s\\S]*?)</string>",
            setOf(RegexOption.IGNORE_CASE)
        )
        val value = regex.find(xml)?.groupValues?.get(1)?.trim().orEmpty()
        assertTrue("Expected non-empty string for $name", value.isNotBlank())
    }
}
