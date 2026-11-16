package com.fitzy.apexcleaner.core

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PathGuardTest {
    @Test fun neverTouchesAppData() {
        assertFalse(PathGuard.mayDeleteInOwnCache(File("/data/data/x/shared_prefs/a.xml")))
        assertFalse(PathGuard.mayDeleteInOwnCache(File("/data/data/x/databases/main.db")))
        assertFalse(PathGuard.mayDeleteInOwnCache(File("/data/data/x/cache/auth_token.tmp")))
        assertFalse(PathGuard.mayDeleteInOwnCache(File("/data/data/x/cache/login.json")))
    }

    @Test fun allowsPlainCacheFiles() {
        assertTrue(PathGuard.mayDeleteInOwnCache(File("/data/data/x/cache/img_123.tmp")))
    }

    @Test fun sharedStorageOnlyDisposable() {
        assertTrue(PathGuard.mayDeleteOnSharedStorage(File("/sdcard/Download/old_installer.apk")))
        assertTrue(PathGuard.mayDeleteOnSharedStorage(File("/sdcard/DCIM/.thumbnails/12345")))
        assertTrue(PathGuard.mayDeleteOnSharedStorage(File("/sdcard/app/debug.log")))
        assertFalse(PathGuard.mayDeleteOnSharedStorage(File("/sdcard/DCIM/photo.jpg")))
        assertFalse(PathGuard.mayDeleteOnSharedStorage(File("/sdcard/Android/data/com.a/cache/x.tmp")))
        assertFalse(PathGuard.mayDeleteOnSharedStorage(File("/sdcard/Documents/notes.pdf")))
        assertFalse(PathGuard.mayDeleteOnSharedStorage(File("/sdcard/Backups/export.db")))
    }

    @Test fun hiddenCacheFolders() {
        assertTrue(PathGuard.mayDeleteOnSharedStorage(File("/sdcard/SomeApp/cache/img.bin")))
        assertTrue(PathGuard.mayDeleteOnSharedStorage(File("/sdcard/.cache/x.dat")))
        assertFalse(PathGuard.mayDeleteOnSharedStorage(File("/sdcard/SomeApp/cache/data.db")))
        assertFalse(PathGuard.mayDeleteOnSharedStorage(File("/sdcard/SomeApp/files/img.bin")))
        assertFalse(PathGuard.mayDeleteOnSharedStorage(File("/sdcard/Documents/cache/a.txt")))
    }
}
