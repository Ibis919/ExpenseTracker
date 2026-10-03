package com.ibis.expense.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@Entity(tableName = "expenses")
data class ExpenseRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountCents: Long,
    val epochDay: Long,
    val createdAt: Long,
    val category: String,
    val note: String,
    val excluded: Boolean = false,
    val deletedAt: Long = 0,
    val recurringId: Long = 0,
    val paymentMethod: String = "",
    val type: String = TransactionType.EXPENSE,
    val transferTo: String = "",
    val relatedRecordId: Long = 0
)

@Entity(tableName = "account_balances")
data class AccountBalance(
    @PrimaryKey val method: String,
    val baseCents: Long
)

@Entity(tableName = "recurring_expenses")
data class RecurringExpense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountCents: Long,
    val dayOfMonth: Int,
    val category: String,
    val note: String,
    val paymentMethod: String = "",
    val lastGeneratedMonth: String = ""
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val name: String,
    val emoji: String,
    val sortOrder: Int
)

@Entity(tableName = "templates")
data class RecordTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountCents: Long,
    val category: String,
    val note: String,
    val paymentMethod: String = ""
)

data class CategoryTotal(
    val category: String,
    val totalCents: Long
)

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE epochDay BETWEEN :fromDay AND :toDay AND deletedAt = 0 ORDER BY epochDay DESC, createdAt DESC")
    fun observeRange(fromDay: Long, toDay: Long): Flow<List<ExpenseRecord>>

    @Query("SELECT category, SUM(CASE WHEN type = 'refund' THEN -amountCents ELSE amountCents END) AS totalCents FROM expenses WHERE epochDay BETWEEN :fromDay AND :toDay AND type IN ('expense', 'refund') AND excluded = 0 AND deletedAt = 0 GROUP BY category ORDER BY totalCents DESC")
    fun observeCategoryTotals(fromDay: Long, toDay: Long): Flow<List<CategoryTotal>>

    @Query("SELECT * FROM expenses WHERE deletedAt = 0 ORDER BY epochDay ASC, createdAt ASC")
    suspend fun getAllOnce(): List<ExpenseRecord>

    @Query("SELECT * FROM expenses ORDER BY id ASC")
    suspend fun getAllStoredOnce(): List<ExpenseRecord>

    @Query("SELECT * FROM expenses WHERE deletedAt = 0 ORDER BY epochDay ASC, createdAt ASC")
    fun observeAll(): Flow<List<ExpenseRecord>>

    @Query("SELECT * FROM expenses WHERE deletedAt > 0 ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<ExpenseRecord>>

    @Insert
    suspend fun insert(record: ExpenseRecord): Long

    @Insert
    suspend fun insertAll(records: List<ExpenseRecord>): List<Long>

    @Update
    suspend fun updateRecordEntity(record: ExpenseRecord): Int

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()

    @Query("SELECT * FROM account_balances")
    fun observeAccountBalances(): Flow<List<AccountBalance>>

    @Query("SELECT * FROM account_balances ORDER BY method ASC")
    suspend fun getAccountBalancesOnce(): List<AccountBalance>

    @Query("DELETE FROM account_balances")
    suspend fun deleteAllAccountBalances()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccountBalance(balance: AccountBalance)

    @Query("DELETE FROM expenses WHERE id = :id OR relatedRecordId = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE expenses SET deletedAt = :ts WHERE (id = :id OR relatedRecordId = :id) AND deletedAt = 0")
    suspend fun softDelete(id: Long, ts: Long)

    @Query("SELECT IFNULL(MAX(deletedAt), 0) FROM expenses")
    suspend fun latestDeletionTimestamp(): Long

    @Query("UPDATE expenses SET deletedAt = 0 WHERE id = :id OR (relatedRecordId = :id AND deletedAt = :deletedAt)")
    suspend fun restore(id: Long, deletedAt: Long)

    @Query("DELETE FROM expenses WHERE (deletedAt > 0 AND deletedAt < :before) OR relatedRecordId IN (SELECT id FROM expenses WHERE deletedAt > 0 AND deletedAt < :before)")
    suspend fun purgeTrashOlderThan(before: Long)

    @Query("SELECT COUNT(*) FROM expenses WHERE recurringId = :recurringId AND epochDay BETWEEN :fromDay AND :toDay")
    suspend fun countRecurringInstance(recurringId: Long, fromDay: Long, toDay: Long): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE recurringId = :recurringId AND createdAt >= :fromMillis AND createdAt < :toMillis")
    suspend fun countRecurringCreated(recurringId: Long, fromMillis: Long, toMillis: Long): Int

    @Query("SELECT * FROM recurring_expenses ORDER BY dayOfMonth ASC")
    fun observeRecurring(): Flow<List<RecurringExpense>>

    @Query("SELECT * FROM recurring_expenses ORDER BY dayOfMonth ASC")
    suspend fun getAllRecurringOnce(): List<RecurringExpense>

    @Query("DELETE FROM recurring_expenses")
    suspend fun deleteAllRecurring()

    @Insert
    suspend fun insertRecurring(item: RecurringExpense): Long

    @Update
    suspend fun updateRecurring(item: RecurringExpense)

    @Query("DELETE FROM recurring_expenses WHERE id = :id")
    suspend fun deleteRecurringById(id: Long)

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    fun observeCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    suspend fun getAllCategoriesOnce(): List<Category>

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun countAllCategories(): Int

    @Query("SELECT COUNT(*) FROM categories WHERE name = :name")
    suspend fun countCategory(name: String): Int

    @Query("SELECT IFNULL(MAX(sortOrder), 0) FROM categories")
    suspend fun maxCategorySort(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: Category)

    @Query("UPDATE categories SET name = :newName, emoji = :emoji WHERE name = :oldName")
    suspend fun renameCategoryRow(oldName: String, newName: String, emoji: String)

    @Query("DELETE FROM categories WHERE name = :name")
    suspend fun deleteCategoryRow(name: String)

    @Query("UPDATE expenses SET category = :newName WHERE category = :oldName")
    suspend fun reassignExpensesCategory(oldName: String, newName: String)

    @Query("UPDATE recurring_expenses SET category = :newName WHERE category = :oldName")
    suspend fun reassignRecurringCategory(oldName: String, newName: String)

    @Query("UPDATE templates SET category = :newName WHERE category = :oldName")
    suspend fun reassignTemplatesCategory(oldName: String, newName: String)

    @Query("SELECT * FROM templates ORDER BY id DESC")
    fun observeTemplates(): Flow<List<RecordTemplate>>

    @Query("SELECT * FROM templates")
    suspend fun getAllTemplatesOnce(): List<RecordTemplate>

    @Query("DELETE FROM templates")
    suspend fun deleteAllTemplates()

    @Insert
    suspend fun insertTemplate(template: RecordTemplate)

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun deleteTemplateById(id: Long)
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE expenses ADD COLUMN excluded INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE expenses ADD COLUMN deletedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE expenses ADD COLUMN recurringId INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS recurring_expenses (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "amountCents INTEGER NOT NULL, " +
                "dayOfMonth INTEGER NOT NULL, " +
                "category TEXT NOT NULL, " +
                "note TEXT NOT NULL)"
        )
    }
}

