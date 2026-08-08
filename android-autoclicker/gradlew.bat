@rem ============================================================
@rem Gradle startup script for Windows.
@rem On Unix/Mac, use ./gradlew instead.
@rem
@rem This script locates Java, then launches the Gradle Wrapper
@rem (GradleWrapperMain) which downloads and runs the correct
@rem Gradle version from gradle/wrapper/gradle-wrapper.properties.
@rem ============================================================
@if "%DEBUG%"=="" @echo off
@setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Collapse any . or .. in APP_HOME
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Default JVM memory for the wrapper bootstrap only.
@rem The Gradle daemon gets its memory from gradle.properties.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo Please install a JDK and set the JAVA_HOME environment variable.
echo Android Studio bundles a JDK — set JAVA_HOME to its location, e.g.:
echo   C:\Program Files\Android\Android Studio\jbr
goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
goto fail

:execute
@rem The wrapper JAR must be on the classpath.
set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

@rem Launch the Gradle Wrapper main class.
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% ^
    "-Dorg.gradle.appname=%APP_BASE_NAME%" ^
    -classpath "%CLASSPATH%" ^
    org.gradle.wrapper.GradleWrapperMain %*

:end
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%GRADLE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
endlocal
