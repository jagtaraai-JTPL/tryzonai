package com.jagtarapvtltd.tryzonai.utils

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

data class AffiliateMarketplace(
    val storeName: String,
    val logoTag: String,
    val domain: String,
    val affiliateTag: String
)

object CountryDetector {
    /**
     * Detects user's country using 3 fallback levels:
     * 1. SIM Card Country ISO (Telephony)
     * 2. Active Cell Network Country ISO (Telephony)
     * 3. Device System Locale (Locale.getDefault().country)
     */
    fun getUserCountry(context: Context): String {
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val simCountry = tm?.simCountryIso?.uppercase(Locale.ROOT)
            if (!simCountry.isNullOrEmpty() && simCountry.length == 2) {
                return simCountry
            }
            val networkCountry = tm?.networkCountryIso?.uppercase(Locale.ROOT)
            if (!networkCountry.isNullOrEmpty() && networkCountry.length == 2) {
                return networkCountry
            }
        } catch (e: Exception) {
            // Telephony service unavailable
        }
        
        val localeCountry = Locale.getDefault().country.uppercase(Locale.ROOT)
        if (localeCountry.isNotEmpty() && localeCountry.length == 2) {
            return localeCountry
        }
        return "US" // Global fallback
    }

    /**
     * Returns country-specific affiliate fashion marketplaces & deals.
     */
    fun getAffiliateMarketplaces(context: Context): List<AffiliateMarketplace> {
        val country = getUserCountry(context)
        return when (country) {
            "IN" -> listOf(
                AffiliateMarketplace("Amazon India", "amazon", "amazon.in", "tryzonai-21"),
                AffiliateMarketplace("Flipkart", "flipkart", "flipkart.com", "tryzonai"),
                AffiliateMarketplace("Myntra", "myntra", "myntra.com", "tryzonai")
            )
            "US" -> listOf(
                AffiliateMarketplace("Amazon US", "amazon", "amazon.com", "tryzonaius-20"),
                AffiliateMarketplace("ASOS US", "asos", "asos.com/us", "tryzonai"),
                AffiliateMarketplace("Nordstrom", "nordstrom", "nordstrom.com", "tryzonai")
            )
            "GB" -> listOf(
                AffiliateMarketplace("Amazon UK", "amazon", "amazon.co.uk", "tryzonaiuk-21"),
                AffiliateMarketplace("ASOS UK", "asos", "asos.com/gb", "tryzonai"),
                AffiliateMarketplace("Boohoo", "boohoo", "boohoo.com", "tryzonai")
            )
            "DE", "FR", "IT", "ES", "NL" -> listOf(
                AffiliateMarketplace("Zalando", "zalando", "zalando.de", "tryzonai"),
                AffiliateMarketplace("Amazon EU", "amazon", "amazon.de", "tryzonaieu-21"),
                AffiliateMarketplace("ASOS EU", "asos", "asos.com", "tryzonai")
            )
            "AE" -> listOf(
                AffiliateMarketplace("Amazon UAE", "amazon", "amazon.ae", "tryzonaiae-21"),
                AffiliateMarketplace("Noon", "noon", "noon.com", "tryzonai"),
                AffiliateMarketplace("Namshi", "namshi", "namshi.com", "tryzonai")
            )
            else -> listOf(
                AffiliateMarketplace("Amazon Global", "amazon", "amazon.com", "tryzonaius-20"),
                AffiliateMarketplace("ASOS World", "asos", "asos.com", "tryzonai"),
                AffiliateMarketplace("Farfetch", "farfetch", "farfetch.com", "tryzonai")
            )
        }
    }
}
