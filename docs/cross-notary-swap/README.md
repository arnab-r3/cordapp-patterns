# 5. Cross Notary Swap demo

## Running the Demo via REST

### Parties Involved

- PartyA - Artist - Creates and issues Kitty NFT to Seller
- PartyB - Bank - Issues INR Currency to Buyer
- PartyC - Seller - Interested in selling Kitty NFT
- PartyD - Buyer - Interested in buying Kitty NFT in exchange with INR

### Step 1: Artist Creates NFT and issues it to Seller

```shell
# use oarty A
curl --request POST "localhost:8080/api/cns/switch-party/PartyA"
```

```shell
# define nft
curl --request POST "localhost:8080/api/cns/token-definition/non-fungible" \
--header "Content-Type: application/json" \
--data-raw "{
    \"properties\": {
        \"kittyName\" : \"Hello Kitty\"
    },
    \"type\":\"KITTY\",
    \"maintainers\":\"O=PartyA,L=London,C=GB\"
}"
```
`Defined an NFT of type KITTY with evolvable token id b4d960e1-4f63-41a1-ae0d-3b8a14add8d8.
TxId: E97E331C07B48DF40D1EA771F0777B49EE499BDE199448C9F6A2E66584D34EDA on notary O=NotaryB, L=London, C=GB`

```shell
# issue the nft
curl --request POST "localhost:8080/api/cns/token/non-fungible/issuance" \
--header "Content-Type: application/json" \
--data-raw "{
    \"tokenIdentifier\" : \"b4d960e1-4f63-41a1-ae0d-3b8a14add8d8\",
    \"receiver\": \"O=PartyC, L=London, C=GB\"
}"
```
`Issued an NFT token to O=PartyC, L=London, C=GB with token identifier 3ba97151-b08b-4033-8739-e41ea678a849.
TxId: BA5C7B91CEB5061440E4E593377B996F8E432789230298CD9996029C7B5A4376 on notary O=NotaryB, L=London, C=GB`

```shell
#switch to party c and check balance
curl --request POST "localhost:8080/api/cns/switch-party/PartyC"

curl --request GET "localhost:8080/api/cns/balance/non-fungible/3ba97151-b08b-4033-8739-e41ea678a849"
```
`Found Non Fungible State : 3ba97151-b08b-4033-8739-e41ea678a849 of class com.r3.demo.crossnotaryswap.states.KittyToken(b4d960e1-4f63-41a1-ae0d-3b8a14add8d8)`

### Step 2: Bank issues INR to Buyer

```shell
curl --request POST "localhost:8080/api/cns/switch-party/PartyB"
```

```shell
curl --request POST "localhost:8080/api/cns/token/fungible/issuance" \
--header "Content-Type: application/json" \
--data-raw "{
    \"tokenIdentifier\": \"INR\",
    \"amount\": \"100\",
    \"receiver\": \"O=PartyD, L=London, C=GB\"
}"
```
`Issued 100 of INR to O=PartyD, L=London, C=GB.
TxId: 117D136E229237AFE7CA7F0BBAC2C10C866013336EC98CD54C99F56DC6887804 on notary O=NotaryA, L=London, C=GB`
```shell
# switch to party D and check balance of INR
curl --request POST "localhost:8080/api/cns/switch-party/PartyD"

curl --request GET "localhost:8080/api/cns/balance/fungible/INR"
```
`Balance of Fungible Token: 100.00 TokenType(tokenIdentifier='INR', fractionDigits=2)`

### Step 3: Cross Notary Swap
```shell
curl --request POST "localhost:8080/api/cns/switch-party/PartyD"

# issue a cross notary swap request from the buyer

curl --request POST "localhost:8080/api/cns/token/swap/request" \
--header "Content-Type: application/json" \
--data-raw "{
    \"seller\": \"O=PartyC,L=London,C=GB\",
    \"buyerAsset\": {
        \"tokenIdentifier\": \"INR\",
        \"amount\": \"100\",
        \"type\":\"FUNGIBLE_ASSET_REQUEST\"
    },
    \"sellerAsset\": {
        \"tokenIdentifier\": \"3ba97151-b08b-4033-8739-e41ea678a849\",
        \"type\": \"NON_FUNGIBLE_ASSET_REQUEST\"

    }
}"
```
`Registered a cross notary swap request with id: 7d1900a3-55b9-4e32-9756-e3138a99cafe with
details seller=O=PartyC, L=London, C=GB,
buyerAsset=AssetReqForm(tokenIdentifier='INR', amount=100),
sellerAsset=AssetReqForm(tokenIdentifier='3ba97151-b08b-4033-8739-e41ea678a849', amount=null)`

