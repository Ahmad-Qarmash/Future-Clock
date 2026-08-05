package com.futureclock.app.ads

import android.app.Activity
import android.util.Log
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Keeps Google Mobile Ads initialization behind the UMP consent flow. UMP refreshes the
 * consent state on every launch, so cached consent is never used as the sole source of truth.
 */
object ConsentManager {

    private const val TAG = "ConsentManager"

    fun gatherConsent(activity: Activity, onAdsPermitted: () -> Unit) {
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        val requestParameters = ConsentRequestParameters.Builder().build()
        var delivered = false

        fun continueWhenAllowed() {
            if (!delivered && consentInformation.canRequestAds() && !activity.isFinishing) {
                delivered = true
                onAdsPermitted()
            }
        }

        consentInformation.requestConsentInfoUpdate(
            activity,
            requestParameters,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    formError?.let { Log.w(TAG, "Consent form could not be shown: ${it.message}") }
                    continueWhenAllowed()
                }
            },
            { requestError ->
                Log.w(TAG, "Consent information update failed: ${requestError.message}")
                // A previous valid consent state may still permit requests. Never request
                // ads on a first-run error because canRequestAds() stays false in that case.
                continueWhenAllowed()
            }
        )
    }

    fun showPrivacyOptions(activity: Activity, onComplete: (available: Boolean) -> Unit) {
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            onComplete(error == null && consentInformation.isConsentFormAvailable)
        }
    }
}
