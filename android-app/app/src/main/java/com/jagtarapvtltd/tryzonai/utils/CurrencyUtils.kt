package com.jagtarapvtltd.tryzonai.utils

import java.util.Locale

object CurrencyUtils {
    fun isIndianUser(): Boolean {
        val locale = Locale.getDefault()
        return locale.country.uppercase() == "IN" || (locale.country.isEmpty() && locale.language == "hi")
    }

    fun formatPrice(amountInINR: Double): String {
        if (amountInINR <= 0) return ""
        val locale = Locale.getDefault()
        val country = locale.country.uppercase()
        
        return when (country) {
            "IN" -> "₹${amountInINR.toInt()}"
            "AE" -> {
                val aedAmount = when (amountInINR.toInt()) {
                    39 -> 3.99
                    379 -> 18.99
                    499 -> 24.99
                    else -> amountInINR * 0.044
                }
                "AED ${String.format(Locale.US, "%.2f", aedAmount)}"
            }
            else -> {
                // International (USD Pricing for US, UK, EU, Global)
                val usdAmount = when (amountInINR.toInt()) {
                    39 -> 0.99
                    379 -> 4.99
                    499 -> 9.99
                    else -> amountInINR * 0.012
                }
                "$${String.format(Locale.US, "%.2f", usdAmount)}"
            }
        }
    }

    fun formatPrice(amountInINR: Int): String = formatPrice(amountInINR.toDouble())

    fun formatSavings(savingsInINR: Double): String {
        val locale = Locale.getDefault()
        val country = locale.country
        if (country.isNotEmpty() && country != "IN") {
            val usdAmount = savingsInINR * 0.012
            return "SAVE $${String.format(Locale.US, "%.2f", usdAmount)}"
        }
        return "SAVE ₹${savingsInINR.toInt()}"
    }

    fun formatSavings(savingsInINR: Int): String = formatSavings(savingsInINR.toDouble())

    fun formatReward(rewardInINR: Double): String {
        val locale = Locale.getDefault()
        val country = locale.country
        if (country.isNotEmpty() && country != "IN") {
            val usdAmount = rewardInINR * 0.012
            return "Includes $${String.format(Locale.US, "%.2f", usdAmount)} Reward"
        }
        return "Includes ₹${rewardInINR.toInt()} Reward"
    }

    fun formatReward(rewardInINR: Int): String = formatReward(rewardInINR.toDouble())
}
