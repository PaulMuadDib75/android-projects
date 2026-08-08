# proguard-rules.pro
# ═══════════════════
# ProGuard/R8 rules for the release build.
# ProGuard strips unused code and obfuscates class/method names to reduce APK size
# and make reverse engineering harder.
#
# For Milestone 1 we have minifyEnabled false in app/build.gradle, so this file
# is not actually used yet. It's here because app/build.gradle references it —
# deleting it would cause a build error.
#
# Rules will be added here in a later milestone when we enable minification
# for the release build.

# Add any custom ProGuard rules here.
