package de.dealcheck.app.data

import android.net.Uri
import de.dealcheck.app.analysis.AnalysisParser
import de.dealcheck.app.analysis.DealCheckException
import de.dealcheck.app.analysis.OpenRouterClient
import kotlinx.coroutines.flow.Flow

class AnalysisRepository(
    private val dao: AnalysisDao,
    private val images: ImageStore,
    private val settings: SettingsRepository,
    private val client: OpenRouterClient,
) {
    fun observeAll(): Flow<List<AnalysisEntity>> = dao.observeAll()

    fun observe(id: Long): Flow<AnalysisEntity?> = dao.observe(id)

    /** Kopiert ein Bild (Kamera, Galerie, Teilen) skaliert in den App-Speicher. */
    suspend fun importImage(uri: Uri): String = try {
        images.importImage(uri).absolutePath
    } catch (e: Exception) {
        throw DealCheckException("Bild konnte nicht geladen werden: ${e.message ?: "unbekannter Fehler"}", e)
    } catch (e: OutOfMemoryError) {
        throw DealCheckException("Bild ist zu groß und konnte nicht geladen werden.")
    }

    /** Führt die Analyse aus, speichert sie im Verlauf und liefert die neue ID. */
    suspend fun analyze(imagePaths: List<String>, note: String): Long {
        val s = settings.settings.value
        val base64 = imagePaths.map { images.toBase64(it) }
        val result = client.analyze(s.apiKey, s.model, s.webSearch, base64, note)
        return dao.insert(
            AnalysisEntity(
                createdAt = System.currentTimeMillis(),
                imagePaths = imagePaths,
                title = result.analysis.title,
                recommendation = result.analysis.empfehlung.name,
                model = result.model,
                note = note,
                resultJson = AnalysisParser.toJson(result.analysis),
            ),
        )
    }

    suspend fun updateCalc(id: Long, purchase: Double?, shipping: Double?, parts: Double?, sale: Double?) {
        val e = dao.get(id) ?: return
        dao.update(e.copy(calcPurchase = purchase, calcShipping = shipping, calcParts = parts, calcSale = sale))
    }

    suspend fun delete(id: Long) {
        val e = dao.get(id) ?: return
        dao.delete(id)
        images.delete(e.imagePaths)
    }

    /** Bild aus einer noch nicht gestarteten Analyse entfernen. */
    fun discardImage(path: String) = images.delete(listOf(path))

    /** Entfernt alte Bilder, die zu keiner gespeicherten Analyse gehören. */
    suspend fun cleanupImages() {
        images.deleteOrphans(dao.getAll().flatMap { it.imagePaths }.toSet())
    }
}
