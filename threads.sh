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

target_pid="${1:-}"

if [[ -z "$target_pid" ]]; then
    target_pid="$(
        "$jps" -lv |
        awk '
            $2 == "org.scm4j.releaser.cli.CLI" { releaser_pid=$1 }
            /com\.microsoft\.java\.test\.runner|RemoteTestRunner|LiveCliDebugTest/ { test_pid=$1 }
            END { print releaser_pid ? releaser_pid : test_pid }
        '
    )"
fi

if [[ -z "$target_pid" ]]; then
    echo "scm4j-releaser CLI or Java test process was not found." >&2
    "$jps" -lv
    exit 1
fi

echo "Status-building threads in JVM $target_pid:"
"$jcmd" "$target_pid" Thread.print -l |
    awk 'BEGIN { RS=""; ORS="\n\n" } /ExtendedStatusBuilder/ { print }'
