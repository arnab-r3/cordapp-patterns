
## 4. Linking a custom Schema with a Group and validating dynamic data

In this example we will see the working of `GroupDataAssociationState` in conjunction with a `SchemaState`. The `SchemaState` acts as a `ReferenceState` for arbitrary data creation in that group when it is referred during the data creation process.

The `SchemaState` acts to validate the data creation during various lifecycle stages (CRUD) and can be defined programmatically through APIs.

Run the Notary & all three nodes using
```shell
# clean, build & prepare nodes
./gradlew clean deployNodes

# run nodes for all parties
build/nodes/runnodes

# in a new terminal run the spring boot server 
./gradlew :clients:runTemplateServer
```

Open the postman collection in `clients/postman/cordapp-patterns.postman_collection.json` in Postman and execute the following steps in order as Party A (default).

1. create network (as party A)
```shell   
Created Network with ID: e7f90735-b11e-49ad-97f5-ff40f81961d9, membershipId: aab4639a-6d00-4582-90f4-ac9fefe313b3, and role BNO
```

2. onboard member `/api/network/<network id from prev step>/member` for PartyB and PartyC
```shell
Onboarded O=PartyB, L=London, C=GB on network e7f90735-b11e-49ad-97f5-ff40f81961d9 with membershipId: 324a4942-febc-4f2a-92e5-1fd626a2534a
```

3. assign data admin role `/api/network/membership/<membership-id of Party A from step 1>/role/data-admin`
> You will get a 406 response, which is a bug and can be ignored now

4. create group `/api/network/<network id from step 1>/group`
```json
{
    "groupName": "new data group",
    "membershipIds": [
        "<membership id of party a from step 1>",
        "<membership id of party b from step 2>",
        "<membership id of party b from step 2>"
    ]
}
```
```shell
Group created with id: e1aa190c-a609-489c-9dad-b27461c7afb5
```
5. create schema `/api/groups/data/schema`
```json
{
    "groupIds": [
        "<group id from step 4>"
    ],
    "schema": {
        "name": "one schema",
        "description": "desc of zero schema",
        "version": "v1",
        "attributes": [
            {
                "name": "firstAttribute",
                "dataType": "INTEGER",
                "mandatory": true,
                "regex": "[0-9]{3,10}",
                "associatedEvents": [
                    "Create"
                ]
            },
            {
                "name": "secondAttribute",
                "dataType": "STRING",
                "mandatory": true,
                "regex": "[a-z0-9]{3,10}",
                "associatedEvents": [
                    "Create"
                ]
            },
            {
                "name": "thirdAttribute",
                "dataType": "BIG_DECIMAL",
                "mandatory": false,
                "regex": "^\\d*\\.?\\d*$",
                "associatedEvents": [
                    "Create"
                ]
            }
        ],
        "parties": [
            "O=PartyA, L=London, C=GB",
            "O=PartyB, L=London, C=GB",
            "O=PartyC, L=London, C=GB"
        ]
    }
}
```   

```shell
Created Schema with id b8b0ebca-5dc9-4c0e-9040-ab5836f85fbd and following are the group association details.
Data with id: 99667efd-6949-4eef-a223-7c06f7e63f6d created and distributed to groups: e1aa190c-a609-489c-9dad-b27461c7afb5, TxId: C6AF7AA8BE1AF4CB1E7FFC26F582252803C73F3E98CFD073F16757F8B157DC42
```

6. create data backed by schema `/api/groups/data/<data with id from step 5>/schema/<schema with id from step 5>/kv`

```json
{
  "data": {
    "firstAttribute": "126",
    "secondAttribute": "abc",
    "thirdAttribute": "123.45"
  }
}
```
```shell
Performed CREATE on Schema-backed KV with identifier 24d136ee-4a2b-4dae-85a8-a749ff903858 using schema id: c2c1d63a-bbf1-489e-96ef-5b0a8e84683c, linked with GroupDataAssociation id: 68eda336-9ac0-4aec-a2d9-4ef995b5d61f
```

7. update data backed by schema `/api/groups/data/<data with id from step 5>/schema/<schema with id from step 5>/kv/<kv with identifier from step 6>`

```json
{
    "data": {
        "firstAttribute": "333",
        "secondAttribute":"abadc",
        "thirdAttribute": "9938.45"
    }
}
```
```shell
Performed UPDATE on Schema-backed KV with identifier 36a033de-850b-47a1-b275-89bc71555d08 using schema id: c2c1d63a-bbf1-489e-96ef-5b0a8e84683c, linked with GroupDataAssociation id: 68eda336-9ac0-4aec-a2d9-4ef995b5d61f
```

TODO Improvement Items

- issue with representing the signed transaction in REST output.
- change the representation of the output to objects
- add rest apis to get
    - the list of networks
    - the list of groups in the network
    - the list of group data association state in the network
    - the list of schemas associated with the group data association state
    - data entries created with the group data association state
      done - update the data created against the schema, validate update lifecycle
    - validate and check the distribution of the data backed by the schema, it should be same as that of the schema
    - check if the schema participants are a subset of that of the group participants
    - add provision to delete schema
    - add provision to add a new schema to existing group data
    - add contract checks in group data association contract if we have rights to create in every group
    - simplify data distribution for just one group
    - data distribution point when primary reference state holder is unavailable
    - validation for nested schema