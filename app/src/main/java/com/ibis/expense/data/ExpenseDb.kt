package com.ibis.expense.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "expenses")
data class ExpenseRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountCents: Long,
    val epochDay: Long,
    val createdAt: Long,
    val category: String,
    val note: String,
    val excluded: Boolean = false
)

data class CategoryTotal(
    val category: String,
    val totalCents: Long
)

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE epochDay BETWEEN :fromDay AND :toDay ORDER BY epochDay DESC, createdAt DESC")
    fun observeRange(fromDay: Long, toDay: Long): Flow<List<ExpenseRecord>>

    @Query("SELECT category, SUM(amountCents) AS totalCents FROM expenses WHERE epochDay BETWEEN :fromDay AND :toDay AND excluded = 0 GROUP BY category ORDER BY totalCents DESC")
    fun observeCategoryTotals(fromDay: Long, toDay: Long): Flow<List<CategoryTotal>>

    @Query("SELECT * FROM expenses ORDER BY epochDay ASC, createdAt ASC")
    suspend fun getAllOnce(): List<ExpenseRecord>

    @Query("SELECT * FROM expenses ORDER BY epochDay ASC, createdAt ASC")
    fun observeAll(): Flow<List<ExpenseRecord>>

    @Insert
    suspend fun insert(record: ExpenseRecord)

    @Insert
    suspend fun insertAll(records: List<ExpenseRecord>)

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()

    @Update
    suspend fun update(record: ExpenseRecord)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: Long)
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE expenses ADD COLUMN excluded INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(entities = [ExpenseRecord::class], version = 2, exportSchema = false)
abstract class ExpenseDatabase : RoomDatabase() {
    abstract fun dao(): ExpenseDao

    companion object {
        fun build(context: Context): ExpenseDatabase =
            Room.databaseBuilder(context, ExpenseDatabase::class.java, "expenses.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
