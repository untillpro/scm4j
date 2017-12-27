@echo off
if not [%1]==[java.exe] test-empty-path java.exe %*
rem FOR /F "tokens=1 delims=" %%A in ('where java') do SET javapath=%%A
SET PATH=%~dp$PATH:1
echo PATH=%PATH%
./releaser.cmd %2 %3 %4 %5 %6