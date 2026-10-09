package org.shilpo.laboon.playback

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.shilpo.laboon.R
import java.util.concurrent.TimeUnit

data class SleepTimerState(
    val activeTimerValueDisplay: String? = null,
    val activeTimerDurationMinutes: Int? = null,
    val playCount: Float = 1f,
    val isEndOfTrackTimerActive: Boolean = false,
) {
    val isTimerActive: Boolean
        get() = activeTimerValueDisplay != null || isEndOfTrackTimerActive || playCount > 1f
}

class SleepTimerManager(private val context: Context) {

    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var countdownJob: Job? = null
    private var eotJob: Job? = null
    private var countedPlayJob: Job? = null

    fun setPredefinedTimer(durationMinutes: Int) {
        if (durationMinutes <= 0) {
            cancelTimer()
            return
        }

        cancelActiveJobs()

        val durationMillis = TimeUnit.MINUTES.toMillis(durationMinutes.toLong())
        val triggerAtMillis = System.currentTimeMillis() + durationMillis
        val pendingIntent = SleepTimerReceiver.createPendingIntent(context)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent,
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent,
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            }
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }

        _state.value = SleepTimerState(
            activeTimerValueDisplay = context.getString(
                R.string.sleep_timer_n_minutes_format,
                durationMinutes,
            ),
            activeTimerDurationMinutes = durationMinutes,
            playCount = 1f,
            isEndOfTrackTimerActive = false,
        )

        countdownJob = scope.launch {
            delay(durationMillis)
            onTimerExpired()
        }

        Toast.makeText(
            context,
            context.getString(R.string.sleep_timer_set_for_minutes_toast, durationMinutes),
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun setEndOfTrackTimer(enable: Boolean) {
        if (!enable) {
            cancelTimer()
            return
        }

        val playbackManager = PlaybackManagerHolder.getInstanceOrNull()
        val currentTrack = playbackManager?.state?.value?.currentTrack
        if (currentTrack == null) {
            Toast.makeText(
                context,
                context.getString(R.string.sleep_timer_eot_no_song_toast),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        cancelAlarm()
        cancelActiveJobs()

        _state.value = SleepTimerState(
            activeTimerValueDisplay = context.getString(R.string.sleep_timer_display_eot),
            activeTimerDurationMinutes = null,
            playCount = 1f,
            isEndOfTrackTimerActive = true,
        )

        Toast.makeText(
            context,
            context.getString(R.string.sleep_timer_eot_stop_at_end_toast),
            Toast.LENGTH_SHORT,
        ).show()

        val trackedTrackId = currentTrack.id
        val oldTitle = currentTrack.title
        eotJob = scope.launch {
            playbackManager.state
                .map { it.currentTrack?.id }
                .distinctUntilChanged()
                .collect { newTrackId ->
                    if (_state.value.isEndOfTrackTimerActive && newTrackId != trackedTrackId) {
                        val newTitle = playbackManager.state.value.currentTrack?.title
                            ?: context.getString(R.string.sleep_timer_label_current_track)
                        playbackManager.pause()
                        cancelTimer(suppressToast = true)
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.sleep_timer_eot_song_changed_toast,
                                oldTitle,
                                newTitle,
                            ),
                            Toast.LENGTH_SHORT,
                        ).show()
                        eotJob?.cancel()
                        eotJob = null
                    }
                }
        }
    }

    fun setPlayCounter(count: Int) {
        if (count <= 1) {
            cancelCountedPlay()
            return
        }

        cancelAlarm()
        cancelActiveJobs()

        _state.value = SleepTimerState(
            activeTimerValueDisplay = null,
            activeTimerDurationMinutes = null,
            playCount = count.toFloat(),
            isEndOfTrackTimerActive = false,
        )

        val playbackManager = PlaybackManagerHolder.getInstanceOrNull() ?: return
        countedPlayJob = scope.launch {
            var remaining = count
            var lastTrackId = playbackManager.state.value.currentTrack?.id
            playbackManager.state
                .map { it.currentTrack?.id }
                .distinctUntilChanged()
                .collect { newTrackId ->
                    if (newTrackId != lastTrackId) {
                        lastTrackId = newTrackId
                        remaining--
                        _state.update { it.copy(playCount = remaining.coerceAtLeast(1).toFloat()) }
                        if (remaining <= 0) {
                            playbackManager.pause()
                            cancelCountedPlay(suppressToast = true)
                            Toast.makeText(
                                context,
                                "Playback stopped by sleep timer",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
        }
    }

    fun cancelCountedPlay(suppressToast: Boolean = false) {
        countedPlayJob?.cancel()
        countedPlayJob = null
        _state.update { it.copy(playCount = 1f) }
        if (!suppressToast) {
            Toast.makeText(
                context,
                context.getString(R.string.sleep_timer_cancelled_toast),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun cancelTimer(suppressToast: Boolean = false) {
        val wasActive = _state.value.isTimerActive
        cancelAlarm()
        cancelActiveJobs()
        _state.value = SleepTimerState()

        if (wasActive && !suppressToast) {
            Toast.makeText(
                context,
                context.getString(R.string.sleep_timer_cancelled_toast),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun onTimerExpired() {
        cancelAlarm()
        cancelActiveJobs()
        _state.value = SleepTimerState()

        PlaybackManagerHolder.getInstanceOrNull()?.pause()
            ?: PlaybackServiceHolder.mediaSession?.player?.pause()

        scope.launch {
            Toast.makeText(context, "Playback stopped by sleep timer", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cancelAlarm() {
        alarmManager.cancel(SleepTimerReceiver.createPendingIntent(context))
    }

    private fun cancelActiveJobs() {
        countdownJob?.cancel()
        countdownJob = null
        eotJob?.cancel()
        eotJob = null
        countedPlayJob?.cancel()
        countedPlayJob = null
    }
}

object SleepTimerManagerHolder {
    private var instance: SleepTimerManager? = null
    private val lock = Any()

    fun getInstance(context: Context): SleepTimerManager {
        return synchronized(lock) {
            instance ?: SleepTimerManager(context.applicationContext).also {
                instance = it
            }
        }
    }
}
