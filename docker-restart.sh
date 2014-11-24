#!/bin/bash -e

cd $(cd -P -- "$(dirname -- "$0")" && pwd -P)

image_type=hipchat-bot

echo "Locating the first running container of type ${image_type}..."
container_id=`docker ps -a | grep .*${image_type} | awk '{print $1}'`

docker stop $container_id 2>/dev/null || true
docker start $container_id 2>/dev/null || true

