package com.r3.demo.contractdependency.states

import com.google.common.hash.BloomFilter
import com.google.common.hash.Funnels
import com.r3.demo.contractdependency.contracts.BloomFilterContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.serialization.SerializationWhitelist
import java.nio.charset.Charset

@BelongsToContract(BloomFilterContract::class)
class BloomFilterState(
    override val participants: List<AbstractParty>,
    override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState {

    private lateinit var bloomFilter: BloomFilter<String>

    private fun initBloomFilter() =
        BloomFilter.create<String>(Funnels.stringFunnel(Charset.defaultCharset()), 100, 0.99)

    private constructor(
        linearId: UniqueIdentifier = UniqueIdentifier(),
        bloomFilter: BloomFilter<String>,
        data: String,
        participants: List<AbstractParty>
    ) : this(participants, linearId) {
        if (!this::bloomFilter.isInitialized)
            this.bloomFilter = initBloomFilter()
        bloomFilter.put(data)
    }

    init {
        if (!this::bloomFilter.isInitialized)
            this.bloomFilter = initBloomFilter()
        this.bloomFilter.put("test")
    }

    fun checkContains(element: String) = bloomFilter.mightContain(element)

    fun copy(data: String, participants: List<AbstractParty>) =
        BloomFilterState(this.linearId, this.bloomFilter, data, participants)
}

class ExampleRPCSerializationWhitelist : SerializationWhitelist {
    // Add classes like this.
    override val whitelist = listOf(BloomFilter::class.java)
}
