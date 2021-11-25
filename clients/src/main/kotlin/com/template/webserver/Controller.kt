package com.template.webserver

import com.r3.demo.datadistribution.flows.MembershipFlows
import com.r3.demo.extensibleworkflows.CreateGroupAwareSchema
import com.template.forms.Forms
import net.corda.bn.states.MembershipState
import net.corda.core.CordaException
import net.corda.core.CordaRuntimeException
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.NodeInfo
import net.corda.core.transactions.SignedTransaction
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.concurrent.CompletableFuture
import javax.servlet.http.HttpServletRequest

/**
 * Define your API endpoints here.
 */
@RestController
@RequestMapping("/api") // The paths for HTTP requests are relative to this base path.
class GroupManagementController() {

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
    @Qualifier("partyAProxy")
    lateinit var proxy: CordaRPCOps


    @GetMapping("/node-info",produces = [MediaType.APPLICATION_JSON_VALUE])
    private fun getNodeInfo() : NodeInfo {
        return proxy.nodeInfo()
    }


    @GetMapping("/network/memberships",produces = [MediaType.APPLICATION_JSON_VALUE])
    private fun getMemberships() : List<MembershipState> {
        return proxy.vaultQuery(MembershipState::class.java).states.map { it.state.data }
    }

    @PostMapping("/network", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.TEXT_PLAIN_VALUE])
    private fun createBusinessNetwork(
        @RequestBody businessNetworkBean: Forms.BusinessNetwork
    ): CompletableFuture<String> {
        require(businessNetworkBean.groupName.isNotBlank()) {"default group name should not be empty"}
        logger.info("Request to create business network with " +
                "default group name : ${businessNetworkBean.groupName} ")

        return proxy.startFlowDynamic(MembershipFlows.CreateMyNetworkFlow::class.java,
            businessNetworkBean.groupName
        ).returnValue.toCompletableFuture()
    }

    @PostMapping("/network/member", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.TEXT_PLAIN_VALUE])
    private fun onBoardMember(
        @RequestBody businessNetworkBean: Forms.BusinessNetwork
    ): CompletableFuture<String> {

        require(businessNetworkBean.networkId.isNotBlank()) {"network id should not be empty"}
        require(businessNetworkBean.party != null) {"party name should not be empty"}

        logger.info("Request to onboard ${businessNetworkBean.party} on business network with " +
                "id : ${businessNetworkBean.networkId} ")

        return proxy.startFlowDynamic(MembershipFlows.OnboardMyNetworkParticipant::class.java,
            businessNetworkBean.networkId,
            businessNetworkBean.party
        ).returnValue.toCompletableFuture()

    }

    @PostMapping("/network/membership-request", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.TEXT_PLAIN_VALUE])
    private fun requestMembership(
        @RequestBody businessNetworkBean: Forms.BusinessNetwork
    ): CompletableFuture<String> {

        require(businessNetworkBean.networkId.isNotBlank()) {"network id should not be empty"}

        val ourIdentity = proxy.nodeInfo().legalIdentities.single().name

        logger.info("Request for membership for party $ourIdentity on business network with " +
                "id : ${businessNetworkBean.networkId} ")

        return proxy.startFlowDynamic(MembershipFlows.RequestMyNetworkMembership::class.java,
            businessNetworkBean.networkId
        ).returnValue.toCompletableFuture()

    }


    @PostMapping("/network/membership-request/approval", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.TEXT_PLAIN_VALUE])
    private fun approveMembership(
        @RequestBody businessNetworkBean: Forms.BusinessNetwork
    ): CompletableFuture<SignedTransaction> {

        require(businessNetworkBean.membershipId.isNotBlank()) {"membership id should not be empty"}

        logger.info("Approving membership with id ${businessNetworkBean.membershipId} on business network with " +
                "id : ${businessNetworkBean.networkId} ")

        return proxy.startFlowDynamic(MembershipFlows.ApproveMyNetworkMembership::class.java,
            businessNetworkBean.membershipId
        ).returnValue.toCompletableFuture()

    }

    @PostMapping("/network/membership/role/data-admin", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.TEXT_PLAIN_VALUE])
    private fun assignDataAdminRole(
        @RequestBody businessNetworkBean: Forms.BusinessNetwork
    ): CompletableFuture<SignedTransaction> {

        require(businessNetworkBean.membershipId.isNotBlank()) {"membership id should not be empty"}

        logger.info("Assigning data admin role to membership with id ${businessNetworkBean.membershipId}")

        return proxy.startFlowDynamic(MembershipFlows.AssignDataAdminRoleFlow::class.java,
            businessNetworkBean.membershipId
        ).returnValue.toCompletableFuture()

    }


    @PostMapping("/network/group", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.TEXT_PLAIN_VALUE])
    private fun createBNGroup(
        @RequestBody businessNetworkBean: Forms.BusinessNetwork
    ): CompletableFuture<String> {

        require(businessNetworkBean.networkId.isNotBlank()) {"network id should not be empty"}
        require(businessNetworkBean.groupName.isNotBlank()) {"group name should not be empty"}
        require(businessNetworkBean.membershipIds.isNotEmpty()) {"membership ids should not be empty"}

        logger.info("Creating Group ${businessNetworkBean.groupName} " +
                "on network ${businessNetworkBean.networkId} " +
                "with membership ids ${businessNetworkBean.membershipIds.joinToString()}")

        return proxy.startFlowDynamic(MembershipFlows.CreateMyGroupFlow::class.java,
            businessNetworkBean.networkId,
            businessNetworkBean.groupName,
            businessNetworkBean.membershipIds.toList()
        ).returnValue.toCompletableFuture()

    }



    @PostMapping("/group/data/schema", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.TEXT_PLAIN_VALUE])
    private fun createGroupAwareSchema(
        @RequestBody groupAwareSchema: Forms.GroupAwareSchema
    ): CompletableFuture<String> {
        logger.info("Request to create schema with " +
                "name : ${groupAwareSchema.schema.name} with " +
                "groups ${groupAwareSchema.groupIds.joinToString()}}")

        return proxy.startFlowDynamic(CreateGroupAwareSchema.Initiator::class.java,
            groupAwareSchema.groupIds,
            groupAwareSchema.schema
        ).returnValue.toCompletableFuture()
    }


    @PostMapping(value = ["switch-party/{party}"], produces = [MediaType.TEXT_PLAIN_VALUE])
    private fun switchParty(@PathVariable party: String): ResponseEntity<String> {
        proxy = when (party) {
            "PartyA" -> partyAProxy
            "PartyB" -> partyBProxy
            "PartyC" -> partyCProxy
            else -> return ResponseEntity.badRequest().build()
        }
        return ResponseEntity.ok("Switched context to Party $party")
    }



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
