package com.r3.demo.crossnotaryswap.flows.utils

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveNonFungibleTokens
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import com.r3.corda.lib.tokens.workflows.types.PartyAndToken
import com.r3.demo.crossnotaryswap.flows.dto.ExchangeAsset
import com.r3.demo.crossnotaryswap.flows.dto.ExchangeRequestDTO
import com.r3.demo.generic.flowFail
import net.corda.core.contracts.Amount
import net.corda.core.crypto.*
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AbstractParty
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction

/**
 * Get all transaction's dependencies from the transaction's inputs and references.
 */
@Suspendable
fun WireTransaction.getDependencies(): Set<SecureHash> {
    return this.inputs.map { it.txhash }.toSet() + this.references.map { it.txhash }.toSet()
}

// This function has been brought across from the reissuance library. It has
// been created to reimplement the merkle tree calculation that is performed by
// the wire transaction class. Implementing it ourselves means that we don't have to
// rely on the implementation detail of the wire transaction (which could in theory use a
// stored value if it wanted to)
@Suspendable
fun WireTransaction.generateWireTransactionMerkleTree(): MerkleTree {
    val availableComponentNonces: Map<Int, List<SecureHash>> by lazy {
        this.componentGroups.map { Pair(it.groupIndex, it.components.mapIndexed {
                internalIndex, internalIt ->
            componentHash(internalIt, this.privacySalt, it.groupIndex, internalIndex) })
        }.toMap()
    }

    val availableComponentHashes = this.componentGroups.map {
        Pair(it.groupIndex, it.components.mapIndexed {
                internalIndex, internalIt ->
            componentHash(availableComponentNonces[it.groupIndex]!![internalIndex], internalIt) })
    }.toMap()

    val groupsMerkleRoots: Map<Int, SecureHash> by lazy {
        availableComponentHashes.map {
            Pair(it.key, MerkleTree.getMerkleTree(it.value).hash)
        }.toMap()
    }
    val groupHashes: List<SecureHash> by lazy {
        val listOfLeaves = mutableListOf<SecureHash>()
        // Even if empty and not used, we should at least send oneHashes for each known
        // or received but unknown (thus, bigger than known ordinal) component groups.
        for (i in 0..this.componentGroups.map { it.groupIndex }.max()!!) {
            val root = groupsMerkleRoots[i] ?: SecureHash.allOnesHash
            listOfLeaves.add(root)
        }
        listOfLeaves
    }
    return MerkleTree.getMerkleTree(groupHashes)
}

/**
 * Return a [SignatureMetadata] instance for the notary on this [WireTransaction].
 * @param serviceHub Corda node services.
 * @return [SignatureMetadata] for the transaction's notary.
 * @throws [IllegalArgumentException] if the notary info cannot be retrieved from the network map cache.
 */
@Suspendable
fun WireTransaction.getSignatureMetadataForNotary(serviceHub: ServiceHub) : SignatureMetadata {
    val notary = this.notary!!
    val notaryInfo = serviceHub.networkMapCache.getNodeByLegalIdentity(notary)
        ?: throw IllegalArgumentException("Unable to retrieve notaryInfo for notary: $notary")

    return SignatureMetadata(
        notaryInfo.platformVersion,
        Crypto.findSignatureScheme(notary.owningKey).schemeNumberID
    )
}

@Suspendable
fun FlowLogic<*>.addOurAssetToTransactionBuilder(
    transactionBuilder: TransactionBuilder,
    exchangeRequestDto: ExchangeRequestDTO,
    isBuyer: Boolean
): TransactionBuilder {
    val ourAsset: ExchangeAsset<out TokenType>
    val receivingParty: AbstractParty
    val senderIdentity: AbstractParty
    val senderTokenType: TokenType
    val senderTokenAmount: Amount<out TokenType>?
    if (isBuyer) {
        ourAsset = exchangeRequestDto.buyerAsset
        receivingParty = exchangeRequestDto.seller
        senderIdentity = ourIdentity
        senderTokenType = exchangeRequestDto.buyerAsset.tokenType
        senderTokenAmount = exchangeRequestDto.buyerAsset.amount
    } else {
        ourAsset = exchangeRequestDto.sellerAsset
        receivingParty = exchangeRequestDto.buyer
        senderIdentity = ourIdentity
        senderTokenType = exchangeRequestDto.sellerAsset.tokenType
        senderTokenAmount = exchangeRequestDto.sellerAsset.amount
    }

    return when {
        // add the move tokens if it is a nft
        ourAsset.tokenType.isRegularTokenType() -> {
            val amount = senderTokenAmount!!.quantity
            val partyAndAmount = PartyAndAmount(receivingParty, amount of senderTokenType)
            addMoveFungibleTokens(
                transactionBuilder = transactionBuilder,
                serviceHub = serviceHub,
                partiesAndAmounts = listOf(partyAndAmount),
                changeHolder = senderIdentity,
                queryCriteria = null)
        }
        // add the move tokens if it is a fungible token
        ourAsset.tokenType.isPointer() -> {
            val partyAndToken = PartyAndToken(receivingParty, senderTokenType)
            addMoveNonFungibleTokens(
                transactionBuilder = transactionBuilder,
                serviceHub = serviceHub,
                partyAndToken = partyAndToken)
        }
        else -> flowFail("Cannot support move operation for a token which is neither a Regular token or a NFT")
    }
}
