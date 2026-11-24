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
secrets=(
  "DATABASE_URL=${connection_string}/postgres?sslmode=disable"
  "container=fly"
)
if [ -n "${OWLCMS_CONTROLPANEL_SECRET:-}" ]; then
  secrets+=("OWLCMS_CONTROLPANEL=${OWLCMS_CONTROLPANEL_SECRET}")
fi
flyctl secrets set "${secrets[@]}" --app "${FLY_APP}"

# control panel token (OWLCMS 69+): the value, and a marker whose name lets the cloud app copy it again later.
# Tracing is off so that the token is not printed.
{ set +x; } 2>/dev/null
if [ -n "${CP_TOKEN:-}" ]; then
  printf 'OWLCMS_CONTROLPANELTOKEN=%s\nCPTOKEN_%s=1\n' "$CP_TOKEN" "$CP_TOKEN" \
    | flyctl secrets import --stage --app "${FLY_APP}"
fi
set -x

# now that we have the secrets, launch
flyctl deploy --ha=false --config $tmpfile
# Strictly enforce a single application machine.
flyctl scale count 1 --yes --app "$FLY_APP"
rm -f $tmpfile