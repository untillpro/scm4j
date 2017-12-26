@echo off
set gitpath=%~dp$PATH:1
if ."%gitpath%" == ."" goto not_installed

PATH=%PATH%;%gitpath%../usr/bin
set shpath=%~dp$PATH:2
if ."%shpath%" == ."" goto sh_not_installed

sh %~dp0releaser %3 %4 %5 %6 %7 %8 %9

exit

:not_installed
echo %1 not found
exit 1

:sh_not_installed
echo %2 not found
exit 1

rem groovy %~dp0releaser.gsh %*
