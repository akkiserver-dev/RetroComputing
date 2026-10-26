#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
shuttle_root="${SHUTTLE_DIR:-$(dirname -- "$repo_root")/shuttle}"
if [[ "${SKIP_SHUTTLE_BUILD:-0}" != 1 ]]; then
    mvn -f "$shuttle_root/pom.xml" -pl shuttle-java -am install -Dspotbugs.skip=true
fi
cd -- "$repo_root"
bash ./gradlew :common:test :architecture-shuttle:test :emulator:test :1.21.1-common:test --console=plain
