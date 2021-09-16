# CorDapp Samples

## 1. Independent Evolution of Inner and Outer States

Demonstrates the independent evolution of an Encapsulated State within an Encapsulating state. The inner one, i.e. the encapsulated one is linked to the encapsulating with a `LinearPointer`.

Both the `Encapsulating` and `Encapsulated` states are modelled as `LinearState` to allow them to evolve independently.

```
|-------------------------------|
|      Encapsulating State      |---> Independently persisted on DB
|       |                       |
|       | (linear pointer)      |
|       v                       |
|   |----------------------|    |
|   |   Encapsulated State |----|---> Independently persisted on DB
|   |----------------------|    |
|-------------------------------|
```



From Party A execute the following:

### Create Inner

```shell
flow start EncapsulationDemoFlows$InitiatorFlow commandString: CreateEncapsulated, txObject: { "enclosedValue":"test-inner"}, counterParty: PartyB
```

You will get an output like the one below:

```shell
Flow completed with result: Encapsulated created with identifier <INNER_IDENTIFIER>, Tx ID: 7DAFB3D816B67FE61F6C80C667AC36E4ECBCB3F27B18FFE5654E01FC6A7230C2
```

> Be sure to update the `INNER_IDENTIFIER` with the above encapsulated identifier.

### Update Inner

```shell
flow start EncapsulationDemoFlows$InitiatorFlow commandString: UpdateEncapsulated, txObject: { "enclosedValue":"new-test-inner", "innerIdentifier":"<INNER_IDENTIFIER>"}, counterParty: PartyB
```


### Create outer with reference to the inner

```shell
flow start EncapsulationDemoFlows$InitiatorFlow commandString: CreateEncapsulating, txObject: { "innerIdentifier":"<INNER_IDENTIFIER>", "enclosingValue":"outer-value"}, counterParty: PartyB
```

You will get an output like the one below:

```shell
Flow completed with result: Encapsulating created, ID: a06a0d78-ad7b-4f1e-8674-b98ce1f4ecac, Tx ID: 1C2294E44A9CC9595F32C20511822B98E289613D193A07177DC41297AA4CAF3A

```
> Be sure to update the `OUTER_IDENTIFER` with the above encapsulating identifier.


### Create outer with reference to the same inner

```shell
flow start EncapsulationDemoFlows$InitiatorFlow commandString: CreateEncapsulating, txObject: { "innerIdentifier":"<INNER_IDENTIFIER>", "enclosingValue":"some-other-outer-value"}, counterParty: PartyB
```

### Update Inner Again

```shell
flow start EncapsulationDemoFlows$InitiatorFlow commandString: UpdateEncapsulated, txObject: { "enclosedValue":"brand-new-inner", "innerIdentifier":"<INNER_IDENTIFIER>"}, counterParty: PartyB
```

### Update the last outer keeping the inner same

```shell
flow start EncapsulationDemoFlows$InitiatorFlow commandString: UpdateEncapsulating, txObject: { "enclosingValue":"brand-new-outer", "innerIdentifier":"<INNER_IDENTIFIER>", "outerIdentifier":"<OUTER_IDENTIFIER>"}, counterParty: PartyB
```

### Query the DB to check the reference to the inner
```sql
SELECT * FROM ENCAPSULATED inr JOIN ENCAPSULATING outr ON (inr.ID = outr.ENCAPSULATED_ID) WHERE inr.TRANSACTION_ID IN (SELECT TRANSACTION_ID FROM VAULT_STATES vs WHERE vs.STATE_STATUS=0) AND outr.TRANSACTION_ID IN (SELECT TRANSACTION_ID FROM VAULT_STATES vs2 WHERE vs2.STATE_STATUS=0)

```

## 2. Top-Down Data distribution in a Business Network

```shell
flow start MembershipFlows$CreateMyNetworkFlow defaultGroupName: myGroup001
```

```
Created membership request for Network 5fa69896-7188-4815-860b-d8213f7ff2e6 with membershipId : d34aa589-dc50-43e2-8f60-0076c0365679. Please share this with the BNO of the network
```

Self assign Data admin role

```shell
flow start AssignDataAdminRoleFlow membershipId: <Membership ID of BNO>
```

#### OPTION A - Onboarding participants from BNO

```shell
flow start OnboardMyNetworkParticipant networkId: <Network ID>, onboardedParty: PartyB

flow start OnboardMyNetworkParticipant networkId: <Network ID>, onboardedParty: PartyC
```

#### OPTION B - Request Membership to BNO

```shell
flow start RequestMyNetworkMembership networkId: <Network ID>
```

Approve the request from BNO

```shell
flow start ApproveMyNetworkMembership membershipId: <Membership ID of the requested participant>

```


