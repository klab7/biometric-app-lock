package eu.hxreborn.biometricapplock.updates

import androidx.annotation.StringRes
import eu.hxreborn.biometricapplock.R
import kotlinx.serialization.Serializable

sealed interface UpdateState {
    data object Idle : UpdateState

    data object Checking : UpdateState

    data class UpToDate(
        val currentVersion: String,
    ) : UpdateState

    data class Available(
        val currentVersion: String,
        val latestVersion: String,
        val releaseUrl: String,
    ) : UpdateState

    data class Failed(
        val cause: FailureCause,
    ) : UpdateState

    data class RateLimited(
        val resetAtEpochMs: Long?,
    ) : UpdateState
}

enum class FailureCause { Offline, Network, Parse, ServiceUnavailable }

sealed interface UpdateSheetState {
    @get:StringRes
    val titleRes: Int

    data object Checking : UpdateSheetState {
        override val titleRes: Int = R.string.updates_sheet_title_checking
    }

    data class UpToDate(
        val currentVersion: String,
    ) : UpdateSheetState {
        override val titleRes: Int = R.string.updates_sheet_title_up_to_date
    }

    data class Available(
        val currentVersion: String,
        val latestVersion: String,
        val releaseUrl: String,
        val notesAvailable: Boolean,
    ) : UpdateSheetState {
        override val titleRes: Int = R.string.updates_sheet_title_available
    }

    data class Failed(
        val cause: FailureCause,
        val cachedFallback: UpdateState.Available?,
    ) : UpdateSheetState {
        override val titleRes: Int =
            when (cause) {
                FailureCause.Offline -> R.string.updates_sheet_title_offline
                FailureCause.ServiceUnavailable -> R.string.updates_sheet_title_service_unavailable
                FailureCause.Network, FailureCause.Parse -> R.string.updates_sheet_title_failed
            }
    }

    data class RateLimited(
        val resetAtEpochMs: Long?,
    ) : UpdateSheetState {
        override val titleRes: Int = R.string.updates_sheet_title_rate_limited
    }

    data object WhatsNew : UpdateSheetState {
        override val titleRes: Int = R.string.whats_new_title
    }
}

fun UpdateState.toSheetState(
    cached: UpdateState.Available?,
    hasMatchingChangelogEntry: Boolean,
): UpdateSheetState =
    when (this) {
        UpdateState.Idle, UpdateState.Checking -> {
            UpdateSheetState.Checking
        }

        is UpdateState.UpToDate -> {
            UpdateSheetState.UpToDate(currentVersion)
        }

        is UpdateState.Available -> {
            UpdateSheetState.Available(
                currentVersion = currentVersion,
                latestVersion = latestVersion,
                releaseUrl = releaseUrl,
                notesAvailable = hasMatchingChangelogEntry,
            )
        }

        is UpdateState.Failed -> {
            UpdateSheetState.Failed(cause = cause, cachedFallback = cached)
        }

        is UpdateState.RateLimited -> {
            UpdateSheetState.RateLimited(resetAtEpochMs)
        }
    }

@Serializable
data class ChangelogManifest(
    val version: String = "",
    val entries: List<ChangelogEntry> = emptyList(),
)

@Serializable
data class ChangelogEntry(
    val type: String,
    val title: String,
    val description: String? = null,
    val version: String? = null,
    val date: String? = null,
    val url: String? = null,
    val scope: String? = null,
    val breaking: Boolean = false,
)

enum class ChangeType {
    Feat,
    Fix,
    Perf,
    Security,
    Refactor,
    Revert,
    Ci,
    Test,
    Misc,
    Breaking,
    ;

    companion object {
        fun from(
            raw: String,
            breaking: Boolean = false,
        ): ChangeType {
            if (breaking) return Breaking
            return when (raw.lowercase()) {
                "feat" -> Feat
                "fix" -> Fix
                "perf" -> Perf
                "security" -> Security
                "refactor" -> Refactor
                "revert" -> Revert
                "ci" -> Ci
                "test" -> Test
                "misc" -> Misc
                "breaking" -> Breaking
                else -> Misc
            }
        }
    }
}
