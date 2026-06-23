#!/bin/bash

set -a
# shellcheck source=.env
source "$(dirname "$0")/.env"
set +a

./gradlew bootRun
