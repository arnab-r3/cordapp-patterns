//package com.r3.custom
//
//import com.r3.custom.DataType.INTEGER
//import com.r3.custom.DataType.STRING
//import net.corda.core.contracts.Contract
//import net.corda.core.transactions.LedgerTransaction
//import net.corda.testing.core.*
//import org.junit.Test
//import org.junit.jupiter.api.assertDoesNotThrow
//import org.junit.jupiter.api.assertThrows
//
//class SchemaStateTests {
//
//
//    private val ALICE_PARTY = TestIdentity(ALICE_NAME).party
//    private val BOB_PARTY = TestIdentity(BOB_NAME).party
//    private val CHARLIE_PARTY = TestIdentity(CHARLIE_NAME).party
//    private val DAVE_PARTY = TestIdentity(DAVE_NAME).party
//
//    class DummyContract : Contract {
//        override fun verify(tx: LedgerTransaction) {}
//    }
//
//    private fun getEvents(eventName: String): EventDescriptor<DummyContract> {
//        val events = mapOf<String, EventDescriptor<DummyContract>>(
//            "Create" to EventDescriptor(
//                name = "CreationEvent",
//                description = "Creation",
//                triggeringContract = DummyContract::class,
//                triggeringCommand = DummyContract.Commands.OperationOne::class,
//                accessControlDefinition = AccessControlDefinition(
//                    parties = setOf(ALICE_PARTY),
//                    canCreate = true
//                )
//            ),
//            "Update" to EventDescriptor(
//                name = "Update",
//                description = "Update",
//                triggeringContract = DummyContract::class,
//                triggeringCommand = DummyContract.Commands.OperationThree::class,
//                accessControlDefinition = AccessControlDefinition(
//                    parties = setOf(ALICE_PARTY, BOB_PARTY),
//                    canUpdate = true
//                )
//            ),
//            "CreateAndUpdate" to EventDescriptor(
//                name = "CreateAndUpdate",
//                description = "CreateAndUpdate",
//                triggeringContract = DummyContract::class,
//                triggeringCommand = DummyContract.Commands.OperationTwo::class,
//                accessControlDefinition = AccessControlDefinition(
//                    parties = setOf(ALICE_PARTY, BOB_PARTY),
//                    canUpdate = true,
//                    canCreate = true
//                )
//            ),
//            "Delete" to EventDescriptor(
//                name = "Delete",
//                description = "Delete",
//                triggeringContract = DummyContract::class,
//                triggeringCommand = DummyContract.Commands.OperationFour::class,
//                accessControlDefinition = AccessControlDefinition(
//                    parties = setOf(DAVE_PARTY, CHARLIE_PARTY),
//                    canDelete = true
//                )
//            )
//        )
//        return events[eventName] ?: throw IllegalArgumentException("Invalid Event name")
//    }
//
//    @Test
//    fun `test minimal schema`() {
//        val schema = SchemaState<DummyContract>(
//            name = "dummy schema",
//            participants = listOf(ALICE_PARTY, BOB_PARTY, DAVE_PARTY, CHARLIE_PARTY),
//            attributes = setOf(
//                Attribute(
//                    name = "firstAttribute",    // firstAttribute is always updated or modified with Create or Update
//                    dataType = STRING,
//                    mandatory = true,
//                    regex = """[a-z]{6,9}""".toRegex(),
//                    associatedEvents = setOf(getEvents("Create"), getEvents("Update"))
//                ),
//                Attribute(
//                    name = "secondAttribute",    // secondAttribute is always updated or modified with Create or Update
//                    dataType = INTEGER,
//                    mandatory = false,
//                    regex = """[0-9]{2,5}""".toRegex(),
//                    associatedEvents = setOf(getEvents("CreateAndUpdate"), getEvents("Delete"))
//                )
//            )
//        )
//
//        val kv1 = SchemaBackedKV<DummyContract>(
//            kvPairs = mapOf(
//                "firstAttribute" to "hellos",
//                "secondAttribute" to "25"
//            ),
//
//        )
//
//        //assertEquals(true, kv.validateSchema(), "Schema validation failed")
//
//        assertDoesNotThrow ("Schema validation failed"){ kv1.validateSchema() }
//
//        val kv2 = SchemaBackedKV<DummyContract>(
//            kvPairs = mapOf(
//                "firstAttribute" to "hellos",
//                "secondAttribute" to "2"
//            ),
//            schema = schema
//        )
//
//        assertThrows<IllegalArgumentException> { kv2.validateSchema() }
//
//        val kv3 = SchemaBackedKV<DummyContract>(
//            kvPairs = mapOf(
//                "firstAttribute" to "hellos",
//                "secondAttribute" to "23abc"
//            ),
//            schema = schema
//        )
//
//        assertThrows<IllegalArgumentException> { kv3.validateSchema() }
//    }
//
//    @Test
//    fun `test permissions`() {
//        val schema = SchemaState<DummyContract>(
//            name = "dummy schema",
//            recipients = setOf(ALICE_PARTY, BOB_PARTY, DAVE_PARTY, CHARLIE_PARTY),
//            signers = setOf(ALICE_PARTY, BOB_PARTY, DAVE_PARTY, CHARLIE_PARTY),
//            attributes = setOf(
//                Attribute(
//                    name = "firstAttribute",    // firstAttribute is always updated or modified with Create or Update
//                    dataType = STRING,
//                    mandatory = true,
//                    regex = """[a-z]{6,9}""".toRegex(),
//                    associatedEvents = setOf(getEvents("Create"), getEvents("Update"))
//                ),
//                Attribute(
//                    name = "secondAttribute",    // secondAttribute is always updated or modified with Create or Update
//                    dataType = INTEGER,
//                    mandatory = false,
//                    regex = """[0-9]{2,5}""".toRegex(),
//                    associatedEvents = setOf(getEvents("CreateAndUpdate"), getEvents("Delete"))
//                )
//            )
//        )
//
//        val kv1 = SchemaBackedKV<DummyContract>(
//            kvPairs = mapOf(
//                "firstAttribute" to "hellos",
//                "secondAttribute" to "25"
//            ),
//            schema = schema
//        )
//
//        assertThrows<IllegalArgumentException>{DummyContract.Commands.OperationOne().checkPermissions(kv1.schema, DAVE_PARTY)}
//        assertThrows<IllegalArgumentException>{DummyContract.Commands.OperationOne().checkPermissions(kv1.schema, CHARLIE_PARTY)}
//        assertThrows<IllegalArgumentException>{DummyContract.Commands.OperationOne().checkPermissions(kv1.schema, BOB_PARTY)}
//        assertDoesNotThrow { DummyContract.Commands.OperationOne().checkPermissions(kv1.schema, ALICE_PARTY) }
//
//
//        assertThrows<IllegalArgumentException>{DummyContract.Commands.OperationTwo().checkPermissions(kv1.schema, DAVE_PARTY)}
//        assertThrows<IllegalArgumentException>{DummyContract.Commands.OperationTwo().checkPermissions(kv1.schema, CHARLIE_PARTY)}
//        assertDoesNotThrow { DummyContract.Commands.OperationTwo().checkPermissions(kv1.schema, ALICE_PARTY) }
//        assertDoesNotThrow { DummyContract.Commands.OperationTwo().checkPermissions(kv1.schema, BOB_PARTY) }
//
//    }
//}