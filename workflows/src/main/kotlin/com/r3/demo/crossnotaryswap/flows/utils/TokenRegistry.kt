package com.r3.demo.crossnotaryswap.flows.utils

import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.amount
import com.r3.corda.lib.tokens.money.FiatCurrency
import com.r3.demo.crossnotaryswap.states.KittyToken
import net.corda.core.contracts.Amount

class TokenRegistry {

    companion object {
        private val registry = mapOf(
            "INR" to FiatCurrency.getInstance("INR"),
            "GBP" to FiatCurrency.getInstance("GBP"),
            "JPY" to FiatCurrency.getInstance("JPY"),
            "KITTY" to TokenType("KITTY", 0)
        )

        private val currencyClassMap = mapOf<Class<*>, String>(
            KittyToken::class.java to "KITTY"
        )

        fun getInstance(tokenIdentifier: String) : TokenType {
            return registry[tokenIdentifier]?: throw IllegalArgumentException("$tokenIdentifier does not exist.")
        }
        fun getTokenIdentifier(clazz: Class<*>) : String{
            return currencyClassMap[clazz]?:throw IllegalArgumentException("$clazz does not exist.")
        }
    }
}

val INRTokenType = TokenRegistry.getInstance("INR")
val Int.INR : Amount<TokenType> get() = amount(this, INRTokenType)
val Long.INR : Amount<TokenType> get() = amount(this, INRTokenType)


