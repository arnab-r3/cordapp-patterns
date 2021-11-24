package com.r3.demo.datadistribution.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.demo.common.canDistributeData
import com.r3.demo.common.canManageData
import com.r3.demo.datadistribution.contracts.GroupDataAssociationContract
import com.r3.demo.datadistribution.contracts.GroupDataAssociationState
import com.r3.demo.generic.argFail
import com.r3.demo.generic.getDefaultNotary
import com.r3.demo.generic.linearPointer
import com.template.flows.CollectSignaturesAndFinalizeTransactionFlow
import net.corda.bn.flows.BNService
import net.corda.bn.flows.MembershipManagementFlow
import net.corda.bn.states.GroupState
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.NonEmptySet
import net.corda.core.utilities.ProgressTracker
import java.util.*

/**
 * Use these flows when associating and distributing relevant states to parties. The capabilities include creating an
 * association and including a set of states that will be distributed along with the association. One can also update
 * the association by including new elements that will be distributed or expanding the horizon of participants who
 * are included in the association.
 */
object GroupDataAssociationFlows {

    abstract class GroupDataManagementFlow<T> : MembershipManagementFlow<T>() {

        fun getGroupDataParticipants(groupIds: Set<String>?): Set<Party> {

            val bnService = serviceHub.cordaService(BNService::class.java)
            return groupIds?.flatMap { groupId ->

                val groupParticipants = mutableSetOf<Party>()
                bnService.getBusinessNetworkGroup(UniqueIdentifier.fromString(groupId))?.apply {
                    // check if we are a part of the network and have data admin role
                    authorise(state.data.networkId, bnService) { it.canManageData() && it.canDistributeData() }
                    val dataDistributionParties = state.data.participants.filter { party ->
                        val membershipState = bnService.getMembership(state.data.networkId, party)?.state?.data
                        membershipState?.canDistributeData() ?: false
                    }
                    groupParticipants.addAll(dataDistributionParties)
                } ?: argFail("Group $groupId does not exist")

                (groupParticipants + ourIdentity).toSet()

            }?.toSet() ?: setOf(ourIdentity)
        }


        fun getGroupDataState(groupDataAssociationStateIdentifier: String): StateAndRef<GroupDataAssociationState> {

            val linearStateQueryCriteria = QueryCriteria.LinearStateQueryCriteria()
                .withUuid(listOf(UUID.fromString(groupDataAssociationStateIdentifier)))
                .withStatus(Vault.StateStatus.UNCONSUMED)
                .withRelevancyStatus(Vault.RelevancyStatus.ALL)

            val queryResult = serviceHub.vaultService.queryBy<GroupDataAssociationState>(linearStateQueryCriteria).states
            require(queryResult.size == 1) {"Could not find GroupDataAssociationState with id: $groupDataAssociationStateIdentifier"}
            return queryResult.single()

        }

        fun getGroupDataAssociatedStates(groupDataAssociationStateIdentifier: String): List<StateAndRef<ContractState>>? {
            val groupDataState = getGroupDataState(groupDataAssociationStateIdentifier)

//            return serviceHub.validatedTransactions.getTransaction(groupDataState.ref.txhash)?.coreTransaction?.let {
//                it.outputStates.filterNot { contractState -> contractState !is GroupDataAssociationState }
//            } ?: flowFail("Could not find the transaction of GroupDataAssociationState in the ledger. " +
//                    "This is likely because the latest transaction has not been shared with this node")

             return serviceHub.validatedTransactions.getTransaction(groupDataState.ref.txhash)?.coreTransaction?.filterOutRefs { it !is GroupDataAssociationState }

        }
    }

    /**
     * Create Data item by the data administrator and distribute to the groups of participants.
     * @param txBuilder containing zero instances of [GroupDataAssociationState] as input or output state and other relevant input and output states that we wish to store along
     * @param data KV pairs, can act as tags
     * @param groupIds relevant groups
     */
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
            val groupDataParticipants = getGroupDataParticipants(groupIds)

            progressTracker.currentStep = BUILDING_THE_TX
            val groupLinearPointers =
                groupIds.map { linearPointer(it, GroupState::class.java) }.toSet()

