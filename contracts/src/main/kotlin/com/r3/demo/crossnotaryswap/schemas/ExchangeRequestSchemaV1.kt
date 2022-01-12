package com.r3.demo.crossnotaryswap.schemas

import com.r3.demo.crossnotaryswap.types.RequestStatus
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.CordaSerializable
import java.util.*
import javax.persistence.*

object ExchangeRequestSchema

object ExchangeRequestSchemaV1 : MappedSchema(
    schemaFamily = ExchangeRequestSchema::class.java,
    version = 1,
    mappedTypes = listOf(ExchangeRequest::class.java)
)

@Entity
@CordaSerializable
class ExchangeRequest(

    @Id
    @Column(name = "request_id", nullable = false)
    var requestId: String = UUID.randomUUID().toString(),

    @Column(name = "buyer", nullable = false)
    var buyer: AbstractParty,

    @Column(name = "seller", nullable = false)
    var seller: AbstractParty,

    @Column(name = "buyer_asset_type", nullable = false)
    var buyerAssetType: String,

    @Column(name = "seller_asset_type", nullable = false)
    var sellerAssetType: String,

    @Column(name = "buyer_asset_qty", nullable = true)
    var buyerAssetQty: Long? = null,

    @Column(name = "seller_asset_qty", nullable = true)
    var sellerAssetQty: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", nullable = true, length = 12)
    var requestStatus: RequestStatus? = null,

    @Column(name = "reason", nullable = true)
    var reason: String? = null,

    @Column(name = "tx_id", nullable = true)
    var txId: String? = null,

    @Lob
    @Column(name = "transaction", nullable = true)
    var unsignedTransaction: ByteArray? = null

) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ExchangeRequest

        if (requestId != other.requestId) return false
        if (buyer != other.buyer) return false
        if (seller != other.seller) return false
        if (buyerAssetType != other.buyerAssetType) return false
        if (sellerAssetType != other.sellerAssetType) return false
        if (buyerAssetQty != other.buyerAssetQty) return false
        if (sellerAssetQty != other.sellerAssetQty) return false
        if (requestStatus != other.requestStatus) return false
        if (reason != other.reason) return false
        if (txId != other.txId) return false
        if (unsignedTransaction != null) {
            if (other.unsignedTransaction == null) return false
            if (!unsignedTransaction!!.contentEquals(other.unsignedTransaction!!)) return false
        } else if (other.unsignedTransaction != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = requestId.hashCode()
        result = 31 * result + buyer.hashCode()
        result = 31 * result + seller.hashCode()
        result = 31 * result + buyerAssetType.hashCode()
        result = 31 * result + sellerAssetType.hashCode()
        result = 31 * result + (buyerAssetQty?.hashCode() ?: 0)
        result = 31 * result + (sellerAssetQty?.hashCode() ?: 0)
        result = 31 * result + (requestStatus?.hashCode() ?: 0)
        result = 31 * result + (reason?.hashCode() ?: 0)
        result = 31 * result + (txId?.hashCode() ?: 0)
        result = 31 * result + (unsignedTransaction?.contentHashCode() ?: 0)
        return result
    }
}