```shell
# switch to seller and approve the request
curl --request POST "localhost:8080/api/cns/switch-party/PartyC"

# approve the request
curl --request POST "localhost:8080/api/cns/token/swap/approval" \
--header "Content-Type: application/json" \
--data-raw "{
    \"requestId\" : \"7d1900a3-55b9-4e32-9756-e3138a99cafe\",
    \"approved\" : true
}"
```
`Approved a cross notary swap request with id: 7d1900a3-55b9-4e32-9756-e3138a99cafe`

```shell
# Switch to D and execute the swap
curl --request POST "localhost:8080/api/cns/switch-party/PartyD"
curl --request POST "localhost:8080/api/cns/token/swap/execute/7d1900a3-55b9-4e32-9756-e3138a99cafe"
```
`Completed the swap with request id 7d1900a3-55b9-4e32-9756-e3138a99cafe`

### Step 4: Check the Balances
```shell
#switch to Seller (PartyC) and check balance
curl --request POST "localhost:8080/api/cns/switch-party/PartyC"
# check NFT Balance
curl --request GET "localhost:8080/api/cns/balance/non-fungible/3ba97151-b08b-4033-8739-e41ea678a849"
```
`Found Non Fungible State : `

```shell
# Check INR Balance
curl --request GET "localhost:8080/api/cns/balance/fungible/INR"
```
`Balance of Fungible Token: 100.00 TokenType(tokenIdentifier='INR', fractionDigits=2)`

```shell
# Check Buyer NFT Balance
curl --request GET "localhost:8080/api/cns/balance/non-fungible/3ba97151-b08b-4033-8739-e41ea678a849"
```
`Found Non Fungible State : 3ba97151-b08b-4033-8739-e41ea678a849 of class com.r3.demo.crossnotaryswap.states.KittyToken(b4d960e1-4f63-41a1-ae0d-3b8a14add8d8)`

```shell
# Check Buyer INR Balance
curl --request GET "localhost:8080/api/cns/balance/fungible/INR"
```
`Balance of Fungible Token: 0.00 TokenType(tokenIdentifier='INR', fractionDigits=2)`

## Relevant Flows

### Initiating Flows

1. `InitiateExchangeFlows` - used to initiate the exchange request from the buyer and the seller and also have the seller approve or deny the exchange request. Possible types of exchanges: `Fungible Token` <> `Fungible Token`, `Non-Fungible Token` <> `Non-Fungible Token`, `Fungible Token` <> `Non-Fungible Token`
2. `CrossNotarySwapDriverFlows` - Once the exchange request has been approved by the Seller, the Buyer can start the `CrossNotarySwapDriverFlows.BuyerDriverFlow` to trigger the swap. This will be merged with the responder flow of the Buyer once the seller approves the request.

### Inline Flows

Legend - (n) **Flow** - Initiated flow, *StartingFlowHandler* - corresponding handler at counterparty


|Buyer|Description|Seller|Description|
|-----|------|-----------|-----------|
|(1) **DraftTransferOfOwnershipFlow**|Offers an unsigned `WireTransaction` to the seller transferring the promised asset as per the `ExchangeRequest` with a defined time window|*DraftTransferOfOwnershipFlowHandler*|Checks the `WireTransaction` against the `Exchange Request`, pull the necessary dependent (input and reference) transactions from the Buyer and checks if back chain satisfies contract logic.|
|*OfferEncumberedTokensFlowHandler*|Ensures the encumbered tokens transfers the promised tokens to the composite key as per the `ExchangeRequest`|(2) **OfferEncumberedTokensFlow**|Creates a composite key having the Public Keys of the Seller and the Buyer and transfers the tokens to this composite key, cyclically encumbers all outputs along with a `LockState` and finalizes the transaction within the time window. The `LockState` can only be spent(unlocking the encumbered tokens) only if the Buyer can provide the signature of the Notary on the `WireTransaction`|
|(3a) **SignAndFinalizeTransferOfOwnershipFlow**|Signs the original `WireTransaction` transferring the assets to the Seller within the time window|*SignAndFinalizeTransferOfOwnershipFlowHandler*|Receives the transaction|
|(4) **UnlockEncumberedTokensFlow**|Unlocks the encumbered outputs that the seller has sent and transfers them to the Buyer's key using the signature of the notary on the finalized `WireTransaction`|*UnlockEncumberedTokensFlowHandler*|Receives the transaction|
|*RevertEncumberedTokensFlowHandler*|(3b) **RevertEncumberedTokensFlow**|If the buyer does not finalize the `WireTransaction` using the `SignAndFinalizeTransferOfOwnershipFlow` then the seller can revert the encumbered tokens and transfer them back to herself, only after the time window set by the buyer has expired|