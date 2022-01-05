//package com.r3.custom
//
//import com.r3.custom.DataType.INTEGER
//import com.r3.custom.DataType.STRING
//import com.r3.examples.testing.staticPointer
//import net.corda.core.contracts.Contract
//import net.corda.core.contracts.StatePointer.Companion.staticPointer
//import net.corda.core.contracts.StaticPointer
//import net.corda.core.transactions.LedgerTransaction
//import net.corda.testing.core.*
//import org.junit.Test
//import org.junit.jupiter.api.assertDoesNotThrow
//import org.junit.jupiter.api.assertThrows
//
//class SchemaTests {
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
//    @Test
//    fun `test minimal schema`() {
//
//        val schema = SchemaState(name = "test-schema-one",
//            version = "v1",
//            description = "first schema",
//            attributes = setOf(
//                Attribute(
//                    name = "firstAttribute",
//                    description = "first attribute",
//                    mandatory = true,
//                    dataType = INTEGER,
//                    associatedEvents = setOf("create", "update"),
//                    regex = """[0-9]{2,5}""".toRegex()
//                ),
//                Attribute(
//                    name = "secondAttribute",
//                    description = "second attribute",
//                    mandatory = true,
//                    dataType = STRING,
//                    associatedEvents = setOf("create", "update"),
//                    regex = """[a-z]{5,9}""".toRegex()
//                )
//            ),
//            participants = listOf(ALICE_PARTY))
//
//
//        val kvBackedSchemaState = SchemaBackedKVState(
//            kvPairs = mapOf(
//                "firstAttribute" to "123",
//                "secondAttribute" to "hello"
//            ),
//            participants = listOf(ALICE_PARTY),
//            schemaStatePointer = staticPointer<SchemaState>()   // pass dummy pointer
//        )
//
//        assertDoesNotThrow("Schema validation failed") { kvBackedSchemaState.validateSchema() }
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
//        assertThrows<IllegalArgumentException> {
//            DummyContract.Commands.OperationOne().checkPermissions(kv1.schema, DAVE_PARTY)
//        }
//        assertThrows<IllegalArgumentException> {
//            DummyContract.Commands.OperationOne().checkPermissions(kv1.schema, CHARLIE_PARTY)
//        }
//        assertThrows<IllegalArgumentException> {
//            DummyContract.Commands.OperationOne().checkPermissions(kv1.schema, BOB_PARTY)
//        }
//        assertDoesNotThrow { DummyContract.Commands.OperationOne().checkPermissions(kv1.schema, ALICE_PARTY) }
//
//
//        assertThrows<IllegalArgumentException> {
//            DummyContract.Commands.OperationTwo().checkPermissions(kv1.schema, DAVE_PARTY)
//        }
//        assertThrows<IllegalArgumentException> {
//            DummyContract.Commands.OperationTwo().checkPermissions(kv1.schema, CHARLIE_PARTY)
//        }
//        assertDoesNotThrow { DummyContract.Commands.OperationTwo().checkPermissions(kv1.schema, ALICE_PARTY) }
//        assertDoesNotThrow { DummyContract.Commands.OperationTwo().checkPermissions(kv1.schema, BOB_PARTY) }
//
//    }
//}