# Media3 and Android framework discover the player through regular typed references.
# Keep model fields used by org.json parsing and the main Android entry points.
-keep class com.maximus.player.nativeapp.model.** { *; }
-keep class com.maximus.player.nativeapp.player.** { *; }
-keep class com.maximus.player.nativeapp.MainActivity { *; }
-keep class com.maximus.player.nativeapp.SeriesActivity { *; }
-dontwarn androidx.media3.**
