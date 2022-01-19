package com.r3.demo.template.forms

import com.r3.demo.custom.Schema
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
        var data: Map<String, String> = mapOf()
    }

}