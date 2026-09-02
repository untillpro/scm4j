@echo off
if [%1] == [git.exe] goto normal
if [%1] == [java.exe] goto test_empty_path

call %~dp0releaser git.exe sh.exe %*
exit /b

:test_empty_path
SET PATH=%~dp$PATH:1
echo Test path=%PATH%
./releaser.cmd %2 %3 %4 %5 %6
exit /b

:normal
@echo off
set gitpath=%~dp$PATH:1
if ."%gitpath%" == ."" goto try_git
goto try_sh

:try_git
PATH=%PATH%;c:/Program Files/Git/Cmd
set gitpath=%~dp$PATH:1
if ."%gitpath%" == ."" goto git_not_found

:try_sh
PATH=%PATH%;%gitpath%../usr/bin
set shpath=%~dp$PATH:2
if ."%shpath%" == ."" goto sh_not_found

"%shpath%sh" %~dp0releaser %3 %4 %5 %6 %7 %8 %9

exit /b

:git_not_found
echo %1 not found
exit /b 1

:sh_not_found
echo %2 not found
exit /b 1