private const val SEED_CATEGORIES =
    "INSERT OR IGNORE INTO categories (name, emoji, sortOrder) VALUES " +
        "('餐饮','🍜',1),('交通','🚗',2),('购物','🛍️',3),('日用','🧴',4)," +
        "('娱乐','🎮',5),('医疗','💊',6),('其他','📦',7)"

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS categories (" +
                "name TEXT NOT NULL PRIMARY KEY, " +
                "emoji TEXT NOT NULL, " +
                "sortOrder INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS templates (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "amountCents INTEGER NOT NULL, " +
                "category TEXT NOT NULL, " +
                "note TEXT NOT NULL)"
        )
        db.execSQL(SEED_CATEGORIES)
        db.execSQL(
            "INSERT OR IGNORE INTO categories (name, emoji, sortOrder) " +
                "SELECT DISTINCT category, '📦', 100 FROM expenses " +
                "WHERE category NOT IN ('餐饮','交通','购物','日用','娱乐','医疗','其他')"
        )
        db.execSQL(
            "INSERT OR IGNORE INTO categories (name, emoji, sortOrder) " +
                "SELECT DISTINCT category, '📦', 100 FROM recurring_expenses " +
                "WHERE category NOT IN ('餐饮','交通','购物','日用','娱乐','医疗','其他')"
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE expenses ADD COLUMN paymentMethod TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS account_balances (" +
                "method TEXT NOT NULL PRIMARY KEY, " +
                "baseCents INTEGER NOT NULL)"
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recurring_expenses ADD COLUMN paymentMethod TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE templates ADD COLUMN paymentMethod TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE expenses ADD COLUMN type TEXT NOT NULL DEFAULT 'expense'")
        db.execSQL("ALTER TABLE expenses ADD COLUMN transferTo TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE expenses ADD COLUMN relatedRecordId INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE recurring_expenses ADD COLUMN lastGeneratedMonth TEXT NOT NULL DEFAULT ''")
        db.query("SELECT recurringId, MAX(createdAt) FROM expenses WHERE recurringId > 0 GROUP BY recurringId").use { cursor ->
            while (cursor.moveToNext()) {
                val generatedMonth = YearMonth.from(Instant.ofEpochMilli(cursor.getLong(1)).atZone(ZoneId.systemDefault())).toString()
                db.execSQL("UPDATE recurring_expenses SET lastGeneratedMonth = ? WHERE id = ?", arrayOf(generatedMonth, cursor.getLong(0)))
            }
        }
    }
}

@Database(
    entities = [ExpenseRecord::class, RecurringExpense::class, Category::class, RecordTemplate::class, AccountBalance::class],
    version = 7,
    exportSchema = false
)
abstract class ExpenseDatabase : RoomDatabase() {
    abstract fun dao(): ExpenseDao

    companion object {
        fun build(context: Context): ExpenseDatabase =
            Room.databaseBuilder(context, ExpenseDatabase::class.java, "expenses.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(SEED_CATEGORIES)
                    }
                })
                .build()
    }
}
