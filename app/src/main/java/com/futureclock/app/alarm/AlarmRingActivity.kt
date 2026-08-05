package com.futureclock.app.alarm

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.futureclock.app.FutureClockApp
import com.futureclock.app.R
import com.futureclock.app.databinding.ActivityAlarmRingBinding
import com.futureclock.app.util.TimeFormat
import com.futureclock.app.util.AlarmMath
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class AlarmRingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmRingBinding
    private var alarmId: Long = -1L
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var volumeJob: Job? = null
    private var challengeJob: Job? = null
    private var challengeAnswer: Int = 0

    private val blockBackCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            // Prevent accidental dismiss; require explicit Dismiss tap
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmRingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this, blockBackCallback)

        // Keep screen on & show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        alarmId = intent.getLongExtra(com.futureclock.app.notification.Extras.ALARM_ID, -1L)
        if (alarmId < 0) { finish(); return }

        binding.btnSnooze.setOnClickListener { onSnooze() }
        binding.btnDismiss.setOnClickListener { onDismiss() }
        loadAlarm()
    }

    private fun loadAlarm() {
        val app = application as FutureClockApp
        lifecycleScope.launch {
            val alarm = app.database.alarmDao().getById(alarmId) ?: run { finish(); return@launch }
            val zone = AlarmMath.timeZone(alarm.timeZoneId)
            binding.textAlarmTime.text = TimeFormat.formatTime(
                zone = zone,
                use24h = true,
                hour = alarm.hour,
                minute = alarm.minute
            )
            binding.textAlarmDate.text = TimeFormat.formatDate(zone)
            binding.textAlarmLabel.text = if (alarm.label.isBlank())
                getString(R.string.notif_alarm_title).uppercase() else alarm.label.uppercase()

            // Difficulty
            if (alarm.difficulty > 0) setupChallenge(alarm.difficulty)

            // Sound
            startSound(alarm.gradualVolume)
            if (alarm.vibrate) startVibrate()

            // Re-arm if repeating
            if (alarm.daysOfWeek == 0) {
                app.database.alarmDao().setEnabled(alarm.id, false)
            } else {
                AlarmScheduler.schedule(this@AlarmRingActivity, alarm)
            }
            // Refresh widget
            com.futureclock.app.widget.WidgetUpdateScheduler.refreshAll(this@AlarmRingActivity)
        }
    }

    private fun setupChallenge(level: Int) {
        binding.challengeContainer.visibility = android.view.View.VISIBLE
        challengeJob = lifecycleScope.launch {
            while (true) {
                val (q, a) = when (level) {
                    1 -> generateEasy()
                    2 -> generateHard()
                    else -> generateEasy()
                }
                challengeAnswer = a
                binding.textChallenge.text = q
                binding.editChallengeAnswer.setText("")
                delay(30_000L)
            }
        }
    }

    private fun generateEasy(): Pair<String, Int> {
        val a = Random.nextInt(1, 10)
        val b = Random.nextInt(1, 10)
        return "$a + $b" to (a + b)
    }

    private fun generateHard(): Pair<String, Int> {
        val a = Random.nextInt(10, 50)
        val b = Random.nextInt(10, 50)
        val c = Random.nextInt(1, 20)
        return "$a + $b - $c" to (a + b - c)
    }

    private fun startSound(gradual: Boolean) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmRingActivity, uri)
                isLooping = true
                prepare()
                if (gradual) {
                    setVolume(0.05f, 0.05f)
                    start()
                    volumeJob = lifecycleScope.launch {
                        val target = 1.0f
                        val steps = 20
                        val step = (target - 0.05f) / steps
                        for (i in 1..steps) {
                            val v = 0.05f + step * i
                            player?.setVolume(v, v)
                            delay(1_000L)
                        }
                    }
                } else {
                    setVolume(1.0f, 1.0f)
                    start()
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private fun startVibrate() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 400, 800)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (_: Throwable) { /* no vibrator on this device */ }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            player?.stop()
            player?.release()
        } catch (_: Throwable) {}
        vibrator?.cancel()
        volumeJob?.cancel()
        challengeJob?.cancel()
    }

    private fun onSnooze() {
        val app = application as FutureClockApp
        lifecycleScope.launch {
            val alarm = app.database.alarmDao().getById(alarmId) ?: return@launch finish()
            AlarmScheduler.snooze(this@AlarmRingActivity, alarm)
            finishAndRemoveTask()
        }
    }

    private fun onDismiss() {
        // If challenge active, require correct answer
        if (binding.challengeContainer.visibility == android.view.View.VISIBLE) {
            val ans = binding.editChallengeAnswer.text.toString().toIntOrNull() ?: -1
            if (ans != challengeAnswer) {
                binding.editChallengeAnswer.error = getString(R.string.alarm_challenge_try_again)
                return
            }
        }
        finishAndRemoveTask()
    }
}
