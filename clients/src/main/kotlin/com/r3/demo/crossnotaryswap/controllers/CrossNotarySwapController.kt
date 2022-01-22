package com.r3.demo.crossnotaryswap.controllers

import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.money.FiatCurrency
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import com.r3.demo.crossnotaryswap.flows.CrossNotarySwapDriverFlows
import com.r3.demo.crossnotaryswap.flows.CurrencyFlows
import com.r3.demo.crossnotaryswap.flows.InitiateExchangeFlows
import com.r3.demo.crossnotaryswap.flows.NFTFlows
import com.r3.demo.crossnotaryswap.flows.dto.KittyTokenDefinition
import com.r3.demo.crossnotaryswap.flows.dto.NFTTokenType
import com.r3.demo.crossnotaryswap.flows.dto.TokenDefinition
import com.r3.demo.crossnotaryswap.forms.CNSForms.*
import net.corda.core.CordaException
import net.corda.core.CordaRuntimeException
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.util.concurrent.CompletableFuture
import javax.servlet.http.HttpServletRequest

/**
 * Define your API endpoints here.
 */
@RestController
@RequestMapping("/api/cns") // The paths for HTTP requests are relative to this base path.
class CrossNotarySwapController {

    companion object {
        private val logger = LoggerFactory.getLogger(RestController::class.java)
    }

    @Autowired
    lateinit var partyAProxy: CordaRPCOps

    @Autowired
    lateinit var partyBProxy: CordaRPCOps

    @Autowired
    lateinit var partyCProxy: CordaRPCOps

    @Autowired
    lateinit var partyDProxy: CordaRPCOps

    @Autowired
    @Qualifier("partyAProxy")
    lateinit var proxy: CordaRPCOps


    @GetMapping("/node-info", produces = [MediaType.APPLICATION_JSON_VALUE])
    private fun getNodeInfo(): NodeInfo {
        return proxy.nodeInfo()
    }


