package app.numbaseball.data.age

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.play.agesignals.AgeSignalsManagerFactory
import com.google.android.play.agesignals.AgeSignalsRequest
import com.google.android.play.agesignals.model.SignificantChangeStatus
import java.time.Year
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.suspendCancellableCoroutine

/** The declared band, on this device only: its own DataStore file, not part of Auto Backup. */
interface AgeStore {
    val band: Flow<AgeBand?>
    suspend fun save(band: AgeBand)
}

private val Context.ageDataStore by preferencesDataStore(name = "age")

class DataStoreAgeStore(context: Context) : AgeStore {
    private val store = context.applicationContext.ageDataStore

    override val band: Flow<AgeBand?> = store.data.map { prefs ->
        prefs[BAND]?.let { name -> AgeBand.entries.firstOrNull { it.name == name } }
    }

    override suspend fun save(band: AgeBand) {
        store.edit { it[BAND] = band.name }
    }

    private companion object {
        val BAND = stringPreferencesKey("band")
    }
}

/** Play Age Signals (US state app-store age laws, policy review R21); null when unavailable or not applicable. */
fun interface AgeSignalsSource {
    suspend fun check(): AgeSignal?
}

class PlayAgeSignalsSource(context: Context) : AgeSignalsSource {
    private val appContext = context.applicationContext

    override suspend fun check(): AgeSignal? = try {
        val manager = AgeSignalsManagerFactory.create(appContext)
        suspendCancellableCoroutine { cont ->
            manager.checkAgeSignals(AgeSignalsRequest.builder().build())
                .addOnSuccessListener { result ->
                    val pending = result.significantChangeStatus() == SignificantChangeStatus.PENDING ||
                        result.significantChangeStatus() == SignificantChangeStatus.DECLINED
                    val signal = if (result.ageLower() == null && result.ageUpper() == null && !pending) {
                        null
                    } else {
                        AgeSignal(result.ageLower(), result.ageUpper(), pending)
                    }
                    cont.resume(signal)
                }
                // Not available (no Play Store, outside covered regions, old Play services...): ignore gracefully.
                .addOnFailureListener { cont.resume(null) }
        }
    } catch (e: RuntimeException) {
        null
    }
}

class AgeRepository(
    private val store: AgeStore,
    private val signals: AgeSignalsSource,
    private val minimumAge: Int,
    scope: CoroutineScope,
    private val currentYear: () -> Int = { Year.now().value },
) {
    private val signal = MutableStateFlow<AgeSignal?>(null)

    val state: StateFlow<AgeState> = combine(store.band, signal) { band, s ->
        if (band == null) AgeState.Unknown else AgeState.Known(band, s)
    }.stateIn(scope, SharingStarted.Eagerly, AgeState.Loading)

    /** Valid birth years (01 §0): 1900 to the current year. */
    fun validYears(): IntRange = 1900..currentYear()

    /** Stores the band once; an under-minimum answer is final on this device (no retry by re-answering). */
    suspend fun submitBirthYear(year: Int): AgeBand {
        val band = AgeRules.bandForBirthYear(year, currentYear(), minimumAge)
        store.save(band)
        return band
    }

    /** Called on app open; refines the band when Play reports one. */
    suspend fun refreshSignals() {
        signal.value = signals.check()
    }
}
