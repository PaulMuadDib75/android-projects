#!/bin/sh
#
# Copyright © 2015-2021 the original authors.
# Licensed under the Apache License, Version 2.0.
#
# Gradle startup script for UN*X systems.
# On Windows, use gradlew.bat instead.
#
# This script locates Java, then launches the Gradle Wrapper main class
# (GradleWrapperMain) which in turn downloads and runs the correct Gradle
# version specified in gradle/wrapper/gradle-wrapper.properties.

APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")

# Resolve symlinks to find the real location of this script.
app_path=$0
while [ -h "$app_path" ] ; do
    ls=$(ls -ld "$app_path")
    link=$(expr "$ls" : '.*-> \(.*\)$')
    if expr "$link" : '/.*' > /dev/null; then
        app_path="$link"
    else
        app_path=$(dirname "$app_path")/"$link"
    fi
done
APP_HOME=$(cd "$(dirname "$app_path")" && pwd -P) || exit

# Default JVM options — memory limits for the wrapper bootstrap only.
# The actual Gradle daemon gets its memory from gradle.properties (org.gradle.jvmargs).
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
MAX_FD=maximum

warn() { echo "$*" >&2; }
die()  { echo; echo "$*" >&2; echo; exit 1; }

# Detect OS
cygwin=false
darwin=false
msys=false
case "$(uname)" in
    CYGWIN*)  cygwin=true ;;
    Darwin*)  darwin=true ;;
    MSYS*|MINGW*) msys=true ;;
esac

# The wrapper JAR must be on the classpath so GradleWrapperMain can be found.
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Find the java executable.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    [ -x "$JAVACMD" ] || die "ERROR: JAVA_HOME points to an invalid directory: $JAVA_HOME"
else
    JAVACMD=java
    command -v java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and 'java' was not found in PATH."
fi

# Raise the max open file descriptors if possible (Unix only).
if ! "$cygwin" && ! "$darwin" && ! "$msys" ; then
    case $MAX_FD in
        max*) MAX_FD=$(ulimit -H -n 2>/dev/null || echo "")  ;;
    esac
    [ -n "$MAX_FD" ] && ulimit -n "$MAX_FD" 2>/dev/null
fi

# On Cygwin/MSYS, convert paths to Windows format for the java command.
if "$cygwin" || "$msys" ; then
    APP_HOME=$(cygpath --path --mixed "$APP_HOME")
    CLASSPATH=$(cygpath --path --mixed "$CLASSPATH")
    JAVACMD=$(cygpath --unix "$JAVACMD")
fi

# Launch the Gradle Wrapper.
exec "$JAVACMD" \
    -classpath "$CLASSPATH" \
    $DEFAULT_JVM_OPTS \
    ${JAVA_OPTS} \
    ${GRADLE_OPTS} \
    "-Dorg.gradle.appname=$APP_BASE_NAME" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
