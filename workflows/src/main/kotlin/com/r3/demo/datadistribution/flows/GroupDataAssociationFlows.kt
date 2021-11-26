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
    fun getGroupsParticipants(
        groupIds: Set<String>,
        filter: (MembershipState) -> Boolean = { true }
    ): Set<Party> {

        val bnService = serviceHub.cordaService(BNService::class.java)
        return groupIds.flatMap { groupId ->

            val groupParticipants = mutableSetOf<Party>()

            bnService.getBusinessNetworkGroup(UniqueIdentifier.fromString(groupId))?.apply {
                // check if we are a part of the network and have data admin role
                authorise(state.data.networkId, bnService) { it.canManageData() && it.canDistributeData() }

                val dataDistributionParties = state.data.participants.filter { party ->
                    bnService.getMembership(state.data.networkId, party)?.state?.data?.let {
                        filter.invoke(it)
                    } ?: flowFail("Cannot fetch membership details for ${party.name} " +
                            "in GroupState ${state.data.name} of network ${state.data.networkId}")
                }

                groupParticipants.addAll(dataDistributionParties)
            } ?: argFail("Group $groupId does not exist")

            (groupParticipants + ourIdentity).toSet()

        }.toSet()
    }


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
    fun getGroupDataAssociatedStates(groupDataAssociationStateIdentifier: String, filter: (ContractState) -> Boolean = {true}): List<StateAndRef<ContractState>>? {
        val groupDataState = getGroupDataState(groupDataAssociationStateIdentifier)

//            return serviceHub.validatedTransactions.getTransaction(groupDataState.ref.txhash)?.coreTransaction?.let {
//                it.outputStates.filterNot { contractState -> contractState !is GroupDataAssociationState }
//            } ?: flowFail("Could not find the transaction of GroupDataAssociationState in the ledger. " +
//                    "This is likely because the latest transaction has not been shared with this node")

        return serviceHub.validatedTransactions.getTransaction(groupDataState.ref.txhash)
            ?.coreTransaction?.filterOutRefs { it !is GroupDataAssociationState && filter(it) }

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
     * @param txBuilder containing zero instances of [GroupDataAssociationState] as input or output state and other relevant input and output states that we wish to store along
     * @param data KV pairs, can act as tags
     * @param groupIds relevant groups
     */
    @InitiatingFlow
    @StartableByRPC
    class CreateDataFlow(
        private val txBuilder: TransactionBuilder,       // this can be changed to the specific type as per the needs
        // tagged data,
        // we could use Any here or String here, but Any risks
        // the type to be @CordaSerializable and using String
        // would be too restrictive what we can store.
        // So a hashtable can provide some balance on what is stored.
        // A set oF KV pairs probably offers some balance between the two.
        private val data: Map<String, String>,
        private val groupIds: Set<String>
    ) : GroupDataManagementFlow<String>() {

        init {
            require(txBuilder.outputStates().filterIsInstance<GroupDataAssociationState>().isEmpty()
                    && txBuilder.inputStates().filterIsInstance<GroupDataAssociationState>().isEmpty())
            {
                "Input/output must not be contain any instance of GroupDataAssociationState in TransactionBuilder " +
                        "while creating group associated data. It will be configured in this flow"
            }
        }

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
            val groupParticipantsWithDistributionRights = getGroupsParticipants(groupIds) {
                it.canDistributeData()
            }

            val groupParticipantsWithDataManagementRights = getGroupsParticipants(groupIds) {
                it.canManageData() && it.canDistributeData()
            }

            progressTracker.currentStep = BUILDING_THE_TX
            val groupLinearPointers =
                groupIds.map { linearPointer(it, GroupState::class.java) }.toSet()

            val outputState = GroupDataAssociationState(
                metaData = data,   // the tagged data we want to store
                associatedGroupStates = groupLinearPointers,
                participants = groupParticipantsWithDistributionRights.toList())

            val signers = groupParticipantsWithDataManagementRights + ourIdentity
            val signerKeys = signers.map { it.owningKey }

            txBuilder
                .addOutputState(outputState)
                .addCommand(GroupDataAssociationContract.Commands.CreateAssociation(), signerKeys)

            progressTracker.currentStep = VERIFYING_THE_TX
            txBuilder.verify(serviceHub)

            progressTracker.currentStep = COLLECTING_SIGS_AND_FINALITY
            val finalizedTx = subFlow(CollectSignaturesAndFinalizeTransactionFlow(
                builder = txBuilder,
                myOptionalKeys = null,
                signers = signers,
                participants = groupParticipantsWithDistributionRights
            ))


            progressTracker.currentStep = DISTRIBUTING_GROUP_DATA
            // distribute the transaction to all group members except the group data participants, because they already have the transaction
            groupIds.forEach { groupId ->
                subFlow(MembershipBroadcastFlows.DistributeTransactionsToGroupFlow(
                    signedTransactions = listOf(finalizedTx),    // this finalized transaction contains all of the embedded states
                    groupId = groupId)

                { party -> // don't send it again to the data admins of the data distributors
                    party !in (groupParticipantsWithDistributionRights + groupParticipantsWithDataManagementRights)
                }
                )
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
    class UpdateGroupDataParticipantsFlow(
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

            val groupParticipantsWithDataManagementRights = getGroupsParticipants(groupIds){
                it.canManageData() && it.canDistributeData()
            }

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
                participants = groupParticipantsWithDataManagementRights
            ))


            // TODO check if we explicitly need to do this
            progressTracker.currentStep = LINKING_TX
            // distribute the transaction that produced the current consuming state so that
            val previousTransaction =
                serviceHub.validatedTransactions.getTransaction(groupDataAssociationStateRef.ref.txhash)


            val transactionsToDistribute = previousTransaction?.let {
                val txList = mutableListOf(finalizedTx)
                txList.add(it)
                txList
            } ?: listOf(finalizedTx)

            progressTracker.currentStep = DISTRIBUTING_GROUP_DATA
            // distribute the transaction to all group members
            groupIds.forEach { groupId ->
                subFlow(MembershipBroadcastFlows.DistributeTransactionsToGroupFlow(
                    signedTransactions = transactionsToDistribute,
                    groupId = groupId)
                { party -> party != ourIdentity }
                )
            }

            // release the lock
            serviceHub.vaultService.softLockRelease(lockId)

            return "Data with id: ${outputState.linearId} updated and distributed to groups: ${groupIds.joinToString()}, TxId: ${finalizedTx.id}"
        }
    }


    /** Updates the group data of the [GroupDataAssociationState]
     * @param dataIdentifier the [GroupDataAssociationState] identifier to update
     * @param txBuilder containing zero input instances of [GroupDataAssociationState] and the set of input states to be consumed and the output states to be produced
     * @param data the data to be replaced in the [GroupDataAssociationState]
     */
    @InitiatingFlow
    @StartableByRPC
    @Suppress("unused")
    class UpdateGroupDataFlow(
        private val dataIdentifier: String,
        private val txBuilder: TransactionBuilder,
        private val data: Map<String, String>
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

            val groupIds = groupDataAssociationStateRef.state.data.associatedGroupStates.map { it.pointer.id.toString() }.toSet()
            val groupParticipantsWithDataManagementRights = getGroupsParticipants(groupIds)
            {
                it.canManageData() && it.canDistributeData()
            }


            progressTracker.currentStep = BUILDING_THE_TX
            val signers = groupParticipantsWithDataManagementRights + ourIdentity
            val signerKeys = signers.map { it.owningKey }


            val outputState = groupDataAssociationStateRef.state.data.copy(
                metaData = data
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
                participants = groupParticipantsWithDataManagementRights.toSet()
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

            return "Data with id: ${outputState.linearId} updated and distributed to groups: ${groupIds.joinToString()}, TxId: ${finalizedTx.id}"
        }
    }
}
