package com.futureclock.app.ads

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import com.futureclock.app.BuildConfig
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.util.concurrent.atomic.AtomicLong

object AdManager {

    private const val TAG = "AdManager"
    private const val MIN_INTERSTITIAL_INTERVAL_MS = 60_000L // 1 minute
    private const val NTH_TAB_CHANGE = 5

    private var interstitial: InterstitialAd? = null
    private var lastShownAt = AtomicLong(0L)
    private var tabChangeCounter = 0

    fun initialize(context: Context) {
        // Preload first interstitial
        loadInterstitial(context)
    }

    fun createBanner(context: Context, parent: ViewGroup): AdView {
        val adView = AdView(context)
        adView.adUnitId = BuildConfig.BANNER_AD_UNIT_ID
        adView.setAdSize(AdSize.BANNER)
        parent.removeAllViews()
        parent.addView(
            adView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        adView.loadAd(AdRequest.Builder().build())
        return adView
    }

    fun loadInterstitial(context: Context) {
        val req = AdRequest.Builder().build()
        InterstitialAd.load(
            context.applicationContext,
            BuildConfig.INTERSTITIAL_AD_UNIT_ID,
            req,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                    Log.d(TAG, "Interstitial loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.d(TAG, "Interstitial failed: ${error.message}")
                    interstitial = null
                }
            }
        )
    }

    /**
     * Try to show an interstitial. Honors a minimum interval and tab-change cadence.
     * Returns true if shown.
     */
    fun maybeShowInterstitial(activity: android.app.Activity, trigger: Trigger): Boolean {
        tabChangeCounter++
        val now = System.currentTimeMillis()
        val sinceLast = now - lastShownAt.get()
        val intervalOk = sinceLast > MIN_INTERSTITIAL_INTERVAL_MS
        val cadenceOk = when (trigger) {
            Trigger.TAB_CHANGE -> tabChangeCounter % NTH_TAB_CHANGE == 0
            Trigger.ALARM_DISMISS,
            Trigger.ADD_CITY -> true
        }
        if (!intervalOk || !cadenceOk) return false
        val ad = interstitial ?: return false
        var shown = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitial = null
                loadInterstitial(activity)
            }
            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                interstitial = null
                loadInterstitial(activity)
            }
        }
        try {
            ad.show(activity)
            shown = true
            lastShownAt.set(now)
        } catch (t: Throwable) {
            Log.w(TAG, "Interstitial show failed", t)
        }
        return shown
    }

    enum class Trigger { TAB_CHANGE, ALARM_DISMISS, ADD_CITY }
}
