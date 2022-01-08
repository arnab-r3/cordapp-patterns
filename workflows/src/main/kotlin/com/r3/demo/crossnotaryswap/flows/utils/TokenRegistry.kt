package com.r3.demo.crossnotaryswap.flows.utils

import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.amount
import com.r3.corda.lib.tokens.money.FiatCurrency
import com.r3.demo.crossnotaryswap.states.KittyToken
import com.r3.demo.generic.argFail
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.vault.QueryCriteria

class TokenRegistry {

    companion object {
        private val registry = mapOf(
            "INR" to FiatCurrency.getInstance("INR"),
            "GBP" to FiatCurrency.getInstance("GBP"),
            "JPY" to FiatCurrency.getInstance("JPY")
        )

        private val currencyClassMap = mapOf<Class<*>, String>(
            KittyToken::class.java to "KITTY"
        )

        fun getInstance(tokenIdentifier: String) =
            registry[tokenIdentifier]
                ?: argFail("Cannot find currency type with token identifier: $tokenIdentifier")

        fun getInstance(
            tokenIdentifier: String,
            serviceHub: ServiceHub,
            tokenClass: Class<out EvolvableTokenType>?
        ): TokenType {
            return when {
                tokenClass != null -> {
                    val queryCriteria = QueryCriteria
                        .LinearStateQueryCriteria(
                            linearId = listOf(UniqueIdentifier.fromString(tokenIdentifier)),
                            contractStateTypes = setOf(tokenClass)
                        )

                    val evolvableTokens = serviceHub.vaultService.queryBy(tokenClass, queryCriteria)
                    require(evolvableTokens.states.isNotEmpty())
                        { "Cannot find any NFT with identifier $tokenIdentifier and class type $tokenClass" }
                    evolvableTokens.states.single().state.data.toPointer(tokenClass)

                }
                else -> registry[tokenIdentifier]
                    ?: argFail("Cannot find currency type with token identifier: $tokenIdentifier")
            }
        }

        fun getTokenAbbreviation(clazz: Class<*>): String {
            return currencyClassMap[clazz] ?: throw IllegalArgumentException("$clazz does not exist.")
        }
    }
}

val INRTokenType = FiatCurrency.getInstance("INR")
val Int.INR: Amount<TokenType> get() = amount(this, INRTokenType)
val Long.INR: Amount<TokenType> get() = amount(this, INRTokenType)


