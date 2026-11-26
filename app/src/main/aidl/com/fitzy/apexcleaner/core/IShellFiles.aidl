package com.fitzy.apexcleaner.core;

/** Runs inside the Shizuku helper. Only touches other apps' cache folders. */
interface IShellFiles {
    void destroy() = 16777114;

    /** Package names that have a cache folder on shared storage. */
    String[] cachePackages() = 1;

    /** "size<TAB>path" lines for files inside one app's cache folder (capped). */
    String[] cacheFiles(String pkg) = 2;

    /** Deletes files; the helper refuses anything that is not inside Android/data/<app>/cache. */
    boolean[] deleteCacheFiles(in String[] paths) = 3;

    /** Reads one of the three window-animation speeds; empty if the helper refuses the key. */
    String getAnimScale(String key) = 4;

    /** Sets one of the three window-animation speeds. Refuses every other setting. */
    boolean setAnimScale(String key, String value) = 5;
}
