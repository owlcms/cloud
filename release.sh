#!/bin/bash -
VERSION=1.8.1

# Check if release already exists
if gh release view $VERSION --repo owlcms/owlcms-cloud &>/dev/null; then
    echo "Error: Release $VERSION already exists. Please update the VERSION number."
    exit 1
fi

if ! fly deploy . --local-only --app owlcms-cloud --config owlcms-cloud.toml --ha=false --image-label $VERSION --build-arg VERSION=$VERSION; then
    echo "Error: fly deploy failed. GitHub release was not created."
    exit 1
fi

gh release create $VERSION --title "Release $VERSION" --notes-file ReleaseNotes.md