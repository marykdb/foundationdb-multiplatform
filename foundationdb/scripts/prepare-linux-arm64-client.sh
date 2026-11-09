#!/usr/bin/env bash
# Cross-linking helper: extract an ARM64 client without trying to execute it on the compiler host.
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
version="${FDB_VERSION:-7.3.81}"
output="$root/build/linux-arm64-client"
mkdir -p "$output"
package="foundationdb-clients_${version}-1_aarch64.deb"
url="https://github.com/apple/foundationdb/releases/download/$version/$package"
curl -fsSL --retry 5 --retry-all-errors "$url" -o "$output/$package"
curl -fsSL --retry 5 --retry-all-errors "$url.sha256" -o "$output/$package.sha256"
python3 - "$output/$package" <<'CHECKSUM'
import hashlib, pathlib, re, sys
archive = pathlib.Path(sys.argv[1])
expected = re.search(r"[a-fA-F0-9]{64}", pathlib.Path(str(archive) + ".sha256").read_text()).group().lower()
assert hashlib.sha256(archive.read_bytes()).hexdigest() == expected, "ARM64 client checksum mismatch"
CHECKSUM
mkdir -p "$output/extracted"
data_archive="$(ar -t "$output/$package" | tr -d '\r' | sed -n '/^data\.tar/p')"
test -n "$data_archive"
# GNU tar cannot auto-detect compression from stdin; a file works on GNU and BSD tar.
ar -p "$output/$package" "$data_archive" > "$output/$data_archive"
tar -xf "$output/$data_archive" -C "$output/extracted"
mkdir -p "$output/lib"
find "$output/extracted" -name libfdb_c.so -exec cp -L {} "$output/lib/libfdb_c.so" \;
test -f "$output/lib/libfdb_c.so"
file "$output/lib/libfdb_c.so" | grep -q 'ARM aarch64'
