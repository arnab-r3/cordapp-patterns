package com.r3.demo.datadistribution.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.demo.common.canDistributeData
import com.r3.demo.common.canManageData
import com.r3.demo.datadistribution.contracts.GroupDataAssociationContract
import com.r3.demo.datadistribution.contracts.GroupDataAssociationState
import com.r3.demo.generic.argFail
import com.r3.demo.generic.flowFail
import com.r3.demo.generic.getDefaultNotary
import com.r3.demo.generic.linearPointer
import com.template.flows.CollectSignaturesAndFinalizeTransactionFlow
import net.corda.bn.flows.BNService
import net.corda.bn.flows.MembershipManagementFlow
import net.corda.bn.states.GroupState
import net.corda.bn.states.MembershipState
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StatePointer
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.NonEmptySet
import net.corda.core.utilities.ProgressTracker
import java.util.*


abstract class GroupDataManagementFlow<T> : MembershipManagementFlow<T>() {


    /**
     * Fetch the group participants
     * @param groupIds to fetch the participant list from
     */
    private fun transformGroupIdsToMembershipStates(
        groupIds: Set<String>,
        filterPredicate: (MembershipState) -> Boolean
    ): Set<MembershipState> {

        val bnService = serviceHub.cordaService(BNService::class.java)
        return groupIds.flatMap { groupId ->

            // risk us not having the group information, if we want to fetch the details of the group,
            // assume that we are a part of it
            val groupState = bnService.getBusinessNetworkGroup(
                UniqueIdentifier.fromString(groupId)
            )?.state?.data ?: flowFail("Group state $groupId not available or $ourIdentity is not a part of it")


            // TODO revisit when we have approaches to distribute group metadata
            // now assume the BNO has all the three rights below to ensure he gets the MembershipState information
            authorise(groupState.networkId, bnService)
            { it.canModifyGroups() && it.canDistributeData() && it.canManageData() }

            groupState.participants.mapNotNull { party ->
                val membershipState = bnService.getMembership(groupState.networkId, party)?.state?.data
                    ?: flowFail("MembershipState information for $party on network ${groupState.networkId} not found on $ourIdentity node")

//                    check(membershipState != null) {"Membership information not available for party $party!"}
                if (filterPredicate.invoke(membershipState)) return@mapNotNull membershipState else return@mapNotNull null
            }

        }.toSet()
    }

    /**
     * Fetch the group participants
     * @param groupIds to fetch the participant list from
     */
    fun getGroupsParticipants(
        groupIds: Set<String>,
        filterPredicate: (MembershipState) -> Boolean = { true }
    ): Set<Party> = transformGroupIdsToMembershipStates(groupIds, filterPredicate)
        .map { it.identity.cordaIdentity }.toSet()



    /**
     * Fetch Group Data state with the group data association state identifier
     * @param groupDataAssociationStateIdentifier stored as [UniqueIdentifier]
     */
    fun getGroupDataState(groupDataAssociationStateIdentifier: String): StateAndRef<GroupDataAssociationState> {

        val linearStateQueryCriteria = QueryCriteria.LinearStateQueryCriteria()
            .withUuid(listOf(UUID.fromString(groupDataAssociationStateIdentifier)))
            .withStatus(Vault.StateStatus.UNCONSUMED)
            .withRelevancyStatus(Vault.RelevancyStatus.ALL)

        val queryResult = serviceHub
            .vaultService
            .queryBy<GroupDataAssociationState>(linearStateQueryCriteria)
            .states
        require(queryResult.size == 1)
        { "Could not find GroupDataAssociationState with id: $groupDataAssociationStateIdentifier" }

        return queryResult.single()

    }

    /**
     * Fetch associated states that were committed in the same transaction as the [GroupDataAssociationState]
     * @param groupDataAssociationStateIdentifier stored as [UniqueIdentifier]
     */
    fun getGroupDataAssociatedStates(
        groupDataAssociationStateIdentifier: String,
        filterPredicate: (ContractState) -> Boolean = { true }
    ): List<StateAndRef<ContractState>> {

        val groupDataState = getGroupDataState(groupDataAssociationStateIdentifier)

        return groupDataState.state.data.data.map {
            it.resolve(serviceHub)
        }.filter { filterPredicate(it.state.data) }
    }

    fun getGroupParticipantsWithManagementAndDistributionRights(groupIds: Set<String>): Pair<Set<Party>, Set<Party>> {
        val groupParticipantsWithDataManagementRights = getGroupsParticipants(groupIds)
        {
            it.canManageData() && it.canDistributeData()
        }

        val groupParticipantsWithDataDistributionRights = getGroupsParticipants(groupIds)
        {
            it.canDistributeData()
        }

        return groupParticipantsWithDataManagementRights to groupParticipantsWithDataDistributionRights
    }
}