            val outputState = GroupDataAssociationState(
                value = data,   // the tagged data we want to store
                associatedGroupStates = groupLinearPointers,
                participants = groupDataParticipants.toList())

            val signers = groupDataParticipants + ourIdentity
            val signerKeys = signers.map { it.owningKey }

            txBuilder
                .addOutputState(outputState)
                .addCommand(GroupDataAssociationContract.Commands.CreateData(), signerKeys)

            progressTracker.currentStep = VERIFYING_THE_TX
            txBuilder.verify(serviceHub)

            progressTracker.currentStep = COLLECTING_SIGS_AND_FINALITY
            val finalizedTx = subFlow(CollectSignaturesAndFinalizeTransactionFlow(
                builder = txBuilder,
                myOptionalKeys = null,
                signers = signers,
                participants = groupDataParticipants
            ))


            progressTracker.currentStep = DISTRIBUTING_GROUP_DATA
            // distribute the transaction to all group members except the group data participants, because they already have the transaction
            groupIds.forEach { groupId ->
                subFlow(MembershipBroadcastFlows.DistributeTransactionsToGroupFlow(
                    signedTransactions = listOf(finalizedTx),    // this finalized transaction contains all of the embedded states
                    groupId = groupId) { it !in groupDataParticipants }
                )
            }

            return "Data with id: ${outputState.linearId} created and distributed to groups: ${groupIds.joinToString()}, TxId: ${finalizedTx.id}"
        }
    }

    /**
     * Flow to add new groups to the association
     * @param dataIdentifier the [GroupDataAssociationState] identifier
     */
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
                groupDataAssociationStateRef.state.data.associatedGroupStates?.toSet()?.map { linearPointer ->

                    val groupId = linearPointer.pointer.id.toString()
                    val businessNetworkGroup = bnService
                        .getBusinessNetworkGroup(UniqueIdentifier.fromString(groupId))
                        ?: argFail("Group $groupId does not exist")
                    // check if our identity can manage and distribute data for the group
                    authorise(businessNetworkGroup.state.data.networkId,
                        bnService) { it.canManageData() && it.canDistributeData() }
                    groupId

                }?.let {
                    (it + newGroupIds).toSet()
                } ?: newGroupIds.toSet()

            progressTracker.currentStep = FETCHING_GROUP_DETAILS

            val groupParticipants = getGroupDataParticipants(groupIds)

            progressTracker.currentStep = BUILDING_THE_TX
            val signers = groupParticipants + ourIdentity
            val signerKeys = signers.map { it.owningKey }

            val groupLinearPointers = groupIds.map { linearPointer(it, GroupState::class.java) }.toSet()

            val outputState = groupDataAssociationStateRef.state.data.copy(
                associatedGroupStates = groupLinearPointers,
                participants = groupParticipants.toList()
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
                participants = groupParticipants
            ))


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
                    groupId = groupId) { it != ourIdentity }
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

            val participantsToDistributeTo = groupDataAssociationStateRef.state.data.participants

            val groupIds = groupDataAssociationStateRef.state.data.associatedGroupStates?.map {
                it.pointer.id
            }

            progressTracker.currentStep = BUILDING_THE_TX
            val signers = participantsToDistributeTo + ourIdentity
            val signerKeys = signers.map { it.owningKey }


            val outputState = groupDataAssociationStateRef.state.data.copy(
                value = data
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
                participants = participantsToDistributeTo.toSet()
            ))

            progressTracker.currentStep = DISTRIBUTING_GROUP_DATA
            // distribute the transaction to all group members
            groupIds?.forEach { groupId ->
                subFlow(MembershipBroadcastFlows.DistributeTransactionsToGroupFlow(
                    signedTransactions = listOf(finalizedTx),
                    groupId = groupId.toString()) { it != ourIdentity }
                )
            }

            // release the lock
            serviceHub.vaultService.softLockRelease(lockId)

            return "Data with id: ${outputState.linearId} updated and distributed to groups: ${groupIds?.joinToString()}, TxId: ${finalizedTx.id}"
        }
    }
}
