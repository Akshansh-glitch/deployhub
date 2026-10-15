#!/usr/bin/env bash
# Runs the app's own tests before the image is built. Usage: run-tests.sh <runtime> <dir>
set -euo pipefail
runtime="$1"
cd "$2"

case "$runtime" in
  springboot)
    if [ -x ./mvnw ]; then ./mvnw -B -q test
    elif [ -f pom.xml ]; then mvn -B -q test
    else echo "::error::No pom.xml found. DeployHub v1 supports Maven projects only."; exit 1
    fi
    ;;
  node)
    if [ -f package-lock.json ]; then npm ci; else npm install; fi
    npm test --if-present
    ;;
  flask)
    python -m pip install -q -r requirements.txt pytest
    # pytest exits with 5 when there are no tests; treat that as a pass
    python -m pytest -q || [ $? -eq 5 ]
    ;;
  *)
    echo "::error::Unknown runtime '$runtime'"; exit 1
    ;;
esac
