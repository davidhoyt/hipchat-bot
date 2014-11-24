#!/bin/bash

docker run \
  -d \
  --net=host \
  --name=hipchat-bot \
  -e SCALABOT_ENABLED=true \
  -e 'SCALABOT_ROOMS=xxxxx_scala' \
  -e SCALABOT_USERNAME=xxxxx_xxxxxxx \
  -e SCALABOT_PASSWORD=xxxxxx \
  -e SCALABOT_MENTIONNAME=scala \
  -e 'SCALABOT_NICKNAME=Scala Bot' \
  davidhoyt/hipchat-bot:0.0.1

