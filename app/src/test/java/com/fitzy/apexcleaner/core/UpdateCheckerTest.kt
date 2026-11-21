package com.fitzy.apexcleaner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckerTest {
    private val json = """[
      {"tag_name":"v1.0.12","draft":false,"assets":[{"name":"ApexCleaner-v1.0.12.apk","browser_download_url":"https://x/12.apk"}]},
      {"tag_name":"v1.0.9","draft":false,"assets":[{"name":"ApexCleaner-v1.0.9.apk","browser_download_url":"https://x/9.apk"}]},
      {"tag_name":"v1.1.0","draft":false,"assets":[]},
      {"tag_name":"nightly","draft":false,"assets":[{"name":"a.apk","browser_download_url":"https://x/n.apk"}]}]"""

    @Test fun readsVersionFromTag() {
        assertEquals(42, UpdateChecker.codeFromTag("v1.0.42")); assertNull(UpdateChecker.codeFromTag("v1.0"))
    }

    @Test fun offersOnlyNewerBuildsWithAnApk() {
        assertEquals("https://x/12.apk", UpdateChecker.pick(json, 10)?.apkUrl)
        assertNull(UpdateChecker.pick(json, 12)); assertNull(UpdateChecker.pick("not json", 1))
    }
}
