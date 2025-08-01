package com.example.tradeveil.services

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object AdManager {
    private const val GET_TICKET_AD_UNIT_ID = "ca-app-pub-2219058417636032/8828655709"
    private const val OPEN_TICKET_AD_UNIT_ID = "ca-app-pub-2219058417636032/8445512320"
    private const val QUIZ_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-2219058417636032/1326543977"
    private const val TRANSFER_POINTS_AD_UNIT_ID = "ca-app-pub-2219058417636032/6415235038"
    private const val DAILY_CHECKIN_AD_UNIT_ID = "ca-app-pub-2219058417636032/9971336662"
    private const val CHAT_REWARD_AD_UNIT_ID = "ca-app-pub-2219058417636032/4263774484"
    private const val MISSIONS_REWARD_AD_UNIT_ID = "ca-app-pub-2219058417636032/8503620984"

    private var getTicketAd: RewardedAd? = null
    private var openTicketAd: RewardedAd? = null
    internal var chatRewardAd: RewardedAd? = null
    private var dailyCheckinAd: InterstitialAd? = null
    private var quizInterstitialAd: InterstitialAd? = null
    private var transferPointsAd: InterstitialAd? = null
    private var missionsRewardAd: RewardedAd? = null

    private val isInitialized = AtomicBoolean(false)
    private val isLoadingGetTicketAd = AtomicBoolean(false)
    private val isLoadingOpenTicketAd = AtomicBoolean(false)
    private val isLoadingChatRewardAd = AtomicBoolean(false)
    private val isLoadingDailyCheckinAd = AtomicBoolean(false)
    private val isLoadingQuizInterstitialAd = AtomicBoolean(false)
    private val isLoadingTransferPointsAd = AtomicBoolean(false)
    private val isLoadingMissionsRewardAd = AtomicBoolean(false)

    private val chatAdRetryCount = AtomicInteger(0)
    private val getTicketAdRetryCount = AtomicInteger(0)
    private val openTicketAdRetryCount = AtomicInteger(0)
    private val quizInterstitialRetryCount = AtomicInteger(0)
    private val dailyCheckinRetryCount = AtomicInteger(0)
    private val transferPointsRetryCount = AtomicInteger(0)
    private val missionsRewardRetryCount = AtomicInteger(0)

    private const val MAX_RETRY_COUNT = 3
    private const val RETRY_DELAY_MS = 30000L

    private val handler = Handler(Looper.getMainLooper())
    private var getTicketRewardEarned = false
    private var openTicketRewardEarned = false
    private var chatRewardEarned = false
    private var missionsRewardEarned = false

    fun initialize(context: Context, onInitialized: (() -> Unit)? = null) {
        if (!isInitialized.getAndSet(true)) {
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE)
                    .setTagForUnderAgeOfConsent(RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_FALSE)
                    .build()
            )

            MobileAds.initialize(context) {
                loadAllAds(context)
                onInitialized?.invoke()
            }
        } else {
            onInitialized?.invoke()
        }
    }

    private fun loadAllAds(context: Context) {
        loadGetTicketAd(context)
        loadOpenTicketAd(context)
        loadChatRewardAd(context)
        loadDailyCheckinAd(context)
        loadQuizInterstitialAd(context)
        loadTransferPointsAd(context)
        loadMissionsRewardAd(context)
    }

    private fun createAdRequest(): AdRequest {
        return AdRequest.Builder().build()
    }

    private fun loadChatRewardAd(context: Context) {
        if (isLoadingChatRewardAd.getAndSet(true)) return

        val currentRetryCount = chatAdRetryCount.get()
        RewardedAd.load(context, CHAT_REWARD_AD_UNIT_ID, createAdRequest(),
            object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    isLoadingChatRewardAd.set(false)
                    chatRewardAd = null
                    handleAdLoadError(adError, currentRetryCount, chatAdRetryCount) {
                        loadChatRewardAd(context)
                    }
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    isLoadingChatRewardAd.set(false)
                    chatAdRetryCount.set(0)
                    chatRewardAd = ad
                }
            })
    }

    private fun loadQuizInterstitialAd(context: Context) {
        if (isLoadingQuizInterstitialAd.getAndSet(true)) return

        val currentRetryCount = quizInterstitialRetryCount.get()
        InterstitialAd.load(context, QUIZ_INTERSTITIAL_AD_UNIT_ID, createAdRequest(),
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    isLoadingQuizInterstitialAd.set(false)
                    quizInterstitialAd = null
                    handleAdLoadError(adError, currentRetryCount, quizInterstitialRetryCount) {
                        loadQuizInterstitialAd(context)
                    }
                }

                override fun onAdLoaded(ad: InterstitialAd) {
                    isLoadingQuizInterstitialAd.set(false)
                    quizInterstitialRetryCount.set(0)
                    quizInterstitialAd = ad
                }
            })
    }

    private fun loadMissionsRewardAd(context: Context) {
        if (isLoadingMissionsRewardAd.getAndSet(true)) return

        val currentRetryCount = missionsRewardRetryCount.get()
        RewardedAd.load(context, MISSIONS_REWARD_AD_UNIT_ID, createAdRequest(),
            object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    isLoadingMissionsRewardAd.set(false)
                    missionsRewardAd = null
                    handleAdLoadError(adError, currentRetryCount, missionsRewardRetryCount) {
                        loadMissionsRewardAd(context)
                    }
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    isLoadingMissionsRewardAd.set(false)
                    missionsRewardRetryCount.set(0)
                    missionsRewardAd = ad
                }
            })
    }

    private fun loadDailyCheckinAd(context: Context) {
        if (isLoadingDailyCheckinAd.getAndSet(true)) return

        val currentRetryCount = dailyCheckinRetryCount.get()
        InterstitialAd.load(context, DAILY_CHECKIN_AD_UNIT_ID, createAdRequest(),
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    isLoadingDailyCheckinAd.set(false)
                    dailyCheckinAd = null
                    handleAdLoadError(adError, currentRetryCount, dailyCheckinRetryCount) {
                        loadDailyCheckinAd(context)
                    }
                }

                override fun onAdLoaded(ad: InterstitialAd) {
                    isLoadingDailyCheckinAd.set(false)
                    dailyCheckinRetryCount.set(0)
                    dailyCheckinAd = ad
                }
            })
    }

    private fun loadTransferPointsAd(context: Context) {
        if (isLoadingTransferPointsAd.getAndSet(true)) return

        val currentRetryCount = transferPointsRetryCount.get()
        InterstitialAd.load(context, TRANSFER_POINTS_AD_UNIT_ID, createAdRequest(),
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    isLoadingTransferPointsAd.set(false)
                    transferPointsAd = null
                    handleAdLoadError(adError, currentRetryCount, transferPointsRetryCount) {
                        loadTransferPointsAd(context)
                    }
                }

                override fun onAdLoaded(ad: InterstitialAd) {
                    isLoadingTransferPointsAd.set(false)
                    transferPointsRetryCount.set(0)
                    transferPointsAd = ad

                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            transferPointsAd = null
                            loadTransferPointsAd(context)
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            transferPointsAd = null
                            loadTransferPointsAd(context)
                        }
                    }
                }
            })
    }

    private fun loadGetTicketAd(context: Context) {
        if (isLoadingGetTicketAd.getAndSet(true)) return

        val currentRetryCount = getTicketAdRetryCount.get()
        RewardedAd.load(context, GET_TICKET_AD_UNIT_ID, createAdRequest(),
            object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    isLoadingGetTicketAd.set(false)
                    getTicketAd = null
                    handleAdLoadError(adError, currentRetryCount, getTicketAdRetryCount) {
                        loadGetTicketAd(context)
                    }
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    isLoadingGetTicketAd.set(false)
                    getTicketAdRetryCount.set(0)
                    getTicketAd = ad
                }
            })
    }

    private fun loadOpenTicketAd(context: Context) {
        if (isLoadingOpenTicketAd.getAndSet(true)) return

        val currentRetryCount = openTicketAdRetryCount.get()
        RewardedAd.load(context, OPEN_TICKET_AD_UNIT_ID, createAdRequest(),
            object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    isLoadingOpenTicketAd.set(false)
                    openTicketAd = null
                    handleAdLoadError(adError, currentRetryCount, openTicketAdRetryCount) {
                        loadOpenTicketAd(context)
                    }
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    isLoadingOpenTicketAd.set(false)
                    openTicketAdRetryCount.set(0)
                    openTicketAd = ad
                }
            })
    }

    private fun handleAdLoadError(
        adError: LoadAdError,
        currentRetryCount: Int,
        retryCounter: AtomicInteger,
        retryFunction: () -> Unit
    ) {
        if (adError.code != AdRequest.ERROR_CODE_INVALID_REQUEST && currentRetryCount < MAX_RETRY_COUNT) {
            retryCounter.incrementAndGet()
            handler.postDelayed(retryFunction, RETRY_DELAY_MS)
        } else {
            retryCounter.set(0)
        }
    }

    fun showChatRewardAd(
        activity: Activity,
        onAdDismissed: () -> Unit,
        onRewardEarned: () -> Unit,
        onAdFailedToLoad: () -> Unit
    ) {
        chatRewardAd?.let { ad ->
            chatRewardEarned = false

            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    if (chatRewardEarned) onRewardEarned() else onAdDismissed()
                    chatRewardAd = null
                    loadChatRewardAd(activity)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    chatRewardAd = null
                    loadChatRewardAd(activity)
                    onAdFailedToLoad()
                }
            }

            ad.show(activity) { _ ->
                chatRewardEarned = true
            }
        } ?: run {
            if (!isLoadingChatRewardAd.get()) loadChatRewardAd(activity)
            onAdFailedToLoad()
        }
    }

    fun showInterstitialAd(activity: Activity, onAdDismissed: () -> Unit = {}) {
        quizInterstitialAd?.let { ad ->
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    quizInterstitialAd = null
                    loadQuizInterstitialAd(activity)
                    onAdDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    quizInterstitialAd = null
                    loadQuizInterstitialAd(activity)
                    onAdDismissed()
                }
            }
            ad.show(activity)
        } ?: run {
            if (!isLoadingQuizInterstitialAd.get()) loadQuizInterstitialAd(activity)
            onAdDismissed()
        }
    }

    fun showMissionsRewardAd(
        activity: Activity,
        onAdDismissed: () -> Unit,
        onRewardEarned: () -> Unit,
        onAdFailedToLoad: () -> Unit
    ) {
        missionsRewardAd?.let { ad ->
            missionsRewardEarned = false

            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    if (missionsRewardEarned) onRewardEarned() else onAdDismissed()
                    missionsRewardAd = null
                    loadMissionsRewardAd(activity)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    missionsRewardAd = null
                    loadMissionsRewardAd(activity)
                    onAdFailedToLoad()
                }
            }

            ad.show(activity) { _ ->
                missionsRewardEarned = true
            }
        } ?: run {
            if (!isLoadingMissionsRewardAd.get()) loadMissionsRewardAd(activity)
            onAdFailedToLoad()
        }
    }

    fun showDailyCheckinAd(activity: Activity, onAdDismissed: () -> Unit) {
        dailyCheckinAd?.let { ad ->
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    dailyCheckinAd = null
                    loadDailyCheckinAd(activity)
                    onAdDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    dailyCheckinAd = null
                    loadDailyCheckinAd(activity)
                    onAdDismissed()
                }
            }
            ad.show(activity)
        } ?: run {
            if (!isLoadingDailyCheckinAd.get()) loadDailyCheckinAd(activity)
            onAdDismissed()
        }
    }

    fun showTransferPointsAd(activity: Activity, onAdDismissed: () -> Unit) {
        transferPointsAd?.let { ad ->
            ad.show(activity)
            onAdDismissed()
        } ?: run {
            if (!isLoadingTransferPointsAd.get()) loadTransferPointsAd(activity)
            onAdDismissed()
        }
    }

    fun showGetTicketAd(
        activity: Activity,
        onAdDismissed: () -> Unit,
        onRewardEarned: () -> Unit,
        onAdFailedToLoad: () -> Unit
    ) {
        getTicketAd?.let { ad ->
            getTicketRewardEarned = false

            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    if (getTicketRewardEarned) onRewardEarned() else onAdDismissed()
                    getTicketAd = null
                    loadGetTicketAd(activity)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    getTicketAd = null
                    loadGetTicketAd(activity)
                    onAdFailedToLoad()
                }
            }

            ad.show(activity) { _ ->
                getTicketRewardEarned = true
            }
        } ?: run {
            if (!isLoadingGetTicketAd.get()) loadGetTicketAd(activity)
            onAdFailedToLoad()
        }
    }

    fun showOpenTicketAd(
        activity: Activity,
        onAdDismissed: () -> Unit,
        onRewardEarned: () -> Unit,
        onAdFailedToLoad: () -> Unit
    ) {
        openTicketAd?.let { ad ->
            openTicketRewardEarned = false

            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    if (openTicketRewardEarned) onRewardEarned() else onAdDismissed()
                    openTicketAd = null
                    loadOpenTicketAd(activity)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    openTicketAd = null
                    loadOpenTicketAd(activity)
                    onAdFailedToLoad()
                }
            }

            ad.show(activity) { _ ->
                openTicketRewardEarned = true
            }
        } ?: run {
            if (!isLoadingOpenTicketAd.get()) loadOpenTicketAd(activity)
            onAdFailedToLoad()
        }
    }

    fun isChatRewardAdReady(): Boolean = chatRewardAd != null
    fun isGetTicketAdReady(): Boolean = getTicketAd != null
    fun isOpenTicketAdReady(): Boolean = openTicketAd != null
    fun isQuizInterstitialAdReady(): Boolean = quizInterstitialAd != null
    fun isDailyCheckinAdReady(): Boolean = dailyCheckinAd != null
    fun isTransferPointsAdReady(): Boolean = transferPointsAd != null
    fun isMissionsRewardAdReady(): Boolean = missionsRewardAd != null

    fun pauseAds() = Unit
    fun resumeAds() = Unit
}