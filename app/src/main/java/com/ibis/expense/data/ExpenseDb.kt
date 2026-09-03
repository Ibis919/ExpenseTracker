package com.ibis.expense.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "expenses")
data class ExpenseRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountCents: Long,
    val epochDay: Long,
    val createdAt: Long,
    val category: String,
    val note: String
)

data class CategoryTotal(
    val category: String,
    val totalCents: Long
)

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE epochDay BETWEEN :fromDay AND :toDay ORDER BY epochDay DESC, createdAt DESC")
    fun observeRange(fromDay: Long, toDay: Long): Flow<List<ExpenseRecord>>

    @Query("SELECT category, SUM(amountCents) AS totalCents FROM expenses WHERE epochDay BETWEEN :fromDay AND :toDay GROUP BY category ORDER BY totalCents DESC")
    fun observeCategoryTotals(fromDay: Long, toDay: Long): Flow<List<CategoryTotal>>

    @Query("SELECT * FROM expenses ORDER BY epochDay ASC, createdAt ASC")
    suspend fun getAllOnce(): List<ExpenseRecord>

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

@Database(entities = [ExpenseRecord::class], version = 1, exportSchema = false)
abstract class ExpenseDatabase : RoomDatabase() {
    abstract fun dao(): ExpenseDao
}
