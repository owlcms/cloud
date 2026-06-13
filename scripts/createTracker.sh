#!/bin/bash -x

rm -f fly.toml
tmpfile=$(mktemp)
envsubst < tracker.toml > $tmpfile

# this deploys tracker without requiring a prior creation?
export OPTIONS="--yes --ha=false --vm-size shared-cpu-2x"
flyctl deploy $OPTIONS --config $tmpfile
# Strictly enforce a single machine (the in-memory hub requires one instance).
flyctl scale count 1 --yes --app "$FLY_APP"
rm $tmpfile

