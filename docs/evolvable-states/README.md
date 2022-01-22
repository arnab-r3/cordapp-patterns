
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