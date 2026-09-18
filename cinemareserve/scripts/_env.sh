#!/usr/bin/env bash
# Sourced by every other script in this directory. Not meant to be run directly.
#
# Java's -cp/--class-path argument uses a platform-specific separator between
# entries: ':' on Linux/macOS, ';' on Windows -- and this is the JVM's own rule,
# not the shell's, so it applies the same way whether you're invoking javac/java
# from Git Bash, WSL, or cmd.exe on a Windows machine. Hardcoding ':' is why an
# earlier version of these scripts failed on Windows with "package ... does not
# exist" errors even though every file was present and correct.
case "$(uname -s 2>/dev/null || echo unknown)" in
    CYGWIN*|MINGW*|MSYS*) CP_SEP=";" ;;
    *) CP_SEP=":" ;;
esac
