#!/bin/bash -e

cd $(cd -P -- "$(dirname -- "$0")" && pwd -P)

sbt clean universal:packageZipTarball

docker build -t davidhoyt/hipchat-bot:0.0.1 .

