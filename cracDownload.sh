#!/usr/bin/env bash

CRAC_JDK_VERSION=17
CRAC_ARCH=$(uname -m)

release_id=$(curl -s "https://api.azul.com/metadata/v1/zulu/packages/?java_version=${CRAC_JDK_VERSION}&arch=${CRAC_ARCH}&crac_supported=true&latest=true&release_status=ga&certifications=tck&page=1&page_size=100" -H "accept: application/json" | jq -r '.[0] | .package_uuid')
if [ "$release_id" = "null" ]; then
  echo "No CRaC OpenJDK $CRAC_JDK_VERSION for $CRAC_ARCH found"
  exit 1
fi

details=$(curl -s "https://api.azul.com/metadata/v1/zulu/packages/$release_id" -H "accept: application/json")
name=$(echo "$details" | jq -r '.name')
url=$(echo "$details" | jq -r '.download_url')
hash=$(echo "$details" | jq -r '.sha256_hash')

echo "Downloading $name from $url"

# Download the JDK
curl -LJOH 'Accept: application/octet-stream' "$url" >&2

# Verify the SHA256 hash
if command -v -- "sha256sum" > /dev/null 2>&1; then
  file_sha=$(sha256sum -b "$name" | cut -d' ' -f 1)
elif command -v -- "shasum" > /dev/null 2>&1; then
  file_sha=$(shasum -a 256 -b "$name" | cut -d' ' -f 1)
else
  echo "No SHA256 hash command found"
  exit 1
fi

if [ "$file_sha" != "$hash" ]; then
  echo "SHA256 hash mismatch: $file_sha != $hash"
  exit 1
fi
echo "SHA256 hash matches: $file_sha == $hash" >&2

# Extract the JDK and remove the tarball
tar xzf "$name"
rm "$name"

# Set the location of the Azul JDK for the rest of the workflow
if [[ -z "${GITHUB_ENV}" ]]; then
  echo "GITHUB_ENV not set"
else
  echo "Setting JDK in GITHUB_ENV"
  echo JDK="$(pwd)/${name%%.tar.gz}" >> $GITHUB_ENV
fi
