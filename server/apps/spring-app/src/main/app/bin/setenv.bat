@REM ----------------------------------------------------------------------------
@REM Copyright 2001-2018 The Apache Software Foundation.
@REM
@REM Licensed under the Apache License, Version 2.0 (the "License");
@REM you may not use this file except in compliance with the License.
@REM You may obtain a copy of the License at
@REM
@REM      http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing, software
@REM distributed under the License is distributed on an "AS IS" BASIS,
@REM WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@REM See the License for the specific language governing permissions and
@REM limitations under the License.
@REM ----------------------------------------------------------------------------
@REM

@REM   This file is sourced (using 'CALL') by the various start scripts. 
@REM   You can use it to add extra environment variables to the startup 
@REM   procedure.
@REM   
@REM   NOTE:  Instead of changing this file it is better to create a new file
@REM          named setenv.bat in the ../conf directory as the files in the
@REM          bin directory should generally not be changed.


@REM Add every needed extra jar to this 
set CLASSPATH_PREFIX=..\conf\lib\*


if exist "%BASEDIR%\conf\setenv.bat" call "%BASEDIR%\conf\setenv.bat"
