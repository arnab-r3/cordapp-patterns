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
import java.math.BigDecimal

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
            serviceHub: ServiceHub
        ): TokenType {
            return if (registry[tokenIdentifier] != null) {
                registry[tokenIdentifier]!!
            } else {
                val queryCriteria = QueryCriteria
                    .LinearStateQueryCriteria(
                        linearId = listOf(UniqueIdentifier.fromString(tokenIdentifier)),
                        contractStateTypes = setOf(EvolvableTokenType::class.java)
                    )
                val nonFungibleTokenPages =
                    serviceHub.vaultService.queryBy(EvolvableTokenType::class.java, queryCriteria)
                require(nonFungibleTokenPages.states.isNotEmpty())
                { "Cannot find any token with identifier $tokenIdentifier" }
                nonFungibleTokenPages.states.single().state.data.toPointer(EvolvableTokenType::class.java)
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
val BigDecimal.INR: Amount<TokenType> get() = amount(this, INRTokenType)