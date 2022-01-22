# Cross Notary Swap demo

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
`Defined an NFT of type KITTY with evolvable token id 274d272d-1e22-4816-b291-f7c3336b6d1b.
TxId: 0ABFAF5A62297265E664991644C6E0D3AE474C57D6A4EA96BB2179B0DF57CB6C on notary O=NotaryB, L=London, C=GB`

```shell
# issue the nft
curl --request POST "localhost:8080/api/cns/token/non-fungible/issuance" \
--header "Content-Type: application/json" \
--data-raw "{
    \"tokenIdentifier\" : \"274d272d-1e22-4816-b291-f7c3336b6d1b\",
    \"receiver\": \"O=PartyC, L=London, C=GB\"
}"
```
`Issued an NFT token to O=PartyC, L=London, C=GB with token identifier fd843f20-543b-4ad5-bdb4-74a9c5965abe.
TxId: B7B5F81989CC825B8723593D853408CE9DCCE5150A06CD02A53708B6B0104CE7 on notary O=NotaryB, L=London, C=GB`

```shell
#switch to party c and check balance
curl --request POST "localhost:8080/api/cns/switch-party/PartyC"

curl --request GET "localhost:8080/api/cns/balance/non-fungible/fd843f20-543b-4ad5-bdb4-74a9c5965abe"
```
`Found Non Fungible State : fd843f20-543b-4ad5-bdb4-74a9c5965abe of class com.r3.demo.crossnotaryswap.states.KittyToken(274d272d-1e22-4816-b291-f7c3336b6d1b)`

### Step 2: Bank issues INR to Buyer

```shell
curl --request POST "localhost:8080/api/cns/switch-party/PartyB"
```

```shell
curl --request POST "localhost:8080/api/cns/token/fungible/issuance" \
--header "Content-Type: application/json" \
--data-raw "{
    \"tokenIdentifier\": \"INR\",
    \"amount\": \"1500.50\",
    \"receiver\": \"O=PartyD, L=London, C=GB\"
}"
```
`Issued 1500.50 of INR to O=PartyD, L=London, C=GB.
TxId: 4822ACC51E97BC03D964F5A0C303C2A18200A1DA984C00971E31F7FC8366C2FE on notary O=NotaryA, L=London, C=GB`
```shell
# switch to party D and check balance of INR
curl --request POST "localhost:8080/api/cns/switch-party/PartyD"

curl --request GET "localhost:8080/api/cns/balance/fungible/INR"
```
`Balance of Fungible Token: 1500.50 TokenType(tokenIdentifier='INR', fractionDigits=2)`

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
        \"tokenIdentifier\": \"fd843f20-543b-4ad5-bdb4-74a9c5965abe\",
        \"type\": \"NON_FUNGIBLE_ASSET_REQUEST\"

    }
}"
```
`Registered a cross notary swap request with id: 5f6c683f-1bd3-4a23-bf85-c625b64f6ce1 with
details seller=O=PartyC, L=London, C=GB,
buyerAsset=AssetReqForm(tokenIdentifier='INR', amount=100),
sellerAsset=AssetReqForm(tokenIdentifier='fd843f20-543b-4ad5-bdb4-74a9c5965abe', amount=0)`

```shell
# switch to seller and approve the request
curl --request POST "localhost:8080/api/cns/switch-party/PartyC"

# approve the request
curl --request POST "localhost:8080/api/cns/token/swap/approval" \
--header "Content-Type: application/json" \
--data-raw "{
    \"requestId\" : \"5f6c683f-1bd3-4a23-bf85-c625b64f6ce1\",
    \"approved\" : true
}"
```
`Approved a cross notary swap request with id: 5f6c683f-1bd3-4a23-bf85-c625b64f6ce1`
## Relevant Flows