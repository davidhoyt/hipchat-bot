#!/bin/bash -e

cd $(cd -P -- "$(dirname -- "$0")" && pwd -P)

docker build -t davidhoyt/hipchat-bot:0.0.1 .

