package com.ibis.expense.data

import android.content.Context
import java.io.File
import java.time.LocalDate

object BackupManager {
    private const val PREFS = "settings"
    private const val KEY_LAST_BACKUP_DAY = "last_backup_day"
    private const val KEEP = 7

    fun backupIfNeeded(context: Context, db: ExpenseDatabase) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toEpochDay()
        if (prefs.getLong(KEY_LAST_BACKUP_DAY, -1L) == today) return
        try {
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            val dir = File(context.filesDir, "backups").apply { mkdirs() }
            context.getDatabasePath("expenses.db").copyTo(File(dir, "expenses-$today.db"), overwrite = true)
            dir.listFiles()
                ?.filter { it.name.startsWith("expenses-") }
                ?.sortedByDescending { it.name }
                ?.drop(KEEP)
                ?.forEach { it.delete() }
            prefs.edit().putLong(KEY_LAST_BACKUP_DAY, today).apply()
        } catch (_: Exception) {
        }
    }

    fun latestBackupDate(context: Context): LocalDate? {
        val dir = File(context.filesDir, "backups")
        return dir.listFiles()
            ?.filter { it.name.startsWith("expenses-") }
            ?.maxByOrNull { it.name }
            ?.let { it.name.removePrefix("expenses-").removeSuffix(".db").toLongOrNull() }
            ?.let(LocalDate::ofEpochDay)
    }
}
