package com.abeswastaken.protontodo.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items")
    fun observeAll(): Flow<List<Item>>

    @Query("SELECT * FROM items")
    suspend fun all(): List<Item>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: Long): Item?

    @Insert
    suspend fun insert(item: Item): Long

    @Insert
    suspend fun insertAll(items: List<Item>)

    @Update
    suspend fun update(item: Item)

    @Delete
    suspend fun delete(item: Item)

    @Query("DELETE FROM items")
    suspend fun clear()
}

@Database(entities = [Item::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun items(): ItemDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "proton-todo.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** v1.1.0: linked to-dos, the important marker and category icons. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN parentId INTEGER")
                db.execSQL("ALTER TABLE items ADD COLUMN important INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE items ADD COLUMN category TEXT")
                db.execSQL("ALTER TABLE items ADD COLUMN pendingTodos TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
