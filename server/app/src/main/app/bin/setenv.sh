#!/bin/sh
# ----------------------------------------------------------------------------
# Copyright 2001-2018 The Apache Software Foundation.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ----------------------------------------------------------------------------
#
# This file is sourced by the various start scripts. You can use it to
# add extra environment variables to the startup procedure.
#
#   NOTE:  Instead of changing this file it is better to create a file
#          named setenv.sh in the ../conf directory as the files in the
#          bin directory should generally not be changed.

# Add every needed extra jar to this
CLASSPATH_PREFIX=../conf/lib/*
export CLASSPATH_PREFIX

[ -f "$BASEDIR"/conf/setenv.sh ] && . "$BASEDIR"/conf/setenv.sh
