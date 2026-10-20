package de.dealcheck.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Insert
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

@Entity(tableName = "analyses")
data class AnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val imagePaths: List<String>,
    val title: String,
    val recommendation: String,
    val model: String,
    val note: String,
    /** Vom Modell gelieferte Analyse als JSON. */
    val resultJson: String,
    // Eigene Gewinnrechnung (null = noch nicht bearbeitet, Werte aus der Analyse)
    val calcPurchase: Double? = null,
    val calcShipping: Double? = null,
    val calcParts: Double? = null,
    val calcSale: Double? = null,
)

class Converters {
    @TypeConverter
    fun fromList(list: List<String>): String = JSONArray(list).toString()

    @TypeConverter
    fun toList(json: String): List<String> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (_: Exception) {
        emptyList()
    }
}

@Dao
interface AnalysisDao {
    @Query("SELECT * FROM analyses ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AnalysisEntity>>

    @Query("SELECT * FROM analyses WHERE id = :id")
    fun observe(id: Long): Flow<AnalysisEntity?>

    @Query("SELECT * FROM analyses")
    suspend fun getAll(): List<AnalysisEntity>

    @Insert
    suspend fun insert(entity: AnalysisEntity): Long

    @Update
    suspend fun update(entity: AnalysisEntity)

    @Query("DELETE FROM analyses WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM analyses WHERE id = :id")
    suspend fun get(id: Long): AnalysisEntity?
}

@Database(entities = [AnalysisEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun analysisDao(): AnalysisDao
}
