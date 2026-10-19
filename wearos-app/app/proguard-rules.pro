# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep ANCS model classes
-keep class app.tandemwear.watch.model.** { *; }
-keep class app.tandemwear.watch.data.** { *; }
