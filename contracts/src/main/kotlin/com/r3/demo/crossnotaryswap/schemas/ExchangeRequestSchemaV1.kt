package com.r3.demo.crossnotaryswap.schemas

import com.r3.demo.crossnotaryswap.types.AssetRequestType
import com.r3.demo.crossnotaryswap.types.RequestStatus
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.CordaSerializable
import javax.persistence.*

object ExchangeRequestSchema

object ExchangeRequestSchemaV1 : MappedSchema(
    schemaFamily = ExchangeRequestSchema::class.java,
    version = 1,
    mappedTypes = listOf(ExchangeRequest::class.java)
)

@Entity
@CordaSerializable
class ExchangeRequest {

    @Id
    @Column(name = "request_id", nullable = false)
    val requestId: String

    @Column(name = "buyer", nullable = false)
    val buyer: AbstractParty

    @Column(name = "seller", nullable = false)
    val seller: AbstractParty

    @Column(name = "buyer_asset_id", nullable = false)
    private lateinit var buyerAssetTokenIdentifier: String

    @Column(name = "seller_asset_id", nullable = false)
    private lateinit var sellerAssetTokenIdentifier: String

    @Column(name = "buyer_asset_qty", nullable = true)
    private var buyerAssetQty: Long? = null

    @Column(name = "seller_asset_qty", nullable = true)
    private var sellerAssetQty: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "buyer_asset_request_type", nullable = false)
    private lateinit var buyerAssetRequestType: AssetRequestType

    @Enumerated(EnumType.STRING)
    @Column(name = "seller_asset_request_type", nullable = false)
    private lateinit var sellerAssetRequestType: AssetRequestType

    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", nullable = true, length = 12)
    var requestStatus: RequestStatus? = null

    @Column(name = "reason", nullable = true)
    var reason: String? = null

    @Column(name = "tx_id", nullable = true)
    var txId: String? = null

    @Lob
    @Column(name = "transaction", nullable = true)
    var unsignedTransaction: ByteArray? = null

    var buyerAssetRequest: DBAssetRequest
        get() = DBAssetRequest(buyerAssetTokenIdentifier, buyerAssetRequestType, buyerAssetQty)
        set(value) {
            buyerAssetTokenIdentifier = value.tokenIdentifier
            buyerAssetRequestType = value.assetRequestType
            buyerAssetQty = value.amount
        }

    var sellerAssetRequest: DBAssetRequest
        get() = DBAssetRequest(sellerAssetTokenIdentifier, sellerAssetRequestType, sellerAssetQty)
        set(value) {
            sellerAssetTokenIdentifier = value.tokenIdentifier
            sellerAssetRequestType = value.assetRequestType
            sellerAssetQty = value.amount
        }


    constructor(
        requestId: String,
        buyer: AbstractParty,
        seller: AbstractParty,
        buyerAssetRequest: DBAssetRequest,
        sellerAssetRequest: DBAssetRequest
    ) {
        this.requestId = requestId
        this.buyer = buyer
        this.seller = seller
        this.buyerAssetRequest = buyerAssetRequest
        this.sellerAssetRequest = sellerAssetRequest
    }

    constructor(
        requestId: String,
        buyer: AbstractParty,
        seller: AbstractParty,
        buyerAssetRequest: DBAssetRequest,
        sellerAssetRequest: DBAssetRequest,
        requestStatus: RequestStatus?,
        reason: String?,
        txId: String?,
        unsignedTransaction: ByteArray?
    ) {
        this.buyer = buyer
        this.seller = seller
        this.requestId = requestId
        this.requestStatus = requestStatus
        this.reason = reason
        this.txId = txId
        this.unsignedTransaction = unsignedTransaction
        this.buyerAssetRequest = buyerAssetRequest
        this.sellerAssetRequest = sellerAssetRequest
    }


}

@CordaSerializable
open class DBAssetRequest(
    val tokenIdentifier: String,
    val assetRequestType: AssetRequestType,
    val amount: Long? = null
) {
    override fun toString(): String {
        return "AssetRequest(tokenIdentifier='$tokenIdentifier', assetRequestType=$assetRequestType, amount=$amount)"
    }
}


