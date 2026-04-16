#!/bin/bash -x
export FLY_APP_root=$FLY_APP
rm -f fly.toml

# create database and get login information
export FLY_APP=${FLY_APP_root}-db
connection_string=$(
  flyctl postgres create \
    --name $FLY_APP \
    --initial-cluster-size 1 \
    --vm-size shared-cpu-1x \
    --volume-size 1 \
    --region=$REGION \
    --org personal | \
  grep 'Connection string:' | \
  sed 's/^[[:blank:]]*Connection string: //'
)

# create application, no deployment
export FLY_APP=$FLY_APP_root
tmpfile=$(mktemp)
envsubst < owlcms.toml > $tmpfile
flyctl launch --ha=false --config $tmpfile --no-deploy

# we do not use flyctl postgres attach because it is unreliable.
# for our purposes the default cluster database is fine.
flyctl secrets set \
  DATABASE_URL=${connection_string}/postgres?sslmode=disable \
  OWLCMS_CONTROLPANEL=3.1.0 \
  container=fly \
  --app ${FLY_APP}

# now that we have the secrets, launch
flyctl deploy --ha=false --config $tmpfile
rm -f $tmpfile