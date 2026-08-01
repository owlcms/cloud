#!/bin/bash -
VERSION=2.0.7

# Check if release already exists
if gh release view $VERSION --repo owlcms/owlcms-cloud &>/dev/null; then
    echo "Error: Release $VERSION already exists. Please update the VERSION number."
    exit 1
fi

# The context is ~75MB, almost all of it the required GeoLite2 database, so the
# default 50MB warning is noise. 100mb still flags anything new that creeps in.
if ! fly deploy . --app owlcms-cloud --config owlcms-cloud.toml --ha=false --image-label $VERSION --build-arg VERSION=$VERSION --build-context-warn-size 100mb; then
    echo "Error: fly deploy failed. GitHub release was not created."
    exit 1
fi

gh release create $VERSION --title "Release $VERSION" --notes-file ReleaseNotes.md