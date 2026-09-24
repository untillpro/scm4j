@echo off
setlocal EnableExtensions DisableDelayedExpansion

set "GIT_EXE="
for /f "delims=" %%I in ('where.exe git.exe 2^>nul') do if not defined GIT_EXE set "GIT_EXE=%%I"

if not defined GIT_EXE call :use_git "%ProgramFiles%\Git\cmd\git.exe"
if not defined GIT_EXE call :use_git "%ProgramW6432%\Git\cmd\git.exe"
if not defined GIT_EXE call :use_git "%ProgramFiles(x86)%\Git\cmd\git.exe"

if not defined GIT_EXE (
	>&2 echo Error: git.exe was not found. Install Git for Windows or add git.exe to PATH.
	exit /b 1
)

rem GIT_DIR is reserved by Git and JGit as an override for the repository path.
for %%I in ("%GIT_EXE%") do set "GIT_EXE_DIR=%%~dpI"
set "PATH=%PATH%;%GIT_EXE_DIR%..\usr\bin"

where.exe /q sh.exe
if errorlevel 1 (
	>&2 echo Error: sh.exe was not found. Install Git for Windows or add sh.exe to PATH.
	exit /b 1
)

sh.exe "%~dp0releaser" %*
set "RELEASER_EXIT_CODE=%ERRORLEVEL%"
endlocal & exit /b %RELEASER_EXIT_CODE%

:use_git
if defined GIT_EXE exit /b 0
if exist "%~1" set "GIT_EXE=%~f1"
exit /b 0
