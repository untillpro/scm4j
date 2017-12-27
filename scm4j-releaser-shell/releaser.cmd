@echo off
if [%1]==[git.exe] test-empty-path java.exe %*
call %~dp0releaser-git git.exe sh.exe %*