/**
 * Use these flows when associating and distributing relevant states to parties. The capabilities include creating an
 * association and including a set of states that will be distributed along with the association. One can also update
 * the association by including new elements that will be distributed or expanding the horizon of participants who
 * are included in the association.
 */
object GroupDataAssociationFlows {


    /**
     * Create Data item by the data administrator and distribute to the groups of participants.
     * @param referredStates containing references any subclass of [StatePointer] that we want to refer in this transaction.
     * All of these referred states will be distributed along with the transaction to the group.
     * Pass empty if no state data is available
     * @param metadataMap KV pairs, can act as tags
     * @param groupIds relevant groups
     */
    @InitiatingFlow
    @StartableByRPC
    class CreateNewAssociationState(
        private val referredStates: Set<StatePointer<out ContractState>>,
        private val metadataMap: Map<String, String>,
        private val groupIds: Set<String>
    ) : GroupDataManagementFlow<String>() {

        companion object {
            object FETCHING_GROUP_DETAILS : ProgressTracker.Step("Fetching Group Details")
//            {
//                override fun childProgressTracker(): ProgressTracker = GroupDataManagementFlow.tracker()
//            }

            object BUILDING_THE_TX : ProgressTracker.Step("Building transaction.")
            object VERIFYING_THE_TX : ProgressTracker.Step("Verifying transaction.")
            object COLLECTING_SIGS_AND_FINALITY : ProgressTracker.Step("Collecting Signatures & Executing Finality") {
                override fun childProgressTracker() = CollectSignaturesAndFinalizeTransactionFlow.tracker()
            }

            object DISTRIBUTING_GROUP_DATA :
                ProgressTracker.Step("Distributing Group Association data to other participants") {
                override fun childProgressTracker(): ProgressTracker =
                    MembershipBroadcastFlows.DistributeTransactionsToGroupFlow.tracker()
            }

            fun tracker() = ProgressTracker(FETCHING_GROUP_DETAILS,
                BUILDING_THE_TX,
                VERIFYING_THE_TX,
                COLLECTING_SIGS_AND_FINALITY,
                DISTRIBUTING_GROUP_DATA)
        }

        override val progressTracker = tracker()


        @Suspendable
        override fun call(): String {

            // if the groupIds are not empty then populate the participants with the Data Distribution permissions,
            // otherwise add ourself
            progressTracker.currentStep = FETCHING_GROUP_DETAILS

            val (
                groupParticipantsWithDataManagementRights,
                groupParticipantsWithDataDistributionRights
            ) =
                getGroupParticipantsWithManagementAndDistributionRights(groupIds)

            progressTracker.currentStep = BUILDING_THE_TX
            val groupLinearPointers =
                groupIds.map { linearPointer(it, GroupState::class.java) }.toSet()

            val outputState = GroupDataAssociationState(
                metaData = metadataMap,   // the tagged data we want to store
                data = referredStates,
                associatedGroupStates = groupLinearPointers,
                participants = groupParticipantsWithDataManagementRights.toList())

            val signers = groupParticipantsWithDataManagementRights + ourIdentity
            val signerKeys = signers.map { it.owningKey }

            val txBuilder = TransactionBuilder(getDefaultNotary(serviceHub))
                .addOutputState(outputState)
                .addCommand(GroupDataAssociationContract.Commands.CreateAssociation(), signerKeys)

            progressTracker.currentStep = VERIFYING_THE_TX
            txBuilder.verify(serviceHub)

            progressTracker.currentStep = COLLECTING_SIGS_AND_FINALITY
            val finalizedTx = subFlow(CollectSignaturesAndFinalizeTransactionFlow(
                builder = txBuilder,
                myOptionalKeys = null,
                signers = signers,
                participants = (groupParticipantsWithDataManagementRights + groupParticipantsWithDataDistributionRights).toSet()
            ))


            progressTracker.currentStep = DISTRIBUTING_GROUP_DATA
            // distribute the transaction to all group members except the group data participants, because they already have the transaction
            groupIds.forEach { groupId ->
                subFlow(MembershipBroadcastFlows.DistributeTransactionsToGroupFlow(
                    signedTransactions = listOf(finalizedTx),    // this finalized transaction contains all of the embedded states
                    stateAndRefs = referredStates.map { it.resolve(serviceHub) }.toSet(),
                    groupId = groupId)
                { party -> // don't send it again to the data admins of the data distributors
                    party !in (groupParticipantsWithDataManagementRights + groupParticipantsWithDataDistributionRights)
                })
            }

            return "Data with id: ${outputState.linearId} created and distributed to groups: ${groupIds.joinToString()}, TxId: ${finalizedTx.id}"
        }
    }

