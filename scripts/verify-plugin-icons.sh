#!/usr/bin/env bash

set -euo pipefail

distribution_dir="build/distributions"

mapfile -t distributions < <(
    find "$distribution_dir" -maxdepth 1 -type f -name '*.zip' ! -name '*-signed.zip' | sort
)

if [[ ${#distributions[@]} -ne 1 ]]; then
    echo "::error::Expected exactly one unsigned plugin distribution in $distribution_dir, found ${#distributions[@]}"
    printf 'Found: %s\n' "${distributions[@]:-<none>}"
    exit 1
fi

distribution="${distributions[0]}"
unpacked="$(mktemp -d)"
trap 'rm -rf "$unpacked"' EXIT

unzip -qq "$distribution" -d "$unpacked"

plugin_jar=""

while IFS= read -r jar_file; do
    entries="$(jar tf "$jar_file")"

    if grep -Fxq 'META-INF/plugin.xml' <<<"$entries"; then
        plugin_jar="$jar_file"
        break
    fi
done < <(find "$unpacked" -type f -path '*/lib/*.jar' | sort)

if [[ -z "$plugin_jar" ]]; then
    echo "::error::Could not find the plugin JAR containing META-INF/plugin.xml in $distribution"
    exit 1
fi

entries="$(jar tf "$plugin_jar")"

for icon in META-INF/pluginIcon.svg META-INF/pluginIcon_dark.svg; do
    if ! grep -Fxq "$icon" <<<"$entries"; then
        echo "::error::$icon is missing from the packaged plugin JAR"
        exit 1
    fi
done

echo "Verified plugin icons in $distribution"
