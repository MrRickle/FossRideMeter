#!/usr/bin/env bash
# Adds license-header.txt to the top of every .kt file under the given
# directory (default: current dir), skipping files that already have
# an SPDX-License-Identifier line. Safe to re-run.
#
# Usage: ./add_license_headers.sh [path/to/project] [path/to/license-header.txt]

set -euo pipefail

ROOT="${1:-.}"
HEADER_FILE="${2:-license-header.txt}"

if [ ! -f "$HEADER_FILE" ]; then
    echo "Header file not found: $HEADER_FILE"
    exit 1
fi

count=0

while IFS= read -r -d '' file; do
    if grep -q "SPDX-License-Identifier" "$file"; then
        continue
    fi

    tmp="$(mktemp)"
    cat "$HEADER_FILE" "$file" > "$tmp"
    # collapse to a single blank line between header and original content
    mv "$tmp" "$file"

    echo "Added header: $file"
    count=$((count + 1))
done < <(find "$ROOT" \
    -type d \( -name build -o -name .git -o -name .gradle -o -name .idea \) -prune \
    -o -type f -name "*.kt" -print0)

echo "Done. Headers added to $count file(s)."
