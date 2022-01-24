package com.r3.demo.crossnotaryswap.contracts

import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.demo.crossnotaryswap.states.LockState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireThat
import net.corda.core.crypto.TransactionSignature
import net.corda.core.crypto.toStringShort
import net.corda.core.transactions.LedgerTransaction

class LockContract : Contract {

    companion object {
        val contractId = this::class.java.enclosingClass.canonicalName!!
    }

    // TODO add more exhaustive checks in the contract

    override fun verify(tx: LedgerTransaction) {

        val ourCommand = tx.commandsOfType(LockCommand::class.java).single()
        val ourInputs = tx.inputsOfType(LockState::class.java)
        val ourOutputs = tx.outputsOfType(LockState::class.java)

        when (ourCommand.value) {
            is Encumber -> {

                require(ourInputs.isEmpty()) {
                    "To create an Encumbered Lock, there must be no input Lock states"
                }

                require(ourOutputs.size == 1) {
                    "To create an Encumbered Lock, there must be a single output Lock state"
                }

                require(tx.outputs.size > 1) {
                    "No non-lock states found to encumber"
                }

                val lockState = ourOutputs.single()
                require(lockState.participants.toSet().size == 2) {
                    "The lock state must have 2 different participants"
                }

                val signer = ourCommand.signers.single()

                require(signer == lockState.compositeKey) {
                    "The lock state must be signed by a composite key derived from an equal weighting of the two participants"
                }

                require(lockState.timeWindow.untilTime != null) {
                    "The time window on the lock state must have an untilTime specified"
                }

                val timeWindowUntil = tx.timeWindow?.untilTime
                require(
                    timeWindowUntil != null &&
                            timeWindowUntil >= lockState.timeWindow.untilTime
                ) {
                    "The time window on the lock state must have a greater untilTime than the lockState"
                }

                require(tx.outputs.single { it.contract == contractId }.encumbrance != null) {
                    "The lock state must be encumbered"
                }

                // TODO - the encumbrance logic in Corda should check for cyclic encumbrance dependency.
                //  need to check whether there are any other conditions we should check for
            }
            is Release -> {
                val signature = (ourCommand.value as Release).signature
                val ourState = tx.inRefsOfType(LockState::class.java).single().state.data
                require(signature.isValid(ourState.txIdWithNotaryMetadata.txId)) {
                    "Signature provided is not valid for encumbrance transaction"
                }
                require(signature.signatureMetadata == ourState.txIdWithNotaryMetadata.signatureMetadata) {
                    "Signature scheme information is not consistent with lock setup"
                }
                require(signature.by.toStringShort() == ourState.controllingNotary.owningKey.toStringShort()) {
                    "Signer of encumbrance transaction does not match controlling notary in Lock setup"
                }
            }
            is Revert -> {
                val lockState = tx.inRefsOfType(LockState::class.java).single().state.data
                val encumberedTxIssuer = lockState.creator
                val allowedOutputs: Set<AbstractToken> = tx.inputsOfType(AbstractToken::class.java).map {
                    if (it.holder.owningKey == lockState.compositeKey) it.withNewHolder(encumberedTxIssuer) else it
                }.toSet()
                val actualOutputs: Set<AbstractToken> = tx.outputsOfType(AbstractToken::class.java).toSet()
                requireThat {
                    "Token offer can be retired by its issuer" using ourCommand.signers.contains(encumberedTxIssuer.owningKey)
                    "Token offer can only be reverted in favor of the offer issuer" using (allowedOutputs == actualOutputs)
                    "Token Revert requires an open ended time window with from time not null" using
                            (tx.timeWindow != null && tx.timeWindow?.fromTime != null)
                    tx.timeWindow?.fromTime?.isAfter(lockState.timeWindow.untilTime)
                }
            }
        }
    }

    open class LockCommand : CommandData
    class Encumber : LockCommand()
    class Release(val signature: TransactionSignature) : LockCommand()
    class Revert : LockCommand()
}

