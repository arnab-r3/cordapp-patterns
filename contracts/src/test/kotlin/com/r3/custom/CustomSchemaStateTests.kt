package com.r3.custom

import com.r3.custom.DataType.INTEGER
import com.r3.custom.DataType.STRING
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction
import net.corda.testing.core.*
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class CustomSchemaStateTests {


    private val ALICE_PARTY = TestIdentity(ALICE_NAME).party
    private val BOB_PARTY = TestIdentity(BOB_NAME).party
    private val CHARLIE_PARTY = TestIdentity(CHARLIE_NAME).party
    private val DAVE_PARTY = TestIdentity(DAVE_NAME).party

    class DummyContract : Contract {
        override fun verify(tx: LedgerTransaction) {}
        class Commands {
            class OperationOne : PermissionCommandData<DummyContract> {
                override fun doesCreate() = true
            }

            class OperationTwo : PermissionCommandData<DummyContract> {
                override fun doesCreate() = true
                override fun doesUpdate() = true
            }

            class OperationThree : PermissionCommandData<DummyContract> {
                override fun doesUpdate() = true
            }

            class OperationFour : PermissionCommandData<DummyContract> {
                override fun doesDelete() = true
            }
        }
    }

    private fun getEvents(eventName: String): EventDescriptor<DummyContract> {
        val events = mapOf<String, EventDescriptor<DummyContract>>(
            "Create" to EventDescriptor(
                name = "CreationEvent",
                description = "Creation",
                triggeringContract = DummyContract::class,
                triggeringCommand = DummyContract.Commands.OperationOne::class,
                recipients = setOf(ALICE_PARTY, BOB_PARTY),
                signers = setOf(CHARLIE_PARTY, DAVE_PARTY),
                accessControlDefinition = AccessControlDefinition(
                    parties = setOf(ALICE_PARTY),
                    canCreate = true
                )
            ),
            "Update" to EventDescriptor(
                name = "Update",
                description = "Update",
                triggeringContract = DummyContract::class,
                triggeringCommand = DummyContract.Commands.OperationThree::class,
                recipients = setOf(ALICE_PARTY, BOB_PARTY),
                signers = setOf(CHARLIE_PARTY, DAVE_PARTY),
                accessControlDefinition = AccessControlDefinition(
                    parties = setOf(ALICE_PARTY, BOB_PARTY),
                    canUpdate = true
                )
            ),
            "CreateAndUpdate" to EventDescriptor(
                name = "CreateAndUpdate",
                description = "CreateAndUpdate",
                triggeringContract = DummyContract::class,
                triggeringCommand = DummyContract.Commands.OperationTwo::class,
                recipients = setOf(ALICE_PARTY, BOB_PARTY),
                signers = setOf(CHARLIE_PARTY, DAVE_PARTY),
                accessControlDefinition = AccessControlDefinition(
                    parties = setOf(ALICE_PARTY, BOB_PARTY),
                    canUpdate = true,
                    canCreate = true
                )
            ),
            "Delete" to EventDescriptor(
                name = "Delete",
                description = "Delete",
                triggeringContract = DummyContract::class,
                triggeringCommand = DummyContract.Commands.OperationFour::class,
                recipients = setOf(ALICE_PARTY, BOB_PARTY),
                signers = setOf(CHARLIE_PARTY, DAVE_PARTY),
                accessControlDefinition = AccessControlDefinition(
                    parties = setOf(DAVE_PARTY, CHARLIE_PARTY),
                    canDelete = true
                )
            )
        )
        return events[eventName] ?: throw IllegalArgumentException("Invalid Event name")
    }

    @Test
    fun `test minimal schema`() {
        val schema = Schema<DummyContract>(
            name = "dummy schema",
            attributes = setOf(
                Attribute(
                    name = "firstAttribute",    // firstAttribute is always updated or modified with Create or Update
                    dataType = STRING,
                    mandatory = true,
                    regex = """[a-z]{6,9}""".toRegex(),
                    associatedEvents = setOf(getEvents("Create"), getEvents("Update"))
                ),
                Attribute(
                    name = "secondAttribute",    // secondAttribute is always updated or modified with Create or Update
                    dataType = INTEGER,
                    mandatory = false,
                    regex = """[0-9]{2,5}""".toRegex(),
                    associatedEvents = setOf(getEvents("CreateAndUpdate"), getEvents("Delete"))
                )
            )
        )

        val kv1 = SchemaBackedKV<DummyContract>(
            kvPairs = mapOf(
                "firstAttribute" to "hellos",
                "secondAttribute" to "25"
            ),
            schema = schema
        )

        //assertEquals(true, kv.validateSchema(), "Schema validation failed")

        assertDoesNotThrow ("Schema validation failed"){ kv1.validateSchema() }

        val kv2 = SchemaBackedKV<DummyContract>(
            kvPairs = mapOf(
                "firstAttribute" to "hellos",
                "secondAttribute" to "2"
            ),
            schema = schema
        )

        assertThrows<IllegalArgumentException> { kv2.validateSchema() }

        val kv3 = SchemaBackedKV<DummyContract>(
            kvPairs = mapOf(
                "firstAttribute" to "hellos",
                "secondAttribute" to "23abc"
            ),
            schema = schema
        )

        assertThrows<IllegalArgumentException> { kv3.validateSchema() }
    }

    @Test
    fun `test permissions`() {
        val schema = Schema<DummyContract>(
            name = "dummy schema",
            attributes = setOf(
                Attribute(
                    name = "firstAttribute",    // firstAttribute is always updated or modified with Create or Update
                    dataType = STRING,
                    mandatory = true,
                    regex = """[a-z]{6,9}""".toRegex(),
                    associatedEvents = setOf(getEvents("Create"), getEvents("Update"))
                ),
                Attribute(
                    name = "secondAttribute",    // secondAttribute is always updated or modified with Create or Update
                    dataType = INTEGER,
                    mandatory = false,
                    regex = """[0-9]{2,5}""".toRegex(),
                    associatedEvents = setOf(getEvents("CreateAndUpdate"), getEvents("Delete"))
                )
            )
        )

        val kv1 = SchemaBackedKV<DummyContract>(
            kvPairs = mapOf(
                "firstAttribute" to "hellos",
                "secondAttribute" to "25"
            ),
            schema = schema
        )

        assertThrows<IllegalArgumentException>{DummyContract.Commands.OperationOne().checkPermissions(kv1.schema, DAVE_PARTY)}
        assertThrows<IllegalArgumentException>{DummyContract.Commands.OperationOne().checkPermissions(kv1.schema, CHARLIE_PARTY)}
        assertThrows<IllegalArgumentException>{DummyContract.Commands.OperationOne().checkPermissions(kv1.schema, BOB_PARTY)}
        assertDoesNotThrow { DummyContract.Commands.OperationOne().checkPermissions(kv1.schema, ALICE_PARTY) }


        assertThrows<IllegalArgumentException>{DummyContract.Commands.OperationTwo().checkPermissions(kv1.schema, DAVE_PARTY)}
        assertThrows<IllegalArgumentException>{DummyContract.Commands.OperationTwo().checkPermissions(kv1.schema, CHARLIE_PARTY)}
        assertDoesNotThrow { DummyContract.Commands.OperationTwo().checkPermissions(kv1.schema, ALICE_PARTY) }
        assertDoesNotThrow { DummyContract.Commands.OperationTwo().checkPermissions(kv1.schema, BOB_PARTY) }

    }
}