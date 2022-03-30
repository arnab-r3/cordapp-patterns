package com.r3.demo.contractdependency.workflows

import co.paralleluniverse.fibers.Suspendable
import com.r3.demo.contractdependency.contracts.JsonStateContract
import com.r3.demo.contractdependency.states.JsonState
import com.r3.demo.generic.getDefaultNotary
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@InitiatingFlow
@StartableByRPC
class CreateJSONFlow : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
//        val attachmentIds = serviceHub.cordaService(CordappDependencyRegistrationService::class.java).getAttachmentIds()
        val jsonValue = """{"contract":{"buyer":"ITC","seller":"Agro Corp","financeRequests":[150000,200000]}}""";
        val jsonValueState = JsonState(jsonValue, listOf(ourIdentity))
//        val attachmentHash = serviceHub.attachments.importAttachment(
//            FileInputStream(File("/Users/arnab.chatterjee/Documents/03_Code/07_experiments/cordapp-patterns/build/nodes/PartyB/cordapps/json-path-2.4.0.jar")), "uploader_name", "jsonpath_lib");
        val txBuilder = TransactionBuilder(getDefaultNotary(serviceHub))
            .addOutputState(jsonValueState)
            .addCommand(JsonStateContract.JsonStateCommands.Verify("""$.contract.buyer"""), ourIdentity.owningKey)
//            .addAttachment(attachmentHash)
//
//        attachmentIds.forEach{
//            txBuilder.addAttachment(it)
//        }
        txBuilder.verify(serviceHub)
        val signedTx = serviceHub.signInitialTransaction(txBuilder)
        return subFlow(FinalityFlow(transaction = signedTx, sessions = emptyList()))
    }
}
