#!/usr/bin/env bash

cd workflows
gradle clean build -x test
cd ..
cp workflows/build/libs/workflows-0.1.jar build/nodes/PartyA/cordapps
cp workflows/build/libs/workflows-0.1.jar build/nodes/PartyB/cordapps
cp workflows/build/libs/workflows-0.1.jar build/nodes/PartyC/cordapps
cp workflows/build/libs/workflows-0.1.jar build/nodes/PartyD/cordapps
cp workflows/build/libs/workflows-0.1.jar build/nodes/NotaryA/cordapps
cp workflows/build/libs/workflows-0.1.jar build/nodes/NotaryB/cordapps
cd contracts
gradle clean build -x test
cd ..
cp contracts/build/libs/contracts-0.1.jar build/nodes/PartyA/cordapps
cp contracts/build/libs/contracts-0.1.jar build/nodes/PartyB/cordapps
cp contracts/build/libs/contracts-0.1.jar build/nodes/PartyC/cordapps
cp contracts/build/libs/contracts-0.1.jar build/nodes/PartyD/cordapps
cp contracts/build/libs/contracts-0.1.jar build/nodes/NotaryA/cordapps
cp contracts/build/libs/contracts-0.1.jar build/nodes/NotaryB/cordapps


