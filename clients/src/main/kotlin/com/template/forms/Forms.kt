package com.template.forms

import com.r3.custom.Schema
import com.r3.demo.extensibleworkflows.ManageGroupAwareSchemaBackedKVData
import net.corda.core.identity.Party


class Forms {

    class BusinessNetwork {
        var groupName: String = ""
        var parties: Set<Party> = setOf()
        var party : Party? = null
        var membershipIds: Set<String> = setOf()
        var membershipId: String = ""
        var networkId : String = ""
    }

    class GroupAwareSchema {
        var groupIds: Set<String> = setOf()
        var schema: Schema = Schema(
            name = "",
            description = "",
            version = "",
            attributes = setOf(),
            parties = listOf()
        )
    }

    class GroupAwareSchemaBackedKV {
        var groupDataAssociationId: String = ""
        var schemaBackedKVId: String = ""
        var schemaId: String = ""
        var data: Map<String, String> = mapOf()
        var operation: ManageGroupAwareSchemaBackedKVData.Operation =
            ManageGroupAwareSchemaBackedKVData.Operation.CREATE
    }

}