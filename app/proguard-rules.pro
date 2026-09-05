# GeckoView reaches into its own classes from native code and from JS bridges.
-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.** { *; }

# kmp-tor loads its service + resource classes reflectively.
-keep class io.matthewnelson.kmp.tor.** { *; }

# Room generated implementations.
-keep class io.effect.browser.data.db.** { *; }

# kmp-process (a kmp-tor dependency) probes for these JVM-only management classes behind a
# reflective "java10OrNull" guard to get its own PID on desktop JVMs. They don't exist on
# Android and the guard means they're never actually reached there, but R8 still needs to be
# told it's fine that they're missing from the classpath.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
