#!/bin/bash -e

cd $(cd -P -- "$(dirname -- "$0")" && pwd -P)

image_type=hipchat-bot

#Kill all running containers of a certain type
echo "Removing all currently running containers of type ${image_type}..."
docker rm -f `docker ps -a | grep .*${image_type} | awk '{print $1}'` 2>/dev/null || true

