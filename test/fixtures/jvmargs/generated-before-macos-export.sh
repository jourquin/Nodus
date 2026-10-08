JVMARGS="-Xmx3072m -Xms512m -Dcustom=value"
JAVA_FEATURE=$(java -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -n 1)
if [ -n "$JAVA_FEATURE" ] && [ "$JAVA_FEATURE" -ge 9 ] && [ "$JAVA_FEATURE" -le 16 ] 2>/dev/null; then
    JVMARGS="$JVMARGS --illegal-access=deny"
fi
if [ -n "$JAVA_FEATURE" ] && [ "$JAVA_FEATURE" -ge 24 ] 2>/dev/null; then
    JVMARGS="$JVMARGS --enable-native-access=ALL-UNNAMED"
fi
if [ -n "$JAVA_FEATURE" ] && [ "$JAVA_FEATURE" -ge 24 ] && [ "$JAVA_FEATURE" -le 25 ] 2>/dev/null; then
    JVMARGS="$JVMARGS --sun-misc-unsafe-memory-access=allow"
fi
if [ -n "$JAVA_FEATURE" ] && [ "$JAVA_FEATURE" -ge 26 ] 2>/dev/null; then
    JVMARGS="$JVMARGS --sun-misc-unsafe-memory-access=warn"
fi