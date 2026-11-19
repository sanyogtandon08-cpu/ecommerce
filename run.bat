@echo off
setlocal
cd /d "%~dp0"
if not defined JAVA_HOME for /d %%J in ("%ProgramFiles%\Java\jdk-*") do set "JAVA_HOME=%%~J"
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
if not exist build mkdir build
javac --add-modules jdk.httpserver -d build src\main\java\shop\ShopServer.java
if errorlevel 1 exit /b 1
if not exist build\web mkdir build\web
xcopy /E /I /Y src\main\resources\web build\web >nul
java --add-modules jdk.httpserver -cp build shop.ShopServer
