package com.fitzy.apexshield.core;

/** Runs inside the Shizuku helper. Only touches other apps' cache folders. */
interface IShellFiles {
    void destroy() = 16777114;

    /** Package names that have a cache folder on shared storage. */
    String[] cachePackages() = 1;

    /** "size<TAB>path" lines for files inside one app's cache folder (capped). */
    String[] cacheFiles(String pkg) = 2;

    /** Deletes files; the helper refuses anything that is not inside Android/data/<app>/cache. */
    boolean[] deleteCacheFiles(in String[] paths) = 3;
}
