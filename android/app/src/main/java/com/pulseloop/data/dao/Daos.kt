package com.pulseloop.data.dao

import androidx.room.*
import com.pulseloop.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY updatedAt DESC LIMIT 1")
    suspend fun current(): DeviceEntity?

    @Query("SELECT * FROM devices ORDER BY updatedAt DESC LIMIT 1")
    fun currentFlow(): Flow<DeviceEntity?>

    @Upsert
    suspend fun upsert(device: DeviceEntity)

    @Query("DELETE FROM devices")
    suspend fun clear()
}

@Dao
interface MeasurementDao {
    @Query("SELECT * FROM measurements WHERE kindRaw = :kind AND timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    suspend fun range(kind: String, start: Long, end: Long): List<MeasurementEntity>

    @Query("SELECT * FROM measurements WHERE kindRaw = :kind AND timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    fun rangeFlow(kind: String, start: Long, end: Long): Flow<List<MeasurementEntity>>

    @Query("SELECT value FROM measurements WHERE kindRaw = :kind AND timestamp <= :before ORDER BY timestamp DESC LIMIT 1")
    suspend fun latest(kind: String, before: Long = System.currentTimeMillis()): Double?

    @Insert
    suspend fun insert(measurement: MeasurementEntity)
}

@Dao
interface ActivityDailyDao {
    @Query("SELECT * FROM activity_daily WHERE date = :day LIMIT 1")
    suspend fun byDay(day: Long): ActivityDailyEntity?

    @Query("SELECT * FROM activity_daily WHERE date = :day LIMIT 1")
    fun byDayFlow(day: Long): Flow<ActivityDailyEntity?>

    @Query("SELECT * FROM activity_daily ORDER BY date DESC LIMIT :limit")
    suspend fun recent(limit: Int = 7): List<ActivityDailyEntity>

    @Query("SELECT * FROM activity_daily ORDER BY date DESC LIMIT :limit")
    fun recentFlow(limit: Int = 7): Flow<List<ActivityDailyEntity>>

    @Upsert
    suspend fun upsert(entry: ActivityDailyEntity)
}

@Dao
interface ActivitySessionDao {
    @Query("SELECT * FROM activity_sessions WHERE statusRaw = 'recording' OR statusRaw = 'paused' LIMIT 1")
    suspend fun active(): ActivitySessionEntity?

    @Query("SELECT * FROM activity_sessions ORDER BY startedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int = 10): List<ActivitySessionEntity>

    @Query("SELECT * FROM activity_sessions ORDER BY startedAt DESC LIMIT :limit")
    fun recentFlow(limit: Int = 10): Flow<List<ActivitySessionEntity>>

    @Upsert
    suspend fun upsert(session: ActivitySessionEntity)
}

@Dao
interface ActivityGpsPointDao {
    @Query("SELECT * FROM activity_gps_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun forSession(sessionId: String): List<ActivityGpsPointEntity>

    @Insert
    suspend fun insert(point: ActivityGpsPointEntity)
}

@Dao
interface SleepSessionDao {
    @Query("SELECT * FROM sleep_sessions WHERE date = :day LIMIT 1")
    suspend fun byDay(day: Long): SleepSessionEntity?

    @Query("SELECT * FROM sleep_sessions ORDER BY date DESC LIMIT :limit")
    suspend fun recent(limit: Int = 7): List<SleepSessionEntity>

    @Query("SELECT * FROM sleep_sessions ORDER BY date DESC LIMIT :limit")
    fun recentFlow(limit: Int = 7): Flow<List<SleepSessionEntity>>

    @Upsert
    suspend fun upsert(session: SleepSessionEntity)
}

@Dao
interface SleepStageBlockDao {
    @Query("SELECT * FROM sleep_stage_blocks WHERE sessionId = :sessionId ORDER BY startAt ASC")
    suspend fun forSession(sessionId: String): List<SleepStageBlockEntity>

    @Insert
    suspend fun insert(block: SleepStageBlockEntity)

    @Query("SELECT * FROM sleep_stage_blocks WHERE sessionId = :sessionId AND startAt = :startAt LIMIT 1")
    suspend fun findBlock(sessionId: String, startAt: Long): SleepStageBlockEntity?
}

@Dao
interface CoachConversationDao {
    @Query("SELECT * FROM coach_conversations ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int = 10): List<CoachConversationEntity>

    @Query("SELECT * FROM coach_conversations ORDER BY updatedAt DESC LIMIT :limit")
    fun recentFlow(limit: Int = 10): Flow<List<CoachConversationEntity>>

    @Upsert
    suspend fun upsert(conversation: CoachConversationEntity)
}

@Dao
interface CoachMessageDao {
    @Query("SELECT * FROM coach_messages WHERE conversationId = :convId ORDER BY createdAt ASC")
    suspend fun forConversation(convId: String): List<CoachMessageEntity>

    @Query("SELECT * FROM coach_messages WHERE conversationId = :convId ORDER BY createdAt ASC")
    fun forConversationFlow(convId: String): Flow<List<CoachMessageEntity>>

    @Insert
    suspend fun insert(message: CoachMessageEntity)
}

@Dao
interface CoachMemoryDao {
    @Query("SELECT * FROM coach_memories WHERE key = :key LIMIT 1")
    suspend fun byKey(key: String): CoachMemoryEntity?

    @Query("SELECT * FROM coach_memories ORDER BY importance DESC")
    suspend fun allRanked(): List<CoachMemoryEntity>

    @Upsert
    suspend fun upsert(memory: CoachMemoryEntity)

    @Query("DELETE FROM coach_memories WHERE expiresAt IS NOT NULL AND expiresAt < :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis())

    @Query("DELETE FROM coach_memories WHERE key = :key")
    suspend fun deleteByKey(key: String)
}

@Dao
interface CoachToolCallDao {
    @Query("SELECT * FROM coach_tool_calls WHERE conversationId = :convId ORDER BY createdAt ASC")
    suspend fun forConversation(convId: String): List<CoachToolCallEntity>

    @Insert
    suspend fun insert(call: CoachToolCallEntity)
}

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles LIMIT 1")
    suspend fun get(): UserProfileEntity?

    @Upsert
    suspend fun upsert(profile: UserProfileEntity)
}

@Dao
interface UserGoalDao {
    @Query("SELECT * FROM user_goals LIMIT 1")
    suspend fun get(): UserGoalEntity?

    @Upsert
    suspend fun upsert(goal: UserGoalEntity)
}
