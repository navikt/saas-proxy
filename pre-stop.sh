#!/bin/bash

# Let most connections get som time to finish
echo "Sleeping 30 seconds before shutting down application" > /proc/1/fd/1
sleep 30