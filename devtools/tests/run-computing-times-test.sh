#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
audit_test_dir=$(mktemp -d "${TMPDIR:-/tmp}/nodus-computing-times.XXXXXX")
trap 'rm -rf "$audit_test_dir"' EXIT HUP INT TERM
javac --release 11 -d "$audit_test_dir" \
  src/edu/uclouvain/core/nodus/NodusC.java \
  src/edu/uclouvain/core/nodus/compute/assign/AssignmentComputingTimes.java \
  devtools/tests/AssignmentComputingTimesTest.java
java -cp "$audit_test_dir" edu.uclouvain.core.nodus.compute.assign.AssignmentComputingTimesTest
