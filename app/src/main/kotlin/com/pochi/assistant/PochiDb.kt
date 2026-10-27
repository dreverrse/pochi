package com.pochi.assistant

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DbChatMessage(
    val id: String,
    val sender: String, // "user" or "pochi"
    val text: String,
    val timestamp: Long,
    val isStarred: Boolean = false
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}

data class FocusLogEntry(
    val id: String,
    val mode: String,
    val durationMins: Int,
    val completedAt: Long,
    val taskTitle: String = ""
) {
    val formattedDate: String
        get() = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(completedAt))
}

data class PochiMemoryEntry(
    val id: String,
    val category: String,
    val fact: String,
    val updatedAt: Long,
    val deviceId: String = "phone"
) {
    val formattedDate: String
        get() = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(updatedAt))
}

class PochiDb private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    companion object {
        const val DATABASE_NAME = "pochi_local.db"
        const val DATABASE_VERSION = 1

        @Volatile
        private var INSTANCE: PochiDb? = null

        fun getInstance(context: Context): PochiDb {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PochiDb(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tasks (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                time_label TEXT,
                hour24 INTEGER,
                minute INTEGER,
                day_index INTEGER,
                is_done INTEGER,
                is_overdue INTEGER,
                is_high_priority INTEGER,
                notes TEXT,
                subtasks_json TEXT,
                created_at INTEGER
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS deck_items (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                category TEXT NOT NULL,
                item_count INTEGER,
                updated_ago_en TEXT,
                updated_ago_id TEXT,
                icon_key TEXT,
                description TEXT,
                created_at INTEGER
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS expenses (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                amount_rp INTEGER,
                category TEXT,
                time_label TEXT,
                created_at INTEGER
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chat_messages (
                id TEXT PRIMARY KEY,
                sender TEXT NOT NULL,
                text TEXT NOT NULL,
                timestamp INTEGER,
                is_starred INTEGER DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS focus_logs (
                id TEXT PRIMARY KEY,
                mode TEXT NOT NULL,
                duration_mins INTEGER,
                completed_at INTEGER,
                task_title TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS memories (
                id TEXT PRIMARY KEY,
                category TEXT NOT NULL,
                fact TEXT NOT NULL,
                created_at INTEGER,
                updated_at INTEGER,
                device_id TEXT
            )
            """.trimIndent()
        )

        seedInitialData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migrasi defensif: pastikan kolom yang dikenal ada (tidak merusak data).
        if (newVersion > oldVersion) {
            ensureColumn(db, "tasks", "is_high_priority", "INTEGER")
            ensureColumn(db, "tasks", "subtasks_json", "TEXT")
            ensureColumn(db, "chat_messages", "is_starred", "INTEGER DEFAULT 0")
            ensureColumn(db, "focus_logs", "task_title", "TEXT")
            ensureColumn(db, "memories", "device_id", "TEXT")
            ensureColumn(db, "memories", "updated_at", "INTEGER")
        }
    }

    private fun ensureColumn(db: SQLiteDatabase, table: String, column: String, type: String) {
        val exists = db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            var found = false
            while (c.moveToNext()) {
                if (c.getString(1) == column) {
                    found = true
                    break
                }
            }
            found
        }
        if (!exists) {
            runCatching { db.execSQL("ALTER TABLE $table ADD COLUMN $column $type") }
        }
    }

    private fun seedInitialData(db: SQLiteDatabase) {
        // Seed initial greeting message
        val now = System.currentTimeMillis()
        val cvMsg = ContentValues().apply {
            put("id", "msg_welcome")
            put("sender", "pochi")
            put("text", "Halo! Aku Pochi, asisten cerdas pribadimu. Ada yang bisa kubantu hari ini?")
            put("timestamp", now)
            put("is_starred", 1)
        }
        db.insertWithOnConflict("chat_messages", null, cvMsg, SQLiteDatabase.CONFLICT_REPLACE)

        // Seed initial focus log
        val cvFocus = ContentValues().apply {
            put("id", "focus_init")
            put("mode", "Focus")
            put("duration_mins", 25)
            put("completed_at", now - 3600000L)
            put("task_title", "Setup Pochi")
        }
        db.insertWithOnConflict("focus_logs", null, cvFocus, SQLiteDatabase.CONFLICT_REPLACE)

        // Seed initial memory: Tuan Andre
        val cvMem1 = ContentValues().apply {
            put("id", "mem_init_1")
            put("category", "Profil")
            put("fact", "Pengguna adalah Tuan Andre. Pochi selalu menyapa dan memanggilnya dengan hormat 'Tuan Andre'.")
            put("created_at", now)
            put("updated_at", now)
            put("device_id", "phone")
        }
        db.insertWithOnConflict("memories", null, cvMem1, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ========================================================================
    // TASKS CRUD
    // ========================================================================
    @Synchronized
    fun getAllTasks(): List<TodayTask> {
        val tasks = mutableListOf<TodayTask>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM tasks ORDER BY created_at DESC", null)
        cursor.use { c ->
            val idxId = c.getColumnIndex("id")
            val idxTitle = c.getColumnIndex("title")
            val idxTime = c.getColumnIndex("time_label")
            val idxHour = c.getColumnIndex("hour24")
            val idxMin = c.getColumnIndex("minute")
            val idxDay = c.getColumnIndex("day_index")
            val idxDone = c.getColumnIndex("is_done")
            val idxOverdue = c.getColumnIndex("is_overdue")
            val idxPrio = c.getColumnIndex("is_high_priority")
            val idxNotes = c.getColumnIndex("notes")
            val idxSub = c.getColumnIndex("subtasks_json")

            while (c.moveToNext()) {
                val subtasksJson = if (idxSub >= 0) c.getString(idxSub) else null
                val subList = parseSubtasks(subtasksJson)
                tasks.add(
                    TodayTask(
                        id = if (idxId >= 0) c.getString(idxId) else "task_${System.currentTimeMillis()}",
                        title = if (idxTitle >= 0) c.getString(idxTitle) else "",
                        timeLabel = if (idxTime >= 0) c.getString(idxTime) ?: "" else "",
                        hour24 = if (idxHour >= 0) c.getInt(idxHour) else -1,
                        minute = if (idxMin >= 0) c.getInt(idxMin) else -1,
                        dayIndex = if (idxDay >= 0) c.getInt(idxDay) else 0,
                        isDone = if (idxDone >= 0) c.getInt(idxDone) == 1 else false,
                        isOverdue = if (idxOverdue >= 0) c.getInt(idxOverdue) == 1 else false,
                        isHighPriority = if (idxPrio >= 0) c.getInt(idxPrio) == 1 else false,
                        notes = if (idxNotes >= 0) c.getString(idxNotes) ?: "" else "",
                        subtasks = subList
                    )
                )
            }
        }
        return tasks
    }

    @Synchronized
    fun saveTask(task: TodayTask) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("id", task.id)
            put("title", task.title)
            put("time_label", task.timeLabel)
            put("hour24", task.hour24)
            put("minute", task.minute)
            put("day_index", task.dayIndex)
            put("is_done", if (task.isDone) 1 else 0)
            put("is_overdue", if (task.isOverdue) 1 else 0)
            put("is_high_priority", if (task.isHighPriority) 1 else 0)
            put("notes", task.notes)
            put("subtasks_json", encodeSubtasks(task.subtasks))
            put("created_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("tasks", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun toggleTask(taskId: String): Boolean {
        val db = writableDatabase
        val cursor = db.rawQuery("SELECT is_done FROM tasks WHERE id = ?", arrayOf(taskId))
        var newStatus = false
        cursor.use { c ->
            if (c.moveToFirst()) {
                val current = c.getInt(0) == 1
                newStatus = !current
                val cv = ContentValues().apply {
                    put("is_done", if (newStatus) 1 else 0)
                }
                db.update("tasks", cv, "id = ?", arrayOf(taskId))
            }
        }
        return newStatus
    }

    @Synchronized
    fun deleteTask(taskId: String) {
        val db = writableDatabase
        db.delete("tasks", "id = ?", arrayOf(taskId))
    }

    // ========================================================================
    // DECK ITEMS CRUD
    // ========================================================================
    @Synchronized
    fun getAllDeckItems(): List<DeckCollectionItem> {
        val items = mutableListOf<DeckCollectionItem>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM deck_items ORDER BY created_at DESC", null)
        cursor.use { c ->
            val idxId = c.getColumnIndex("id")
            val idxTitle = c.getColumnIndex("title")
            val idxCategory = c.getColumnIndex("category")
            val idxCount = c.getColumnIndex("item_count")
            val idxEn = c.getColumnIndex("updated_ago_en")
            val idxIdn = c.getColumnIndex("updated_ago_id")
            val idxIcon = c.getColumnIndex("icon_key")
            val idxDesc = c.getColumnIndex("description")

            while (c.moveToNext()) {
                items.add(
                    DeckCollectionItem(
                        id = if (idxId >= 0) c.getString(idxId) else "deck_${System.currentTimeMillis()}",
                        title = if (idxTitle >= 0) c.getString(idxTitle) else "Collection",
                        category = if (idxCategory >= 0) c.getString(idxCategory) ?: "Notes" else "Notes",
                        itemCount = if (idxCount >= 0) c.getInt(idxCount) else 0,
                        updatedAgoEn = if (idxEn >= 0) c.getString(idxEn) ?: "Just now" else "Just now",
                        updatedAgoId = if (idxIdn >= 0) c.getString(idxIdn) ?: "Baru saja" else "Baru saja",
                        iconKey = if (idxIcon >= 0) c.getString(idxIcon) ?: "ui" else "ui",
                        description = if (idxDesc >= 0) c.getString(idxDesc) ?: "" else ""
                    )
                )
            }
        }
        return items
    }

    @Synchronized
    fun saveDeckItem(item: DeckCollectionItem) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("id", item.id)
            put("title", item.title)
            put("category", item.category)
            put("item_count", item.itemCount)
            put("updated_ago_en", item.updatedAgoEn)
            put("updated_ago_id", item.updatedAgoId)
            put("icon_key", item.iconKey)
            put("description", item.description)
            put("created_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("deck_items", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun deleteDeckItem(id: String) {
        val db = writableDatabase
        db.delete("deck_items", "id = ?", arrayOf(id))
    }

    // ========================================================================
    // EXPENSES CRUD
    // ========================================================================
    @Synchronized
    fun getAllExpenses(): List<ExpenseEntry> {
        val expenses = mutableListOf<ExpenseEntry>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM expenses ORDER BY created_at DESC", null)
        cursor.use { c ->
            val idxId = c.getColumnIndex("id")
            val idxTitle = c.getColumnIndex("title")
            val idxAmount = c.getColumnIndex("amount_rp")
            val idxCat = c.getColumnIndex("category")
            val idxTime = c.getColumnIndex("time_label")

            while (c.moveToNext()) {
                expenses.add(
                    ExpenseEntry(
                        id = if (idxId >= 0) c.getString(idxId) else "exp_${System.currentTimeMillis()}",
                        title = if (idxTitle >= 0) c.getString(idxTitle) else "Pengeluaran",
                        amountRp = if (idxAmount >= 0) c.getLong(idxAmount) else 0L,
                        category = if (idxCat >= 0) c.getString(idxCat) ?: "Lainnya" else "Lainnya",
                        timeLabel = if (idxTime >= 0) c.getString(idxTime) ?: "12:00" else "12:00"
                    )
                )
            }
        }
        return expenses
    }

    @Synchronized
    fun saveExpense(entry: ExpenseEntry) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("id", entry.id)
            put("title", entry.title)
            put("amount_rp", entry.amountRp)
            put("category", entry.category)
            put("time_label", entry.timeLabel)
            put("created_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("expenses", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun deleteExpense(id: String) {
        val db = writableDatabase
        db.delete("expenses", "id = ?", arrayOf(id))
    }

    // ========================================================================
    // CHAT MESSAGES CRUD
    // ========================================================================
    @Synchronized
    fun getChatMessages(limit: Int = 100): List<DbChatMessage> {
        val list = mutableListOf<DbChatMessage>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM chat_messages ORDER BY timestamp ASC LIMIT ?",
            arrayOf(limit.toString())
        )
        cursor.use { c ->
            val idxId = c.getColumnIndex("id")
            val idxSender = c.getColumnIndex("sender")
            val idxText = c.getColumnIndex("text")
            val idxTs = c.getColumnIndex("timestamp")
            val idxStar = c.getColumnIndex("is_starred")

            while (c.moveToNext()) {
                list.add(
                    DbChatMessage(
                        id = if (idxId >= 0) c.getString(idxId) else "msg_${System.currentTimeMillis()}",
                        sender = if (idxSender >= 0) c.getString(idxSender) else "pochi",
                        text = if (idxText >= 0) c.getString(idxText) else "",
                        timestamp = if (idxTs >= 0) c.getLong(idxTs) else System.currentTimeMillis(),
                        isStarred = if (idxStar >= 0) c.getInt(idxStar) == 1 else false
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    fun insertChatMessage(sender: String, text: String): DbChatMessage {
        val msg = DbChatMessage(
            id = "msg_${System.currentTimeMillis()}_${(100..999).random()}",
            sender = sender,
            text = text.trim(),
            timestamp = System.currentTimeMillis()
        )
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("id", msg.id)
            put("sender", msg.sender)
            put("text", msg.text)
            put("timestamp", msg.timestamp)
            put("is_starred", 0)
        }
        db.insertWithOnConflict("chat_messages", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        return msg
    }

    @Synchronized
    fun deleteChatMessage(id: String) {
        val db = writableDatabase
        db.delete("chat_messages", "id = ?", arrayOf(id))
    }

    @Synchronized
    fun clearChatMessages() {
        val db = writableDatabase
        db.delete("chat_messages", null, null)
    }

    // ========================================================================
    // FOCUS LOGS CRUD
    // ========================================================================
    @Synchronized
    fun insertFocusLog(mode: String, durationMins: Int, taskTitle: String = "") {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("id", "focus_${System.currentTimeMillis()}")
            put("mode", mode)
            put("duration_mins", durationMins)
            put("completed_at", System.currentTimeMillis())
            put("task_title", taskTitle)
        }
        db.insert("focus_logs", null, cv)
    }

    @Synchronized
    fun getFocusLogs(limit: Int = 20): List<FocusLogEntry> {
        val list = mutableListOf<FocusLogEntry>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM focus_logs ORDER BY completed_at DESC LIMIT ?",
            arrayOf(limit.toString())
        )
        cursor.use { c ->
            val idxId = c.getColumnIndex("id")
            val idxMode = c.getColumnIndex("mode")
            val idxDur = c.getColumnIndex("duration_mins")
            val idxTime = c.getColumnIndex("completed_at")
            val idxTask = c.getColumnIndex("task_title")

            while (c.moveToNext()) {
                list.add(
                    FocusLogEntry(
                        id = if (idxId >= 0) c.getString(idxId) else "fl_${System.currentTimeMillis()}",
                        mode = if (idxMode >= 0) c.getString(idxMode) else "Focus",
                        durationMins = if (idxDur >= 0) c.getInt(idxDur) else 25,
                        completedAt = if (idxTime >= 0) c.getLong(idxTime) else System.currentTimeMillis(),
                        taskTitle = if (idxTask >= 0) c.getString(idxTask) ?: "" else ""
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    fun getTotalFocusMinutesToday(): Int {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT SUM(duration_mins) FROM focus_logs WHERE completed_at >= ?",
            arrayOf(startOfDay.toString())
        )
        var total = 0
        cursor.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) {
                total = c.getInt(0)
            }
        }
        return total
    }

    @Synchronized
    fun getAllMemories(): List<PochiMemoryEntry> {
        val list = mutableListOf<PochiMemoryEntry>()
        val db = readableDatabase
        val cursor = runCatching {
            db.rawQuery("SELECT * FROM memories ORDER BY updated_at DESC", null)
        }.getOrNull() ?: return emptyList()

        cursor.use { c ->
            val idxId = c.getColumnIndex("id")
            val idxCat = c.getColumnIndex("category")
            val idxFact = c.getColumnIndex("fact")
            val idxUp = c.getColumnIndex("updated_at")
            val idxDev = c.getColumnIndex("device_id")

            while (c.moveToNext()) {
                list.add(
                    PochiMemoryEntry(
                        id = if (idxId >= 0) c.getString(idxId) else "mem_${System.currentTimeMillis()}",
                        category = if (idxCat >= 0) c.getString(idxCat) else "Fakta",
                        fact = if (idxFact >= 0) c.getString(idxFact) else "",
                        updatedAt = if (idxUp >= 0) c.getLong(idxUp) else System.currentTimeMillis(),
                        deviceId = if (idxDev >= 0) c.getString(idxDev) ?: "phone" else "phone"
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    fun saveMemory(category: String, fact: String, deviceId: String = "phone"): PochiMemoryEntry {
        val now = System.currentTimeMillis()
        val entry = PochiMemoryEntry(
            id = "mem_${now}_${(100..999).random()}",
            category = category.trim().ifBlank { "Fakta" },
            fact = fact.trim(),
            updatedAt = now,
            deviceId = deviceId
        )
        val db = writableDatabase
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS memories (
                id TEXT PRIMARY KEY,
                category TEXT NOT NULL,
                fact TEXT NOT NULL,
                created_at INTEGER,
                updated_at INTEGER,
                device_id TEXT
            )
            """.trimIndent()
        )
        val cv = ContentValues().apply {
            put("id", entry.id)
            put("category", entry.category)
            put("fact", entry.fact)
            put("created_at", now)
            put("updated_at", now)
            put("device_id", entry.deviceId)
        }
        db.insertWithOnConflict("memories", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        return entry
    }

    @Synchronized
    fun deleteMemory(id: String) {
        val db = writableDatabase
        db.delete("memories", "id = ?", arrayOf(id))
    }

    @Synchronized
    fun getMemoriesPrompt(): String {
        val memories = getAllMemories()
        if (memories.isEmpty()) return ""
        return memories.take(15).joinToString("\n") { "• [${it.category}] ${it.fact}" }
    }

    @Synchronized
    fun getDbSummary(): String {
        val db = readableDatabase
        val countTasks = getCount(db, "tasks")
        val countDecks = getCount(db, "deck_items")
        val countExpenses = getCount(db, "expenses")
        val countChats = getCount(db, "chat_messages")
        val countFocus = getCount(db, "focus_logs")
        val countMemories = runCatching { getCount(db, "memories") }.getOrDefault(0)
        return "SQLite: $countTasks tasks • $countDecks deck items • $countExpenses expenses • $countChats chats • $countFocus sessions • $countMemories memories"
    }

    private fun getCount(db: SQLiteDatabase, table: String): Int {
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $table", null)
        return cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    @Synchronized
    fun resetDatabase() {
        val db = writableDatabase
        db.delete("tasks", null, null)
        db.delete("deck_items", null, null)
        db.delete("expenses", null, null)
        db.delete("chat_messages", null, null)
        db.delete("focus_logs", null, null)
        runCatching { db.delete("memories", null, null) }
        seedInitialData(db)
    }

    @Synchronized
    fun exportBackupJson(): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exported_at", System.currentTimeMillis())

        val tasksArr = JSONArray()
        getAllTasks().forEach { t ->
            tasksArr.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("title", t.title)
                    put("timeLabel", t.timeLabel)
                    put("isDone", t.isDone)
                    put("notes", t.notes)
                }
            )
        }
        root.put("tasks", tasksArr)

        val expensesArr = JSONArray()
        getAllExpenses().forEach { e ->
            expensesArr.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("title", e.title)
                    put("amountRp", e.amountRp)
                    put("category", e.category)
                    put("timeLabel", e.timeLabel)
                }
            )
        }
        root.put("expenses", expensesArr)

        val chatArr = JSONArray()
        getChatMessages().forEach { m ->
            chatArr.put(
                JSONObject().apply {
                    put("id", m.id)
                    put("sender", m.sender)
                    put("text", m.text)
                    put("timestamp", m.timestamp)
                }
            )
        }
        root.put("chat_messages", chatArr)

        return root.toString(2)
    }

    // ========================================================================
    // HELPERS
    // ========================================================================
    private fun parseSubtasks(json: String?): List<TaskSubtask> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        TaskSubtask(
                            id = obj.optString("id", "st_$i"),
                            title = obj.optString("title", ""),
                            isDone = obj.optBoolean("isDone", false)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeSubtasks(subtasks: List<TaskSubtask>): String {
        val arr = JSONArray()
        subtasks.forEach { st ->
            arr.put(
                JSONObject().apply {
                    put("id", st.id)
                    put("title", st.title)
                    put("isDone", st.isDone)
                }
            )
        }
        return arr.toString()
    }
}

