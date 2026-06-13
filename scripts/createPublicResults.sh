#!/bin/bash -x

rm -f fly.toml
tmpfile=$(mktemp)
envsubst < publicresults.toml > $tmpfile

# this deploys publicresults without a requiring a prior creation?
export OPTIONS="--yes --ha=false --vm-size shared-cpu-2x"
flyctl deploy $OPTIONS --config $tmpfile
# Strictly enforce a single machine.
flyctl scale count 1 --yes --app "$FLY_APP"
rm $tmpfile

