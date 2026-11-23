package app.numbaseball.data.age

/** Age bands from the neutral first-launch age screen (policy review R19/R20). Only the band is stored, never the year. */
enum class AgeBand {
    /** Below the configured minimum age (default 14): the app stops. */
    UNDER_MINIMUM,

    /** Minimum age to 15: AdMob child treatment, under age of consent, no personalized ads. */
    CHILD,

    /** 16 and 17: AdMob teen treatment. */
    TEEN,
    ADULT,
}

/** How ads must treat this player; mapped to AdMob/UMP settings in the ads package. */
enum class AdAgeTreatment { CHILD, TEEN, UNSPECIFIED }

/** What Play Age Signals reported (null bounds = unknown). [approvalPending]: a supervised user's significant change awaits a parent. */
data class AgeSignal(val ageLower: Int?, val ageUpper: Int?, val approvalPending: Boolean = false)

sealed interface AgeState {
    data object Loading : AgeState

    /** First launch: ask the birth year before any ad or account. */
    data object Unknown : AgeState

    data class Known(val declared: AgeBand, val signal: AgeSignal? = null) : AgeState {
        /** The stricter of the declared band and Play Age Signals. */
        val band: AgeBand get() = AgeRules.stricter(declared, signal?.let { AgeRules.bandForSignal(it) })

        /** Play Age Signals below 13 refuses online features (R21) even if the declared band allowed them. */
        val onlineAllowed: Boolean get() = band != AgeBand.UNDER_MINIMUM && (signal?.ageUpper ?: Int.MAX_VALUE) >= 13

        val adTreatment: AdAgeTreatment get() = AgeRules.adTreatment(band)
    }
}

object AgeRules {
    /**
     * Year only, so the age is known to ±1 year. Design 01 §0: allowed when current year − birth year ≥ the minimum
     * (the player reaches that age at some point this calendar year).
     */
    fun ageThisYear(birthYear: Int, currentYear: Int): Int = currentYear - birthYear

    fun bandForAge(age: Int, minimumAge: Int): AgeBand = when {
        age < minimumAge -> AgeBand.UNDER_MINIMUM
        age < 16 -> AgeBand.CHILD
        age < 18 -> AgeBand.TEEN
        else -> AgeBand.ADULT
    }

    fun bandForBirthYear(birthYear: Int, currentYear: Int, minimumAge: Int): AgeBand =
        bandForAge(ageThisYear(birthYear, currentYear), minimumAge)

    /**
     * Signals only tighten ad treatment; the app-wide stop stays with the declared band (signals below 13 refuse online
     * features through [AgeState.Known.onlineAllowed]). Unknown upper bound = no information.
     */
    fun bandForSignal(signal: AgeSignal): AgeBand? {
        val upper = signal.ageUpper ?: return null
        return when {
            upper < 16 -> AgeBand.CHILD
            upper < 18 -> AgeBand.TEEN
            else -> null
        }
    }

    fun stricter(a: AgeBand, b: AgeBand?): AgeBand = if (b == null || a.ordinal <= b.ordinal) a else b

    fun adTreatment(band: AgeBand): AdAgeTreatment = when (band) {
        AgeBand.UNDER_MINIMUM, AgeBand.CHILD -> AdAgeTreatment.CHILD
        AgeBand.TEEN -> AdAgeTreatment.TEEN
        AgeBand.ADULT -> AdAgeTreatment.UNSPECIFIED
    }
}
