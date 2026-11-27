#!/bin/sh
set -eu

cd "$(dirname "$0")"
build_dir=$(mktemp -d "${TMPDIR:-/tmp}/nodus-sample-plugin.XXXXXX")
trap 'rm -rf "$build_dir"' 0
trap 'exit 1' HUP INT TERM

mkdir "$build_dir/classes"
javac --release 11 -encoding UTF-8 -cp '../../nodus9.jar:../../lib/*' \
  -d "$build_dir/classes" NodusSamplePlugin.java
jar cf "$build_dir/NodusSamplePlugin.jar" -C "$build_dir/classes" .
mv -f "$build_dir/NodusSamplePlugin.jar" ../NodusSamplePlugin.jar