    @PostMapping("/token-definition/non-fungible",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.TEXT_PLAIN_VALUE])
    private fun defineNonFungibleToken(
        @RequestBody nftDefinition: NFTDefinition
    ): CompletableFuture<String> = with(nftDefinition) {
        require(maintainers.isNotEmpty()) { "There should be at least one Non Fungible Token maintainer" }
        logger.info("Request to define Non Fungible token with properties: $properties")
        val tokenDefinition: TokenDefinition = when (nftDefinition.type) {
            NFTTokenType.KITTY -> {
                val requiredFields = setOf("kittyName")
                require(properties.keys.containsAll(requiredFields)) {
                    "Kitty token definition requires the following properties ${requiredFields.joinToString()}}"
                }
                KittyTokenDefinition(properties["kittyName"]!!, maintainers)
            }
            else -> throw IllegalArgumentException("Token type not found")
        }
        val txn = proxy.startFlowDynamic(NFTFlows.DefineNFTFlow::class.java,
            tokenDefinition
        ).returnValue.toCompletableFuture().getOrThrow()
        val tokenIdentifier = txn.coreTransaction.outRefsOfType<EvolvableTokenType>()
            .single().state.data.linearId
        successMessage("Defined an NFT of type $type with evolvable token id $tokenIdentifier.", txn)
    }


    @PostMapping("/token/non-fungible/issuance",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.TEXT_PLAIN_VALUE])
    private fun issueNFT(
        @RequestBody assetWithReceiverForm: AssetWithReceiverForm
    ): CompletableFuture<String> = with(assetWithReceiverForm) {
        require(tokenIdentifier.isNotBlank()) { "NFT asset request must have a token identifier" }
        val txn = proxy.startFlowDynamic(NFTFlows.IssueNFTFlow::class.java,
            tokenIdentifier, receiver.toParty()
        ).returnValue.toCompletableFuture().getOrThrow()
        val tokenIdentifier = txn.coreTransaction.outRefsOfType<NonFungibleToken>()
            .single().state.data.linearId
        successMessage("Issued an NFT token to $receiver with token identifier $tokenIdentifier.", txn)
    }

    @PostMapping("/token/fungible/issuance",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.TEXT_PLAIN_VALUE])
    private fun issueFungibleToken(
        @RequestBody assetWithReceiverForm: AssetWithReceiverForm
    ): CompletableFuture<String> =
        with(assetWithReceiverForm) {
            require(tokenIdentifier.isNotBlank()) { "Fungible asset request must have a token identifier" }
            require(amount != null) { "Fungible asset request must have an amount" }
            require(amount!! > BigDecimal.ZERO) { "Fungible asset request must have a positive amount" }
            val txn = proxy.startFlowDynamic(CurrencyFlows.IssueFiatCurrencyFlow::class.java,
                amount, tokenIdentifier, receiver.toParty()
            ).returnValue.toCompletableFuture().getOrThrow()
            successMessage("Issued $amount of $tokenIdentifier to $receiver.", txn)
        }

    @PostMapping("/token/fungible/move",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.TEXT_PLAIN_VALUE])
    private fun moveFungibleToken(
        @RequestBody assetWithReceiverForm: AssetWithReceiverForm
    ): CompletableFuture<String> =
        with(assetWithReceiverForm) {
            require(tokenIdentifier.isNotBlank()) { "Fungible asset request must have a token identifier" }
            require(amount != null) { "Fungible asset request must have an amount" }
            require(amount!! > BigDecimal.ZERO) { "Fungible asset request must have a positive amount" }
            val txn = proxy.startFlowDynamic(CurrencyFlows.MoveFiatTokensFlow::class.java,
                listOf(PartyAndAmount(receiver.toParty(), amount of FiatCurrency.getInstance(tokenIdentifier)))
            ).returnValue.toCompletableFuture().getOrThrow()
            successMessage("Moved $amount of $tokenIdentifier to $receiver.", txn)
        }

    @PostMapping("/token/non-fungible/move",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.TEXT_PLAIN_VALUE])
    private fun moveNonFungibleToken(
        @RequestBody assetWithReceiverForm: AssetWithReceiverForm
    ): CompletableFuture<String> =
        with(assetWithReceiverForm) {
            require(tokenIdentifier.isNotBlank()) { "Fungible asset request must have a token identifier" }
            val txn = proxy.startFlowDynamic(NFTFlows.MoveNFT::class.java,
                tokenIdentifier, receiver.toParty()
            ).returnValue.toCompletableFuture().getOrThrow()
            successMessage("Moved $tokenIdentifier to $receiver.", txn)
        }

    @PostMapping("/token/swap/request",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.TEXT_PLAIN_VALUE])
    private fun createCrossNotarySwapRequest(
        @RequestBody buyerAssetRequest: BuyerAssetRequestForm
    ): CompletableFuture<String> =
        with(buyerAssetRequest) {
            require(buyerAsset.tokenIdentifier.isNotBlank() && sellerAsset.tokenIdentifier.isNotBlank())
            { "Token Identifier for buyer and seller asset should not be blank" }
            val requestId = proxy.startFlowDynamic(InitiateExchangeFlows.ExchangeRequesterFlow::class.java,
                seller, sellerAsset.toAssetRequest(), buyerAsset.toAssetRequest()
            ).returnValue.toCompletableFuture().getOrThrow()
            CompletableFuture.completedFuture("Registered a cross notary swap request with id: $requestId with " +
                    "\ndetails $this")
        }


    @PostMapping("/token/swap/approval",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.TEXT_PLAIN_VALUE])
    private fun approveCrossNotarySwap(
        @RequestBody sellerApprovalForm: SellerApprovalForm
    ): CompletableFuture<String> =
        with(sellerApprovalForm) {
            require(requestId.isNotBlank()) { "The request for seller approval cannot be blank" }
            require(approved != null) { "approved flag for the seller cannot be blank" }
            proxy.startFlowDynamic(InitiateExchangeFlows.ExchangeResponderFlow::class.java,
                requestId, approved, rejectionReason
            ).returnValue.toCompletableFuture().getOrThrow()
            CompletableFuture.completedFuture("Approved a cross notary swap request with id: $requestId")
        }


    @PostMapping("/token/swap/execute/{request-id}",
        produces = [MediaType.TEXT_PLAIN_VALUE])
    private fun performCrossNotarySwap(
        @PathVariable("request-id") requestId: String
    ): CompletableFuture<String> =
        with(requestId) {
            require(isNotBlank()) { "The request for seller approval cannot be blank" }
            proxy.startFlowDynamic(CrossNotarySwapDriverFlows.BuyerDriverFlow::class.java, this)
                .returnValue
                .toCompletableFuture()
                .getOrThrow()
            CompletableFuture.completedFuture("Completed the swap with request id $requestId")
        }

    @GetMapping(value = ["balance/{token-type}/{token-identifier}"], produces = [MediaType.TEXT_PLAIN_VALUE])
    private fun getBalance(
        @PathVariable("token-identifier") tokenIdentifier: String,
        @PathVariable("token-type") tokenType: String
    ): ResponseEntity<String> = when (tokenType) {
        "non-fungible" -> {
            val linearQueryCriteria = QueryCriteria
                .LinearStateQueryCriteria(
                    linearId = listOf(UniqueIdentifier.fromString(tokenIdentifier)),
                    contractStateTypes = setOf(NonFungibleToken::class.java)
                )
            val nftStates =
                proxy.vaultQueryBy<NonFungibleToken>(linearQueryCriteria).states
                    .filter { it.state.data.holder == proxy.nodeInfo().legalIdentities.first() }
                    .joinToString(separator = "\n") {
                        "${it.state.data.linearId} of ${it.state.data.token.tokenType.tokenClass}" +
                                "(${it.state.data.token.tokenType.tokenIdentifier})"
                    }
            ResponseEntity.ok("Found Non Fungible State : $nftStates")
        }
        "fungible" -> {
            val amount = proxy.startFlowDynamic(CurrencyFlows.GetBalanceFlow::class.java, tokenIdentifier)
                .returnValue.toCompletableFuture().getOrThrow()
            ResponseEntity.ok("Balance of Fungible Token: $amount")
        }
        else -> throw IllegalArgumentException("Invalid type of token balance queried. Allowed: fungible, non-fungible")
    }


    @PostMapping(value = ["switch-party/{party}"], produces = [MediaType.TEXT_PLAIN_VALUE])
    private fun switchParty(@PathVariable party: String): ResponseEntity<String> {
        proxy = when (party) {
            "PartyA" -> partyAProxy
            "PartyB" -> partyBProxy
            "PartyC" -> partyCProxy
            "PartyD" -> partyDProxy
            else -> return ResponseEntity.badRequest().build()
        }
        return ResponseEntity.ok("Switched context to Party $party")
    }


    private fun successMessage(
        controllerMessage: String,
        signedTransaction: SignedTransaction
    ): CompletableFuture<String> =
        CompletableFuture.completedFuture("$controllerMessage\nTxId: ${signedTransaction.id} " +
                "on notary ${signedTransaction.coreTransaction.notary}")


    private fun String.toParty(): AbstractParty =
        proxy.wellKnownPartyFromX500Name(CordaX500Name.parse(this))!!

    @ExceptionHandler(IllegalArgumentException::class)
    private fun handleIllegalArgumentExceptions(
        @Suppress("unused") req: HttpServletRequest,
        ex: Exception
    ): ResponseEntity<String?> {
        return ResponseEntity.badRequest().body(ex.message)
    }

    @ExceptionHandler(CordaRuntimeException::class)
    private fun handleCordaRuntimeExceptions(
        @Suppress("unused") req: HttpServletRequest,
        ex: Exception
    ): ResponseEntity<String?> {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.message)
    }

    @ExceptionHandler(CordaException::class)
    private fun handleCordaExceptions(
        @Suppress("unused") req: HttpServletRequest,
        ex: Exception
    ): ResponseEntity<String?> {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.message)
    }
}
