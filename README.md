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

![Data Distribution](docs/assets/data_distribution.png "Data Distribution")

### On-boarding
From the shell of Party A (i.e. BNO in this case), run the following
```shell
flow start MembershipFlows$CreateMyNetworkFlow defaultGroupName: myGroup001
```
You will be presented with an output like the below which contains the created `networkId` and the unique Membership state identifier (`membershipId`) for the BNO.

```
Flow completed with result: Created Network with ID: 00f217f9-f6fb-4db8-8ccc-e2a638e86c24, membershipId: 3606e91e-d956-47f3-a7a3-b5acebc79874, and role BNO
```

Next we have to onboard other participants in the network. These participants can be regular members or members with Data administration permissions. There are two types of permissions in this example that a member might have with respect to data. The mapping is shown below.

|Role|Permissions|Party in this example|
|----|-----------|---------------------|
|BNORole|CAN_MODIFY_GROUPS,CAN_MODIFY_BUSINESS_IDENTITY, CAN_MODIFY_ROLE, CAN_REVOKE_MEMBERSHIP, CAN_SUSPEND_MEMBERSHIP, CAN_ACTIVATE_MEMBERSHIP|PartyA|
|DataAdminRole|CAN_DISTRIBUTE_DATA, CAN_MANAGE_DATA|PartyA|
|NetworkMemberRole|CAN_DISTRIBUTE_DATA|PartyC|

> Note: A member with `BNORole` (with `AdminPermission`) cannot hold `DataAdminRole` at the same time.

We need to onboard PartyB to be the Data Admin for our Business network. 

From the shell of PartyA, execute the following:

```shell
flow start OnboardMyNetworkParticipant networkId: <Network ID>, onboardedParty: PartyB
```

You should be presented with an output:

```shell
Onboarded PartyB on network 00f217f9-f6fb-4db8-8ccc-e2a638e86c24 with membershipId: f13f676d-c7c4-44ac-acfe-e97cc75f1e18
```

---
> Note you can also make use of the `RequestMyNetworkMembership` flow to allow PartyB to request membership to a network.

From the shell of PartyB, run

```shell
flow start RequestMyNetworkMembership networkId: <Network ID>
```

From the shell of PartyA (BNO), approve the request

```shell
flow start ApproveMyNetworkMembership membershipId: <Membership ID of the requested participant>
```
---

Check the roles of participants before we proceed to the next step:

```shell
flow start QueryRolesForMembershipFlow membershipId: <Membership ID of BNO>
```
The above should yield a result like: `Flow completed with result: BNO`

```shell
flow start QueryRolesForMembershipFlow membershipId: <Membership ID of PartyB>
```
The above should yield a result like: `Flow completed with result: `

This means that PartyB does not have any roles yet. Now let us assign the Data admin permissions to PartyB

```shell
flow start AssignDataAdminRoleFlow membershipId: <Membership ID of PartyB>
```

Querying the role of PartyB 

```shell
flow start QueryRolesForMembershipFlow membershipId: <Membership ID of PartyB>
```
should now yield the following result `Flow completed with result: DataAdmin`


From the shell of PartyA, execute the following

```shell
flow start OnboardMyNetworkParticipant networkId: <NetworkID>, onboardedParty: PartyC
```

You should be presented with an output:

```shell
Onboarded PartyC on network 00f217f9-f6fb-4db8-8ccc-e2a638e86c24 with membershipId: 42087884-e3ce-4731-9d3c-36b1595784d2
```


### Group Creation

From the shell of PartyA, execute the following:

```shell
flow start CreateMyGroupFlow networkId: 00f217f9-f6fb-4db8-8ccc-e2a638e86c24, groupName: data-grp-001, membershipIds:["3606e91e-d956-47f3-a7a3-b5acebc79874", "f13f676d-c7c4-44ac-acfe-e97cc75f1e18", "42087884-e3ce-4731-9d3c-36b1595784d2"]
```
You should be presented with a group id

```
Flow completed with result: Group created with id: ba1b9ee5-e2e3-4c81-9005-3f473e21692b
```

### Data Creation

Now that we have onboarded the participants with the roles, let us create some data from PartyB (Data Admin) and see if they are distributed to the participants


```shell
flow start CreateDataFlow data: "Hello Sample Data", groupIds: [<Group ID>]
```

should yield a result like

```
Flow completed with result: Data with id: bf88d94d-283f-4f7b-85b0-ea6d0e517826 created and distributed to groups: ba1b9ee5-e2e3-4c81-9005-3f473e21692b, TxId: 6DE8BE807D3E7C94ECA09007E29131FF708C0E44920FDF4A1BF3086F1DB868B1
```

### Query Data

From each of PartyA (BNO), PartyB (DataAdmin), PartyC execute the following.

```shell
run vaultQuery contractStateType: com.r3.demo.datadistribution.contracts.GroupDataAssociationState
```

