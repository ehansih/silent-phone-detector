package com.ehansih.silentphonedetector.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "permission_events")
data class PermissionEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val permissionType: String,   // "Microphone", "Camera", "Location", "Contacts", "SMS"
    val detectedAtMs: Long,
    val isBackground: Boolean,
    val riskScore: Int            // 0-100 contribution of this event
)

@Dao
interface PermissionEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<PermissionEvent>)

    @Query("SELECT * FROM permission_events ORDER BY detectedAtMs DESC LIMIT 50")
    suspend fun getRecent(): List<PermissionEvent>

    @Query("SELECT COUNT(*) FROM permission_events WHERE permissionType = 'Microphone'")
    suspend fun micCount(): Int

    @Query("SELECT COUNT(*) FROM permission_events WHERE permissionType = 'Location'")
    suspend fun locationCount(): Int

    @Query("SELECT COUNT(*) FROM permission_events WHERE isBackground = 1")
    suspend fun backgroundCount(): Int

    @Query("DELETE FROM permission_events")
    suspend fun clear()
}

@Database(entities = [PermissionEvent::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun permissionEventDao(): PermissionEventDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "silent_phone_detector.db"
                ).build().also { INSTANCE = it }
            }
    }
}
