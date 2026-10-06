#!/usr/bin/env bash
# Copyright (c) 2026-present unTill Software Development Group B.V.
# @author Denis Gribanov

set -euo pipefail

jps="${JAVA_HOME}/bin/jps.exe"
jcmd="${JAVA_HOME}/bin/jcmd.exe"

if command -v cygpath >/dev/null 2>&1; then
    jps="$(cygpath -u "$jps")"
    jcmd="$(cygpath -u "$jcmd")"
fi

test_name=""
target_pid=""
stack_pattern="org.junit"
dump_file="$(mktemp)"
trap 'rm -f "$dump_file"' EXIT

if [[ "${1:-}" =~ ^[0-9]+$ ]]; then
    target_pid="$1"
else
    test_name="${1:-}"
    target_pid="${2:-}"
    if [[ -n "$test_name" ]]; then
        stack_pattern="$test_name"
    fi
fi

capture_dump() {
    local pid="$1"
    : > "$dump_file"
    "$jcmd" "$pid" Thread.print -l > "$dump_file" 2>/dev/null
}

if [[ -n "$target_pid" ]]; then
    if ! capture_dump "$target_pid"; then
        echo "Could not read threads from JVM $target_pid." >&2
        exit 1
    fi
    if [[ -n "$test_name" ]] && ! grep -Fq -- "$test_name" "$dump_file"; then
        echo "Test '$test_name' was not found in JVM $target_pid." >&2
        exit 1
    fi
else
    mapfile -t test_pids < <(
        "$jps" -lv |
        awk '/GradleWorkerMain|com\.microsoft\.java\.test\.runner|RemoteTestRunner|JUnitStarter|JUnitCore/ { print $1 }'
    )

    # Gradle test JVMs have a generic GradleWorkerMain command line. Inspect
    # their stacks to distinguish the requested or active test from unrelated
    # workers.
    for pid in "${test_pids[@]}"; do
        if ! capture_dump "$pid"; then
            continue
        fi
        if [[ -n "$test_name" ]] && grep -Fq -- "$test_name" "$dump_file"; then
            target_pid="$pid"
            break
        fi
        if [[ -z "$test_name" ]] && grep -Fq 'org.junit' "$dump_file"; then
            target_pid="$pid"
            break
        fi
    done
fi

if [[ -z "$target_pid" ]]; then
    if [[ -n "$test_name" ]]; then
        echo "Running test '$test_name' was not found in a Gradle or IDE test JVM." >&2
    else
        echo "No active JUnit test was found in a Gradle or IDE test JVM." >&2
    fi
    "$jps" -lv
    exit 1
fi

display_name="${test_name:-Active test}"
echo "$display_name threads in JVM $target_pid:"
awk -v pattern="$stack_pattern" '
    BEGIN { RS=""; ORS="\n\n" }
    index($0, pattern) { print }
' "$dump_file"
