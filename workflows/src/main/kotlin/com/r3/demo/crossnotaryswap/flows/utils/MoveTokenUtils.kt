package com.r3.demo.crossnotaryswap.flows.utils

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.commands.MoveTokenCommand
import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.selection.TokenQueryBy
import com.r3.corda.lib.tokens.selection.database.selector.DatabaseTokenSelection
import com.r3.corda.lib.tokens.workflows.internal.selection.generateMoveNonFungible
import com.r3.corda.lib.tokens.workflows.types.PartyAndToken
import com.r3.corda.lib.tokens.workflows.utilities.addTokenTypeJar
import com.r3.demo.crossnotaryswap.contracts.LockContract
import com.r3.demo.crossnotaryswap.flows.dto.ExchangeAsset
import com.r3.demo.crossnotaryswap.flows.dto.ExchangeRequestDTO
import com.r3.demo.crossnotaryswap.states.LockState
import com.r3.demo.generic.flowFail
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.CompositeKey
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AbstractParty
import net.corda.core.internal.requiredContractClassName
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import java.math.BigDecimal
import java.security.PublicKey


/**
 * For [NonFungibleToken], adds token move to the transaction, the [tokenIdentifier] and [tokenClass] identify
 * the token to be holder.
 */
fun TransactionBuilder.addMoveToken(
    serviceHub: ServiceHub,
    tokenIdentifier: String,
    tokenClass: Class<out EvolvableTokenType>,
    holder: AbstractParty,
    additionalKeys: List<PublicKey>,
    lockState: LockState?
): TransactionBuilder {

    val tokenType = TokenRegistry.getInstance(
        tokenIdentifier = tokenIdentifier,
        serviceHub = serviceHub)
    val partyAndToken = PartyAndToken(
        party = holder,
        token = tokenType)
    val (input, output) = generateMoveNonFungible(
        partyAndToken = partyAndToken,
        vaultService = serviceHub.vaultService,
        queryCriteria = null)
    return this.addMoveTokens(inputs = listOf(input),
        outputs = listOf(output),
        additionalKeys = additionalKeys,
        lockState = lockState)
}

/**
 * For [FungibleToken], adds token move to transaction. [amount] and [holder] parameters specify which party should receive the amount of
 * token, with possible change paid to [changeHolder].
 * Note: For now this method always uses database token selection, to use in memory one, use [addMoveTokens] with
 * already selected input and output states. []
 */
@Suspendable
fun TransactionBuilder.addMoveTokens(
    serviceHub: ServiceHub,
    amount: Amount<TokenType>, // encumbered amount
    holder: AbstractParty, // composite key
    changeHolder: AbstractParty,
    additionalKeys: List<PublicKey>,
    lockState: LockState? = null
): TransactionBuilder {
    val selector = DatabaseTokenSelection(serviceHub)
    val (inputs, outputs) = selector.generateMove(
        listOf(Pair(holder, amount)),
        changeHolder,
        TokenQueryBy(),
        this.lockId
    )
    return addMoveTokens(
        inputs = inputs,
        outputs = outputs,
        additionalKeys = additionalKeys,
        lockState = lockState
    )
}


/**
 * Adds a set of token moves to a transaction using specific inputs and outputs. If a [lockState] is passed in, it will
 * be used as an encumbrance for any token that is being moved to a new holder where the holder is a composite key.
 * The output tokens are added to the transaction as encumbrances in cyclic fashion, such that none of
 * the output tokens can be spent without the entirety of the output states added in the transaction.
 */
@Suspendable
fun TransactionBuilder.addMoveTokens(
    inputs: List<StateAndRef<AbstractToken>>,
    outputs: List<AbstractToken>,
    additionalKeys: List<PublicKey>,
    lockState: LockState? = null
): TransactionBuilder {
    val outputGroups: Map<IssuedTokenType, List<AbstractToken>> = outputs.groupBy { it.issuedTokenType }
    val inputGroups: Map<IssuedTokenType, List<StateAndRef<AbstractToken>>> = inputs.groupBy {
        it.state.data.issuedTokenType
    }

    check(outputGroups.keys == inputGroups.keys) {
        "Input and output token types must correspond to each other when moving tokensToIssue"
    }

    var previousEncumbrance = outputs.size

    this.apply {
        // Add a notary to the transaction.
        notary = inputs.map { it.state.notary }.toSet().single()
        outputGroups.forEach { (issuedTokenType: IssuedTokenType, outputStates: List<AbstractToken>) ->
            val inputGroup = inputGroups[issuedTokenType]
                ?: throw IllegalArgumentException("No corresponding inputs for the outputs issued token type: $issuedTokenType")
            val keys = inputGroup.map { it.state.data.holder.owningKey }.distinct()

            var inputStartingIdx = inputStates().size
            var outputStartingIdx = outputStates().size

            val inputIdx = inputGroup.map {
                addInputState(it)
                inputStartingIdx++
            }

            val outputIdx = outputStates.map {
                if (lockState != null && it.holder.owningKey is CompositeKey) {
                    addOutputState(it, it.requiredContractClassName!!, notary!!, previousEncumbrance)
                    previousEncumbrance = outputStartingIdx
                } else {
                    addOutputState(it)
                }
                outputStartingIdx++
            }

            addCommand(
                MoveTokenCommand(issuedTokenType, inputs = inputIdx, outputs = outputIdx),
                keys + additionalKeys
            )
        }

        if (lockState != null) {
            addOutputState(
                state = lockState,
                contract = LockContract.contractId,
                notary = notary!!,
                encumbrance = previousEncumbrance
            )
            addCommand(LockContract.Encumber(), lockState.compositeKey)
        }
    }
    // add the token type jar to ensure that same contract is used for all transactions of this token
    addTokenTypeJar(inputs.map { it.state.data } + outputs, this)

    return this
}


/**
 * Verify the shared unsigned [WireTransaction] against the offer details from the buyer
 * present in the [ExchangeRequestDTO]
 * @param unsignedWireTx shared by the buyer
 * @param exchangeAsset to be verified against present in the [ExchangeRequestDTO]
 */
@Suspendable
fun FlowLogic<*>.verifySharedTransactionAgainstExchangeRequest(
    exchangeAsset: ExchangeAsset<out TokenType>,
    unsignedWireTx: WireTransaction
) {
    val sentAsset = unsignedWireTx.outputStates
    if (exchangeAsset.amount != null && exchangeAsset.tokenType.isRegularTokenType()) {
        val sentAmount = sentAsset
            .map { uncheckedCast<ContractState, FungibleToken>(it) }
            .filter {
                it.holder == ourIdentity
            }.fold(BigDecimal.ZERO) { acc, fungibleToken ->
                fungibleToken.amount.toDecimal() + acc
            }
        if (sentAmount != exchangeAsset.amount.toDecimal())
            flowFail("The shared unsigned transaction does not send the agreed amount of " +
                    "shared tokens to $ourIdentity as agreed in Exchange Request; " +
                    "Shared in unsigned tx: $sentAmount, Agreed: ${exchangeAsset.amount}")
    } else if (exchangeAsset.tokenType.isPointer()) {
        val sentNFTAsset = sentAsset
            .map { uncheckedCast<ContractState, NonFungibleToken>(it) }
            .filter { it.holder == ourIdentity }
            .filter { it.token.tokenIdentifier == exchangeAsset.tokenType.tokenIdentifier }
        if (sentNFTAsset.isEmpty())
            flowFail("The shared unsigned transaction does not transfer " +
                    "the token with id: ${exchangeAsset.tokenType.tokenIdentifier} to $ourIdentity" +
                    "as agreed in the Exchange Request")
    }
}
