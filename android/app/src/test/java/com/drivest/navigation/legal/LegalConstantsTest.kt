package com.drivest.navigation.legal

import org.junit.Assert.assertTrue
import org.junit.Test

class LegalConstantsTest {

    @Test
    fun urlsUseDrivestDomain() {
        assertTrue(LegalConstants.TERMS_URL.startsWith("https://www.drivest.uk/"))
        assertTrue(LegalConstants.PRIVACY_URL.startsWith("https://www.drivest.uk/"))
        assertTrue(LegalConstants.FAQ_URL.startsWith("https://www.drivest.uk/"))
    }

    @Test
    fun supportEmailLooksValid() {
        assertTrue(LegalConstants.SUPPORT_EMAIL.contains("@"))
    }

    @Test
    fun legalVersionsAreNonEmpty() {
        assertTrue(LegalConstants.TERMS_VERSION.isNotBlank())
        assertTrue(LegalConstants.PRIVACY_VERSION.isNotBlank())
    }
}
