# Room генерирует реализации DAO во время сборки — их имена трогать нельзя.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# MPAndroidChart обращается к своим классам через рефлексию.
-keep class com.github.mikephil.charting.** { *; }

# Имена перечислений сохраняются: настроение хранится в базе строкой,
# и переименование константы сделало бы старые записи нечитаемыми.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
