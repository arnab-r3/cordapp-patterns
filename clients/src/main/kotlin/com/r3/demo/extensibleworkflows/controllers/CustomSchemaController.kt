package com.r3.demo.extensibleworkflows.controllers

import com.r3.demo.datadistribution.flows.MembershipFlows
import com.r3.demo.extensibleworkflows.CreateGroupDataAssociationAndLinkSchema
import com.r3.demo.extensibleworkflows.ManageGroupAwareSchemaBackedKVData
import com.r3.demo.template.forms.Forms
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
@RequestMapping("/api/schema") // The paths for HTTP requests are relative to this base path.
class GroupManagementController {

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

    @PostMapping("/network/{network-id}/member", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.TEXT_PLAIN_VALUE])
    private fun onBoardMember(
        @RequestBody businessNetworkBean: Forms.BusinessNetwork,
        @PathVariable("network-id") networkId: String
    ): CompletableFuture<String> {

        require(networkId.isNotBlank()) {"network id should not be empty"}
        require(businessNetworkBean.party != null) {"party name should not be empty"}

        logger.info("Request to onboard ${businessNetworkBean.party} on business network with " +
                "id : $networkId ")

        return proxy.startFlowDynamic(MembershipFlows.OnboardMyNetworkParticipant::class.java,
            networkId,
            businessNetworkBean.party
        ).returnValue.toCompletableFuture()

    }

    @PostMapping("/network/{network-id}/membership-request", produces = [MediaType.TEXT_PLAIN_VALUE])
    private fun requestMembership(
        @PathVariable("network-id") networkId: String
    ): CompletableFuture<String> {

        require(networkId.isNotBlank()) {"network id should not be empty"}

        val ourIdentity = proxy.nodeInfo().legalIdentities.single().name

        logger.info("Request for membership for party $ourIdentity on business network with " +
                "id : $networkId ")

        return proxy.startFlowDynamic(MembershipFlows.RequestMyNetworkMembership::class.java, networkId)
            .returnValue.toCompletableFuture()

    }


    @PostMapping("/network/membership-request/{membership-id}/approval", produces = [MediaType.TEXT_PLAIN_VALUE])
    private fun approveMembership(
        @PathVariable("membership-id") membershipId: String
    ): CompletableFuture<SignedTransaction> {

        require(membershipId.isNotBlank()) {"membership id should not be empty"}

        logger.info("Approving membership with id $membershipId on business network")

        return proxy.startFlowDynamic(MembershipFlows.ApproveMyNetworkMembership::class.java, membershipId)
            .returnValue.toCompletableFuture()

    }

    @PostMapping("/network/membership/{membership-id}/role/{role-name}", produces = [MediaType.TEXT_PLAIN_VALUE])
    private fun assignDataAdminRole(
        @PathVariable("membership-id") membershipId: String,
        @PathVariable("role-name") roleName: String
    ): CompletableFuture<SignedTransaction> {

        require(membershipId.isNotBlank()) {"membership id should not be empty"}

        logger.info("Assigning data admin role to membership with id $membershipId")

        return when (roleName) {
            "data-admin" -> proxy.startFlowDynamic(MembershipFlows.AssignDataAdminRoleFlow::class.java,
                membershipId
            ).returnValue.toCompletableFuture()
            "member" -> proxy.startFlowDynamic(MembershipFlows.AssignNetworkMemberRoleFlow::class.java,
                membershipId
            ).returnValue.toCompletableFuture()
            else -> {
                throw IllegalArgumentException("role name should be either of the following : [data-admin, member]")
            }
        }

    }


    @PostMapping("/network/{network-id}/group", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.TEXT_PLAIN_VALUE])
    private fun createBNGroup(
        @RequestBody businessNetworkBean: Forms.BusinessNetwork,
        @PathVariable("network-id") networkId: String
    ): CompletableFuture<String> {

        require(networkId.isNotBlank()) {"network id should not be empty"}
        require(businessNetworkBean.groupName.isNotBlank()) {"group name should not be empty"}
        require(businessNetworkBean.membershipIds.isNotEmpty()) {"membership ids should not be empty"}

        logger.info("Creating Group ${businessNetworkBean.groupName} " +
                "on network $networkId " +
                "with membership ids ${businessNetworkBean.membershipIds.joinToString()}")

        return proxy.startFlowDynamic(MembershipFlows.CreateMyGroupFlow::class.java,
            networkId,
            businessNetworkBean.groupName,
            businessNetworkBean.membershipIds.toList()
        ).returnValue.toCompletableFuture()

    }



    @PostMapping("/groups/data/schema", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.TEXT_PLAIN_VALUE])
    private fun createGroupAwareSchema(
        @RequestBody groupAwareSchema: Forms.GroupAwareSchema
    ): CompletableFuture<String> {
        logger.info("Request to create schema with " +
                "name : ${groupAwareSchema.schema.name} with " +
                "groups ${groupAwareSchema.groupIds.joinToString()}}")

        return proxy.startFlowDynamic(CreateGroupDataAssociationAndLinkSchema.Initiator::class.java,
            groupAwareSchema.groupIds,
            groupAwareSchema.schema
        ).returnValue.toCompletableFuture()
    }


    @PostMapping("/groups/data/{association-id}/schema/{schema-id}/kv",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.TEXT_PLAIN_VALUE])

    private fun createGroupAwareSchemaBackedKVData(
        @RequestBody groupAwareSchema: Forms.GroupAwareSchemaBackedKV,
        @PathVariable("schema-id") schemaId: String,
        @PathVariable("association-id") groupDataAssociationId: String
    ): CompletableFuture<String> {


        require(groupDataAssociationId.isNotBlank()) {"GroupDataAssociation id must be present"}
        require(schemaId.isNotBlank()) {"Schema ID should be present"}
        require(groupAwareSchema.data.isNotEmpty()) {"KV data should be present"}

        logger.info("Request to KV backed by Group Backed Schema with ID " +
                "name : $schemaId association " +
                "with Group Data identifier $groupDataAssociationId")

        return proxy.startFlowDynamic(ManageGroupAwareSchemaBackedKVData.Initiator::class.java,
            groupDataAssociationId,
            null,
            schemaId,
            groupAwareSchema.data,
            ManageGroupAwareSchemaBackedKVData.Operation.CREATE
        ).returnValue.toCompletableFuture()
    }

    @PutMapping("/groups/data/{association-id}/schema/{schema-id}/kv/{kv-id}",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.TEXT_PLAIN_VALUE])

    private fun updateGroupAwareSchemaBackedKVData(
        @RequestBody groupAwareSchema: Forms.GroupAwareSchemaBackedKV,
        @PathVariable("schema-id") schemaId: String,
        @PathVariable("kv-id") kvId: String,
        @PathVariable("association-id") groupDataAssociationId: String
    ): CompletableFuture<String> {


        require(groupDataAssociationId.isNotBlank()) {"GroupDataAssociation id must be present"}
        require(kvId.isNotBlank()) {"KV id must be present"}
        require(schemaId.isNotBlank()) {"Schema ID should be present"}
        require(groupAwareSchema.data.isNotEmpty()) {"KV data should be present"}

        logger.info("Request to KV backed by Group Backed Schema with ID " +
                "name : $schemaId association " +
                "with Group Data identifier $groupDataAssociationId")

        return proxy.startFlowDynamic(ManageGroupAwareSchemaBackedKVData.Initiator::class.java,
            groupDataAssociationId,
            kvId,
            schemaId,
            groupAwareSchema.data,
            ManageGroupAwareSchemaBackedKVData.Operation.UPDATE
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
