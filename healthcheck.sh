#!/usr/bin/env bash

URL = ${VAMP_PERSISTENCE_KEY_VALUE_STORE_VAULT_URL}


RESULT=$(curl ${URL}/v1/sys/health)

echo "Vault healthcheck result ${RESULT}"

return 0