    /**
     * Flow to add new groups to the association, note that it does not remove the existing group,
     * it merges the groups instead
     *
     * @param dataIdentifier the [GroupDataAssociationState] identifier
     * @param newGroupIds the new group identifiers
     */
    @InitiatingFlow
    @StartableByRPC
    @Suppress("unused")
    class UpdateAssociationGroups(
        private val dataIdentifier: String,
        private val newGroupIds: Set<String>
    ) : GroupDataManagementFlow<String>() {

        companion object {
            object FETCHING_GROUP_DETAILS : ProgressTracker.Step("Fetching Group Details")
            object BUILDING_THE_TX : ProgressTracker.Step("Building transaction.")
            object LINKING_TX : ProgressTracker.Step("Linking previous transactions")
            object VERIFYING_THE_TX : ProgressTracker.Step("Verifying transaction.")
            object COLLECTING_SIGS_AND_FINALITY : ProgressTracker.Step("Collecting Signatures & Executing Finality") {
                override fun childProgressTracker() = CollectSignaturesAndFinalizeTransactionFlow.tracker()
            }

            object DISTRIBUTING_GROUP_DATA :
                ProgressTracker.Step("Distributing Group Association data to other participants") {
                override fun childProgressTracker(): ProgressTracker =
                    MembershipBroadcastFlows.DistributeTransactionsToGroupFlow.tracker()
            }

            fun tracker() = ProgressTracker(FETCHING_GROUP_DETAILS,
                BUILDING_THE_TX,
                VERIFYING_THE_TX,
                LINKING_TX,
                COLLECTING_SIGS_AND_FINALITY,
                DISTRIBUTING_GROUP_DATA)
        }

        override val progressTracker = tracker()


        @Suspendable
        override fun call(): String {

            val bnService = serviceHub.cordaService(BNService::class.java)

            val groupDataAssociationStateRef = getGroupDataState(dataIdentifier)

            val groupIds =
                groupDataAssociationStateRef.state.data.associatedGroupStates.toSet().map { linearPointer ->

                    // get the group id from the associated group states
                    val groupId = linearPointer.pointer.id.toString()

                    // find the business network group
                    val businessNetworkGroup = bnService
                        .getBusinessNetworkGroup(UniqueIdentifier.fromString(groupId))
                        ?: argFail("Group $groupId does not exist")

                    // check if our identity can manage and distribute data for the group
                    authorise(businessNetworkGroup.state.data.networkId,
                        bnService) { it.canManageData() && it.canDistributeData() }

                    groupId

                }.let {
                    (it + newGroupIds).toSet() // combine the new group ids with the newly added group ids
                }

            progressTracker.currentStep = FETCHING_GROUP_DETAILS


            val (
                groupParticipantsWithDataManagementRights,
                groupParticipantsWithDataDistributionRights
            ) =
                getGroupParticipantsWithManagementAndDistributionRights(groupIds)


            progressTracker.currentStep = BUILDING_THE_TX
            val signers = groupParticipantsWithDataManagementRights + ourIdentity
            val signerKeys = signers.map { it.owningKey }

            // transform the new group identifiers into linear pointers to be added in the GroupDataAssociationState
            val groupLinearPointers =
                groupIds.map { linearPointer(it, GroupState::class.java) }.toSet()

            val outputState = groupDataAssociationStateRef.state.data.copy(
                associatedGroupStates = groupLinearPointers,
                participants = groupParticipantsWithDataManagementRights.toList()
            )

            val txBuilder = TransactionBuilder(getDefaultNotary(serviceHub))
                .addInputState(groupDataAssociationStateRef)
                .addOutputState(outputState)
                .addCommand(GroupDataAssociationContract.Commands.UpdateGroupParticipants(), signerKeys)


            progressTracker.currentStep = VERIFYING_THE_TX
            txBuilder.verify(serviceHub)

            val lockId = UUID.randomUUID()

            // lock the state so that it cannot be spent elsewhere
            serviceHub.vaultService.softLockReserve(lockId, NonEmptySet.of(groupDataAssociationStateRef.ref))

            progressTracker.currentStep = COLLECTING_SIGS_AND_FINALITY
            val finalizedTx = subFlow(CollectSignaturesAndFinalizeTransactionFlow(
                builder = txBuilder,
                myOptionalKeys = null,
                signers = signers,
                participants = (groupParticipantsWithDataManagementRights + groupParticipantsWithDataDistributionRights)
            ))


            // TODO check if we explicitly need to do this
            progressTracker.currentStep = LINKING_TX
            // linking state refs that were linked with this transactions
            val groupDataAssociatedStates =
                getGroupDataAssociatedStates(outputState.linearId.toString())



            progressTracker.currentStep = DISTRIBUTING_GROUP_DATA
            // distribute the transaction to all group members
            groupIds.forEach { groupId ->
                subFlow(MembershipBroadcastFlows.DistributeTransactionsToGroupFlow(
                    signedTransactions = listOf(finalizedTx),
                    stateAndRefs = groupDataAssociatedStates.toSet(),
                    groupId = groupId)
                { party ->
                    party !in
                            (groupParticipantsWithDataDistributionRights + groupParticipantsWithDataManagementRights)
                })
            }

            // release the lock
            serviceHub.vaultService.softLockRelease(lockId)

            return "Data with id: ${outputState.linearId} updated " +
                    "and distributed to groups: ${groupIds.joinToString()}, TxId: ${finalizedTx.id}"
        }
    }


