#!/bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
SAMPLES=${1:-25}
python3 "$DIR/benchmark.py" "$SAMPLES"
