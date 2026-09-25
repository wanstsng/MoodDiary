package com.nastya.diary.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nastya.diary.data.database.dao.CategoryDao
import com.nastya.diary.data.database.dao.EntryDao
import com.nastya.diary.data.database.dao.TagDao
import com.nastya.diary.data.database.entity.CategoryEntity
import com.nastya.diary.data.database.entity.EntryEntity
import com.nastya.diary.data.database.entity.EntryTagCrossRef
import com.nastya.diary.data.database.entity.TagEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * База данных приложения.
 *
 * Содержит четыре таблицы: три основные ([CategoryEntity], [EntryEntity],
 * [TagEntity]) и промежуточную [EntryTagCrossRef] для связи
 * «многие-ко-многим».
 */
@Database(
    entities = [
        CategoryEntity::class,
        EntryEntity::class,
        TagEntity::class,
        EntryTagCrossRef::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun entryDao(): EntryDao

    abstract fun categoryDao(): CategoryDao

    abstract fun tagDao(): TagDao

    companion object {

        private const val DATABASE_NAME = "diary.db"

        @Volatile
        private var instance: AppDatabase? = null

        /**
         * Возвращает единственный экземпляр базы данных.
         *
         * Двойная проверка с блокировкой нужна, потому что за базой могут
         * одновременно обратиться несколько потоков, а открывать один файл
         * базы дважды нельзя.
         */
        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                .addCallback(object : Callback() {

                    /** При первом создании базы заполняем её категориями по умолчанию. */
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                            DatabaseSeeder.seed(getInstance(context))
                        }
                    }

                    /**
                     * SQLite по умолчанию не проверяет внешние ключи, и включать
                     * их нужно для каждого соединения. Без этой строки каскадное
                     * удаление категорий вместе с записями просто не сработает.
                     */
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.execSQL("PRAGMA foreign_keys = ON")
                    }
                })
                .build()
    }
}
