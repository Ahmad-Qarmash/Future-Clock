package com.futureclock.app.ads

import android.content.Context
import android.view.ViewGroup
import com.futureclock.app.BuildConfig
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import java.util.concurrent.atomic.AtomicBoolean

/** Banner-only monetisation for the first public release.
 *
 * Full-screen ads are deliberately excluded from alarm dismissal and tab navigation: neither is
 * a clear break in a clock task. This keeps the release respectful and policy-friendly while
 * still allowing AdMob to monetize the app with anchored adaptive banners.
 */
object AdManager {

    private val initialized = AtomicBoolean(false)
    private var banner: AdView? = null

    fun initialize(context: Context) {
        if (initialized.compareAndSet(false, true)) {
            MobileAds.initialize(context.applicationContext) { }
        }
    }

    fun createBanner(context: Context, parent: ViewGroup): AdView {
        banner?.destroy()
        val adView = AdView(context).apply {
            adUnitId = BuildConfig.BANNER_AD_UNIT_ID
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, AdSize.FULL_WIDTH))
        }
        parent.removeAllViews()
        parent.addView(
            adView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        adView.loadAd(AdRequest.Builder().build())
        banner = adView
        return adView
    }

    fun pauseBanner() = banner?.pause()

    fun resumeBanner() = banner?.resume()

    fun destroyBanner() {
        banner?.destroy()
        banner = null
    }
}
