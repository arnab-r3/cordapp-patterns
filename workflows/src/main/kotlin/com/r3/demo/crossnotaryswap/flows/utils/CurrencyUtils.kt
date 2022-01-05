package com.r3.demo.crossnotaryswap.flows.utils

import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.amount
import com.r3.corda.lib.tokens.money.FiatCurrency
import net.corda.core.contracts.Amount

class CurrencyUtils {

    companion object {
        private val registry = mapOf(
            "INR" to FiatCurrency.getInstance("INR"),
            "GBP" to FiatCurrency.getInstance("GBP"),
            "JPY" to FiatCurrency.getInstance("JPY"),
            "KITTY" to TokenType("KITTY", 0)
        )

        fun getInstance(currencyCode: String) : TokenType {
            return registry[currencyCode]?: throw IllegalArgumentException("$currencyCode does not exist.")
        }
    }
}

val INRTokenType = CurrencyUtils.getInstance("INR")
val Int.INR : Amount<TokenType> get() = amount(this, INRTokenType)
val Long.INR : Amount<TokenType> get() = amount(this, INRTokenType)
