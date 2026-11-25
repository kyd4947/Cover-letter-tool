@echo off
setlocal
cd /d "%~dp0"

rem M4 - Windows portable build (app-image + zip). Installer exe needs WiX Toolset 3.x.

echo [1/4] gradle clean installDist ...
call gradlew.bat clean installDist --console=plain
if errorlevel 1 goto :fail

echo [2/4] jpackage app-image ...
if exist dist rmdir /s /q dist
jpackage ^
  --type app-image ^
  --name CoverLetterTool ^
  --app-version 0.1.0 ^
  --vendor "KYD" ^
  --description "Personalized cover letter generator" ^
  --input build\install\coverletter\lib ^
  --main-jar coverletter.jar ^
  --main-class coverletter.gui.GuiMain ^
  --dest dist ^
  --java-options "-Dfile.encoding=UTF-8"
if errorlevel 1 goto :fail

echo [3/4] license copy ...
copy /y "..\LICENSE" "dist\CoverLetterTool\LICENSE.txt" >nul
if errorlevel 1 goto :fail

echo [4/4] portable zip ...
powershell -NoProfile -Command "Compress-Archive -Path 'dist\CoverLetterTool' -DestinationPath 'dist\CoverLetterTool-portable.zip' -Force"
if errorlevel 1 goto :fail

echo.
echo Done.
echo   exe : dist\CoverLetterTool\CoverLetterTool.exe
echo   zip : dist\CoverLetterTool-portable.zip
exit /b 0

:fail
echo.
echo BUILD FAILED
exit /b 1