From PartyB you should get a result as below.

```shell
states:
- state:
    data: !<com.r3.demo.datadistribution.contracts.GroupDataAssociationState>
      linearId:
        externalId: null
        id: "bf88d94d-283f-4f7b-85b0-ea6d0e517826"
      value: "Hello Sample Data"
      associatedGroupStates:
      - pointer:
          externalId: null
          id: "ba1b9ee5-e2e3-4c81-9005-3f473e21692b"     # <---- Group state reference
        type: "net.corda.bn.states.GroupState"
        isResolved: true
      participants:
      - "O=PartyB, L=London, C=GB"    # <---- PartyB is the participant since it has Data Admin Role
    contract: "com.r3.demo.datadistribution.contracts.GroupDataAssociationContract"
    notary: "O=Notary, L=London, C=GB"
    encumbrance: null
    constraint: !<net.corda.core.contracts.SignatureAttachmentConstraint>
      key: "aSq9DsNNvGhYxYyqA9wd2eduEAZ5AXWgJTbTEw3G5d2maAq8vtLE4kZHgCs5jcB1N31cx1hpsLeqG2ngSysVHqcXhbNts6SkRWDaV7xNcr6MtcbufGUchxredBb6"
  ref:
    txhash: "6DE8BE807D3E7C94ECA09007E29131FF708C0E44920FDF4A1BF3086F1DB868B1"
    index: 0
statesMetadata:
- ref:
    txhash: "6DE8BE807D3E7C94ECA09007E29131FF708C0E44920FDF4A1BF3086F1DB868B1"
    index: 0
  contractStateClassName: "com.r3.demo.datadistribution.contracts.GroupDataAssociationState"
  recordedTime: "2021-09-21T12:24:38.003Z"
  consumedTime: null
  status: "UNCONSUMED"
  notary: "O=Notary, L=London, C=GB"
  lockId: null
  lockUpdateTime: null
  relevancyStatus: "RELEVANT"  # <---- PartyB is the participant, hence this state is relevant
  constraintInfo:
    constraint:
      key: "aSq9DsNNvGhYxYyqA9wd2eduEAZ5AXWgJTbTEw3G5d2maAq8vtLE4kZHgCs5jcB1N31cx1hpsLeqG2ngSysVHqcXhbNts6SkRWDaV7xNcr6MtcbufGUchxredBb6"
totalStatesAvailable: -1
stateTypes: "UNCONSUMED"
otherResults: []
```

From PartyA and PartyC you should get the same result but with the difference in relevancy as below.

```shell
states:
- state:
    data: !<com.r3.demo.datadistribution.contracts.GroupDataAssociationState>
      linearId:
        externalId: null
        id: "bf88d94d-283f-4f7b-85b0-ea6d0e517826"
      value: "Hello Sample Data"
      associatedGroupStates:
      - pointer:
          externalId: null
          id: "2e064ba9-6959-4d54-b840-025535de4eb0"
        type: "net.corda.bn.states.GroupState"
        isResolved: true
      participants:
      - "O=PartyB, L=London, C=GB"
    contract: "com.r3.demo.datadistribution.contracts.GroupDataAssociationContract"
    notary: "O=Notary, L=London, C=GB"
    encumbrance: null
    constraint: !<net.corda.core.contracts.SignatureAttachmentConstraint>
      key: "aSq9DsNNvGhYxYyqA9wd2eduEAZ5AXWgJTbTEw3G5d2maAq8vtLE4kZHgCs5jcB1N31cx1hpsLeqG2ngSysVHqcXhbNts6SkRWDaV7xNcr6MtcbufGUchxredBb6"
  ref:
    txhash: "6DE8BE807D3E7C94ECA09007E29131FF708C0E44920FDF4A1BF3086F1DB868B1"
    index: 0
statesMetadata:
- ref:
    txhash: "6DE8BE807D3E7C94ECA09007E29131FF708C0E44920FDF4A1BF3086F1DB868B1"
    index: 0
  contractStateClassName: "com.r3.demo.datadistribution.contracts.GroupDataAssociationState"
  recordedTime: "2021-09-21T12:24:38.226Z"
  consumedTime: null
  status: "UNCONSUMED"
  notary: "O=Notary, L=London, C=GB"
  lockId: null
  lockUpdateTime: null
  relevancyStatus: "NOT_RELEVANT" # <---- PartyB is the participant, hence this state is not relevant
  constraintInfo:
    constraint:
      key: "aSq9DsNNvGhYxYyqA9wd2eduEAZ5AXWgJTbTEw3G5d2maAq8vtLE4kZHgCs5jcB1N31cx1hpsLeqG2ngSysVHqcXhbNts6SkRWDaV7xNcr6MtcbufGUchxredBb6"
totalStatesAvailable: -1
stateTypes: "UNCONSUMED"
otherResults: []
```