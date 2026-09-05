# GeckoView reaches into its own classes from native code and from JS bridges.
-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.** { *; }

# kmp-tor loads its service + resource classes reflectively.
-keep class io.matthewnelson.kmp.tor.** { *; }

# Room generated implementations.
-keep class io.effect.browser.data.db.** { *; }
