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
flow start ExampleFlows$InitiatorFlow commandString: CreateEnclosed, txObject: { "enclosedValue":"test-inner"}, counterParty: PartyB
```


### Update Inner

```shell
flow start ExampleFlows$InitiatorFlow commandString: UpdateEnclosed, txObject: { "enclosedValue":"new-test-inner", "innerIdentifier":"f7336b00-27d0-4ecf-8eb4-e30bb794c3a1"}, counterParty: PartyB
```


### Create outer with reference to the inner

```shell
flow start ExampleFlows$InitiatorFlow commandString: CreateEncapsulating, txObject: { "innerIdentifier":"f7336b00-27d0-4ecf-8eb4-e30bb794c3a1", "enclosingValue":"outer-value"}, counterParty: PartyB
```

### Create outer with reference to the same inner

```shell
flow start ExampleFlows$InitiatorFlow commandString: CreateEncapsulating, txObject: { "innerIdentifier":"f7336b00-27d0-4ecf-8eb4-e30bb794c3a1", "enclosingValue":"some-other-outer-value"}, counterParty: PartyB
```

### Update Inner Again

```shell
flow start ExampleFlows$InitiatorFlow commandString: UpdateEnclosed, txObject: { "enclosedValue":"brand-new-inner", "innerIdentifier":"f7336b00-27d0-4ecf-8eb4-e30bb794c3a1"}, counterParty: PartyB
```

### Update the last outer keeping the inner same

```shell
flow start ExampleFlows$InitiatorFlow commandString: UpdateEncapsulating, txObject: { "enclosingValue":"brand-new-outer", "innerIdentifier":"f7336b00-27d0-4ecf-8eb4-e30bb794c3a1", "outerIdentifier":"3c1f082c-35b8-4fd6-81f3-0317b0a17268"}, counterParty: PartyB
```

### Query the DB to check the reference to the inner
```sql
SELECT * FROM ENCAPSULATED inr JOIN ENCAPSULATING outr ON (inr.ID = outr.ENCAPSULATED_ID) WHERE inr.TRANSACTION_ID IN (SELECT TRANSACTION_ID FROM VAULT_STATES vs WHERE vs.STATE_STATUS=0) AND outr.TRANSACTION_ID IN (SELECT TRANSACTION_ID FROM VAULT_STATES vs2 WHERE vs2.STATE_STATUS=0)

```