package com.r3.demo.contractdependency.contracts

import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.PathNotFoundException
import com.r3.demo.contractdependency.states.JsonState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

class JsonStateContract : Contract {
    override fun verify(tx: LedgerTransaction) {
        val outputs = tx.outputsOfType<JsonState>()
        val commands = tx.commandsOfType<JsonStateCommands>()
        val json = outputs.single().jsonString
        val jsonPath = (commands.single().value as JsonStateCommands.Verify).jsonPath
        val jsonPathHelper = getValueFromJson(json, jsonPath)
        require(jsonPathHelper != null) {"Did not expect the json parsed value to be null for $"}


    }
    private fun getValueFromJson(payload: String?, jsonPath: String?): String? {
        return try {
            if (JsonPath.read<Any?>(payload, jsonPath) != null) JsonPath.read<Any>(payload, jsonPath)
                .toString() else null
        } catch (e: PathNotFoundException) {
            println(String.format("Path %s not found in the payload %s thus returning null", jsonPath, payload))
            null
        }
    }

    interface JsonStateCommands : CommandData {
        class Verify(val jsonPath: String) : JsonStateCommands
    }
}