    /** Updates the group data of the [GroupDataAssociationState]
     * @param dataIdentifier the [GroupDataAssociationState] identifier to update
     * @param metadataMap the data to be replaced in the [GroupDataAssociationState]
     * @param referredStates the new references to be updated
     */
    @InitiatingFlow
    @StartableByRPC
    @Suppress("unused")
    class UpdateAssociationReferences(
        private val dataIdentifier: String,
        private val referredStates: Set<StatePointer<out ContractState>>,
        private val metadataMap: Map<String, String>
    ) : GroupDataManagementFlow<String>() {

        companion object {
            object FETCHING_GROUP_DETAILS : ProgressTracker.Step("Fetching Group Details")

            object BUILDING_THE_TX : ProgressTracker.Step("Building transaction.")
            object VERIFYING_THE_TX : ProgressTracker.Step("Verifying transaction.")
            object COLLECTING_SIGS_AND_FINALITY : ProgressTracker.Step("Collecting Signatures & Executing Finality") {
                override fun childProgressTracker() = CollectSignaturesAndFinalizeTransactionFlow.tracker()
            }

            object DISTRIBUTING_GROUP_DATA :
                ProgressTracker.Step("Distributing Group Association data to other participants") {
                override fun childProgressTracker(): ProgressTracker =
                    MembershipBroadcastFlows.DistributeTransactionsToGroupFlow.tracker()
            }

            fun tracker() = ProgressTracker(FETCHING_GROUP_DETAILS,
                BUILDING_THE_TX,
                VERIFYING_THE_TX,
                COLLECTING_SIGS_AND_FINALITY,
                DISTRIBUTING_GROUP_DATA)
        }

        override val progressTracker = tracker()


        @Suspendable
        override fun call(): String {

            // find out the group data association state from the vault to be distributed to the new set of participants
            val groupDataAssociationStateRef = getGroupDataState(dataIdentifier)

            val groupIds =
                groupDataAssociationStateRef.state.data.associatedGroupStates.map { it.pointer.id.toString() }.toSet()


            val (
                groupParticipantsWithDataManagementRights,
                groupParticipantsWithDataDistributionRights
            ) =
                getGroupParticipantsWithManagementAndDistributionRights(groupIds)


            progressTracker.currentStep = BUILDING_THE_TX
            val signers = groupParticipantsWithDataManagementRights + ourIdentity
            val signerKeys = signers.map { it.owningKey }


            val outputState = groupDataAssociationStateRef.state.data.copy(
                metaData = metadataMap,
                data = referredStates
            )

            val txBuilder = TransactionBuilder(getDefaultNotary(serviceHub))
                .addInputState(groupDataAssociationStateRef)
                .addOutputState(outputState)
                .addCommand(GroupDataAssociationContract.Commands.UpdateGroupData(), signerKeys)


            progressTracker.currentStep = VERIFYING_THE_TX
            txBuilder.verify(serviceHub)

            val lockId = UUID.randomUUID()

            // lock the state so that it cannot be spent elsewhere
            serviceHub.vaultService.softLockReserve(lockId, NonEmptySet.of(groupDataAssociationStateRef.ref))

            progressTracker.currentStep = COLLECTING_SIGS_AND_FINALITY
            val finalizedTx = subFlow(CollectSignaturesAndFinalizeTransactionFlow(
                builder = txBuilder,
                myOptionalKeys = null,
                signers = signers.toSet(),
                participants = (groupParticipantsWithDataManagementRights
                        + groupParticipantsWithDataDistributionRights).toSet()
            ))

            progressTracker.currentStep = DISTRIBUTING_GROUP_DATA
            // distribute the transaction to all group members
            groupIds.forEach { groupId ->
                subFlow(MembershipBroadcastFlows.DistributeTransactionsToGroupFlow(
                    signedTransactions = listOf(finalizedTx),
                    groupId = groupId) { it != ourIdentity }
                )
            }

            // release the lock
            serviceHub.vaultService.softLockRelease(lockId)

            return "Data with id: ${outputState.linearId} updated " +
                    "and distributed to groups: ${groupIds.joinToString()}, TxId: ${finalizedTx.id}"
        }
    }
}
