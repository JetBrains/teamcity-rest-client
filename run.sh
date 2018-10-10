#!/bin/bash

ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

set -e -x

cd "$ROOT/teamcity-rest-client-impl/src/test/environment"
docker-compose rm --stop --force ||:
docker-compose build --pull
docker-compose up "$@"
