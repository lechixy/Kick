package com.lechixy.kick.data.account

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*

@Serializable
data class Challenge(
    val id: String,
    val name: String,
    val description: String = "",
    val condition: Condition,
    @SerialName("drop_table") val dropTable: List<DropTableEntry> = emptyList(),
    val featured: Boolean = false,
    val recurrence: String? = null,
    val status: String,
    val window: Window? = null,
    @SerialName("active_period") val activePeriod: Window? = null,
    val animations: ChallengeAnimations? = null
) {
    val remaining: Long get() = (condition.threshold.coerceAtLeast(0) - condition.progress.coerceAtLeast(0)).coerceAtLeast(0)
    val claimed: Boolean get() = status in setOf("claimed", "redeemed")
    fun canClaim(now: Instant): Boolean = !claimed && condition.threshold > 0 && remaining == 0L &&
        window?.contains(now) != false && activePeriod?.contains(now) != false
    fun expired(now: Instant): Boolean = listOfNotNull(window?.end, activePeriod?.end).any { !now.isBefore(it) }
}
@Serializable
data class Condition(val progress: Long, val threshold: Long, val type: String)
@Serializable
data class DropTableEntry(val rarity: String, val weighting: Long)
@Serializable
data class Window(@SerialName("starts_at") val startsAt: String, @SerialName("ends_at") val endsAt: String) {
    val start: Instant? get() = runCatching { Instant.parse(startsAt) }.getOrNull()
    val end: Instant? get() = runCatching { Instant.parse(endsAt) }.getOrNull()
    fun contains(now: Instant): Boolean = start?.let { !now.isBefore(it) } == true && end?.let { now.isBefore(it) } == true
}
@Serializable
data class ChallengeAnimations(@SerialName("idle_url") val idleUrl: String? = null, @SerialName("unbox_url") val unboxUrl: String? = null)

/** Kick returns an animation base URL; Coil needs the GIF rendition, not the 404 base path. */
internal fun rewardAnimationImageUrl(raw: String?): String? {
    val url = raw?.toHttpUrlOrNull() ?: return null
    return if (url.host == "static.kick.com" && url.encodedPath.startsWith("/rewards/") &&
        url.pathSegments.lastOrNull() in setOf("idle", "unbox")) {
        url.newBuilder().encodedPath(url.encodedPath + ".gif").build().toString()
    } else url.toString()
}

internal fun orderChallenges(challenges: List<Challenge>): List<Challenge> = challenges.sortedWith(
    compareByDescending<Challenge> { it.activePeriod != null }.thenByDescending { it.featured }
)
@Serializable
data class WatchTimeGoalReached(
    @SerialName("user_id") val userId: Long,
    @SerialName("challenge_id") val challengeId: String,
    val recurrence: String? = null,
    @SerialName("threshold_minutes") val thresholdMinutes: Long
)

fun DropTableEntry.chancePercent(table: List<DropTableEntry>): BigDecimal {
    val total = table.fold(BigDecimal.ZERO) { sum, entry -> sum + entry.weighting.coerceAtLeast(0).toBigDecimal() }
    return if (total.signum() == 0) BigDecimal.ZERO else weighting.coerceAtLeast(0).toBigDecimal()
        .multiply(BigDecimal(100)).divide(total, 12, RoundingMode.HALF_UP)
}
fun formatDropChance(entry: DropTableEntry, table: List<DropTableEntry>, locale: Locale): String {
    val value = entry.chancePercent(table)
    val formatter = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 8; isGroupingUsed = false }
    return if (value.signum() > 0 && value < BigDecimal("0.00000001")) "<${formatter.format(BigDecimal("0.00000001"))}%"
        else "${formatter.format(value)}%"
}
fun challengeCountdown(end: Instant?, now: Instant): String? {
    if (end == null) return null
    val seconds = Duration.between(now, end).seconds.coerceAtLeast(0)
    return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
}

enum class ChallengeLoadFailure { Http, Network, Format, Other }
data class ChallengesState(val challenges: List<Challenge> = emptyList(), val loading: Boolean = false,
    val loaded: Boolean = false, val failed: Boolean = false,
    val failure: ChallengeLoadFailure? = null, val httpStatus: Int? = null,
    val claimingId: String? = null, val reward: ChallengeClaim? = null,
    val claimFailed: Boolean = false, val claimHttpStatus: Int? = null)

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
internal fun decodeChallenges(response: JsonElement, json: Json): List<Challenge> {
    val array = when (response) {
        is JsonArray -> response
        is JsonObject -> response["data"] as? JsonArray
        else -> null
    } ?: throw SerializationException("Expected challenge array or data array")
    return array.mapIndexed { index, element ->
        try { json.decodeFromJsonElement<Challenge>(element) }
        catch (error: SerializationException) {
            val fields = (error as? MissingFieldException)?.missingFields?.joinToString().orEmpty()
            accountDiagnostic("KickChallenges", "Decode failed: entry=$index type=${error.javaClass.simpleName} missingFields=[$fields]")
            throw error
        }
    }.distinctBy { it.id }
}

class ChallengesRepository(private val auth: AuthRepository, private val scope: CoroutineScope) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutableState = MutableStateFlow(ChallengesState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var claimJob: Job? = null
    init {
        scope.launch {
            auth.session.map { Triple(it.signedIn, it.generation, it.profile?.userId) }.distinctUntilChanged().collect { (signedIn, _, _) ->
                job?.cancel()
                claimJob?.cancel()
                mutableState.value = ChallengesState()
                if (signedIn) refresh()
            }
        }
    }
    fun refresh() {
        if (!auth.session.value.signedIn) return
        val generation = auth.session.value.generation
        while (true) {
            val current = state.value
            if (current.loading) return
            if (mutableState.compareAndSet(current, current.copy(loading = true, failed = false, failure = null, httpStatus = null))) break
        }
        job = scope.launch {
            try {
                val response = auth.requestElement("https://web.kick.com/api/v1/gamification/challenges")
                val shape = when (response) {
                    is JsonArray -> "array(size=${response.size})"
                    is JsonObject -> "object(keys=${response.keys.joinToString()})"
                    else -> "${response.javaClass.simpleName}"
                }
                accountDiagnostic("KickChallenges", "Challenges response: $shape")
                val challenges = decodeChallenges(response, json)
                accountDiagnostic("KickChallenges", "Loaded ${challenges.size} challenges")
                if (generation == auth.session.value.generation && auth.session.value.signedIn) {
                    mutableState.update { previous ->
                        val old = previous.challenges.associateBy { it.id }
                        previous.copy(challenges = challenges.map { challenge ->
                            val prior = old[challenge.id]
                            val sameWindow = prior != null && prior.window == challenge.window && prior.activePeriod == challenge.activePeriod
                            if (sameWindow && prior.claimed) return@map challenge.copy(status = "claimed")
                            // Watch time is monotonic within a window; an in-flight fetch may predate the goal event.
                            if (prior != null && challenge.window != null && prior.window == challenge.window &&
                                prior.condition.type == challenge.condition.type && challenge.condition.type == "watch_time_minutes" &&
                                prior.condition.threshold == challenge.condition.threshold) {
                                challenge.copy(condition = challenge.condition.copy(progress = maxOf(prior.condition.progress, challenge.condition.progress)))
                            } else challenge
                        }, loaded = true, loading = false, failed = false, failure = null, httpStatus = null)
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val failure = when (error) {
                    is AccountApiException -> ChallengeLoadFailure.Http
                    is java.io.IOException -> ChallengeLoadFailure.Network
                    is SerializationException, is IllegalArgumentException -> ChallengeLoadFailure.Format
                    else -> ChallengeLoadFailure.Other
                }
                val status = (error as? AccountApiException)?.status
                accountDiagnostic("KickChallenges", "Load failed: kind=$failure status=$status type=${error.javaClass.simpleName}")
                if (generation == auth.session.value.generation && auth.session.value.signedIn) {
                    mutableState.update { it.copy(loading = false, failed = true, failure = failure, httpStatus = status) }
                }
            }
        }
    }
    /** One POST per user action; polling and reveal acknowledgement never repeat the mutation. */
    fun claim(challenge: Challenge) {
        val session = auth.session.value
        if (!session.signedIn) return
        while (true) {
            val current = state.value
            val eligible = current.challenges.firstOrNull { it.id == challenge.id } ?: return
            if (current.claimingId != null || current.reward != null || !eligible.canClaim(Instant.now())) return
            if (mutableState.compareAndSet(current, current.copy(claimingId = eligible.id, claimFailed = false, claimHttpStatus = null))) break
        }
        claimJob = scope.launch {
            var accepted = false
            try {
                val id = java.net.URLEncoder.encode(challenge.id, Charsets.UTF_8.name())
                val response = auth.request("https://web.kick.com/api/v1/gamification/challenges/$id/claim", "POST")
                accepted = true
                val reward = decodeChallengeClaim(response, challenge.id, json)
                if (session.generation == auth.session.value.generation && auth.session.value.signedIn) {
                    mutableState.update { it.copy(claimingId = null, reward = reward,
                        challenges = it.challenges.map { entry -> if (entry.id == challenge.id) entry.copy(status = "claimed") else entry }) }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (session.generation == auth.session.value.generation && auth.session.value.signedIn) {
                    mutableState.update { it.copy(claimingId = null, claimFailed = true,
                        claimHttpStatus = (error as? AccountApiException)?.status,
                        challenges = if (accepted) it.challenges.map { entry ->
                            if (entry.id == challenge.id) entry.copy(status = "claimed") else entry
                        } else it.challenges) }
                    refresh()
                }
            }
        }
    }

    fun acknowledgeReward() {
        mutableState.update { it.copy(reward = null) }
        refresh()
    }
    /** Receives only the current user's private publication; never derives watch time locally. */
    fun onGoalReached(channel: String?, payload: JsonElement) {
        val session = auth.session.value
        val user = session.profile?.userId ?: return
        if (!session.signedIn || channel != "private-$user") return
        val event = runCatching {
            val element = if (payload is JsonPrimitive && payload.isString) json.parseToJsonElement(payload.content) else payload
            json.decodeFromJsonElement<WatchTimeGoalReached>(element)
        }.getOrNull() ?: return
        if (event.userId != user || event.thresholdMinutes <= 0) return
        val now = Instant.now()
        mutableState.update { state -> state.copy(challenges = state.challenges.map { challenge ->
            if (challenge.id == event.challengeId && challenge.recurrence == event.recurrence &&
                challenge.condition.type == "watch_time_minutes" && challenge.condition.threshold == event.thresholdMinutes &&
                !challenge.claimed && challenge.window?.contains(now) != false && challenge.activePeriod?.contains(now) != false) {
                challenge.copy(condition = challenge.condition.copy(progress = maxOf(challenge.condition.progress, challenge.condition.threshold)))
            } else challenge
        }) }
        refresh()
    }
}
