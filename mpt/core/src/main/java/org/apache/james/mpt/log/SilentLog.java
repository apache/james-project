/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mpt.log;

import org.slf4j.Logger;
import org.slf4j.Marker;

final class SilentLog implements Logger {
    public void debug(Object arg0) {
    }

    public void debug(Object arg0, Throwable arg1) {
    }

    public void error(Object arg0) {
    }

    public void error(Object arg0, Throwable arg1) {
    }

    public void fatalError(Object arg0) {
    }

    public void fatalError(Object arg0, Throwable arg1) {
    }

    public void info(Object arg0) {
    }

    public void info(Object arg0, Throwable arg1) {
    }

    public boolean isDebugEnabled() {
        return false;
    }

    public boolean isErrorEnabled() {
        return false;
    }

    public boolean isFatalErrorEnabled() {
        return false;
    }

    public boolean isInfoEnabled() {
        return false;
    }

    public boolean isWarnEnabled() {
        return false;
    }

    public void warn(Object arg0) {

    }

    public void warn(Object arg0, Throwable arg1) {

    }

    public void fatal(Object arg0) {
    }

    public void fatal(Object arg0, Throwable arg1) {
    }

    public boolean isFatalEnabled() {
        return false;
    }

    public boolean isTraceEnabled() {
        return false;
    }

    public void trace(Object arg0) {
    }

    public void trace(Object arg0, Throwable arg1) {
    }

    public String getName() {
	return "SilentLog";
    }

    public void trace(String msg) {
    }

    public void trace(String format, Object arg) {
    }

    public void trace(String format, Object arg1, Object arg2) {
    }

    public void trace(String format, Object[] argArray) {
    }

    public void trace(String msg, Throwable t) {
    }

    public boolean isTraceEnabled(Marker marker) {
	return false;
    }

    public void trace(Marker marker, String msg) {
    }

    public void trace(Marker marker, String format, Object arg) {
    }

    public void trace(Marker marker, String format, Object arg1, Object arg2) {
    }

    public void trace(Marker marker, String format, Object[] argArray) {
    }

    public void trace(Marker marker, String msg, Throwable t) {
    }

    public void debug(String msg) {
    }

    public void debug(String format, Object arg) {
    }

    public void debug(String format, Object arg1, Object arg2) {
    }

    public void debug(String format, Object[] argArray) {
    }

    public void debug(String msg, Throwable t) {
    }

    public boolean isDebugEnabled(Marker marker) {
	return false;
    }

    public void debug(Marker marker, String msg) {
    }

    public void debug(Marker marker, String format, Object arg) {
    }

    public void debug(Marker marker, String format, Object arg1, Object arg2) {
    }

    public void debug(Marker marker, String format, Object[] argArray) {
    }

    public void debug(Marker marker, String msg, Throwable t) {
    }

    public void info(String msg) {
    }

    public void info(String format, Object arg) {
    }

    public void info(String format, Object arg1, Object arg2) {
    }

    public void info(String format, Object[] argArray) {
    }

    public void info(String msg, Throwable t) {
    }

    public boolean isInfoEnabled(Marker marker) {
	return false;
    }

    public void info(Marker marker, String msg) {
    }

    public void info(Marker marker, String format, Object arg) {
    }

    public void info(Marker marker, String format, Object arg1, Object arg2) {
    }

    public void info(Marker marker, String format, Object[] argArray) {
    }

    public void info(Marker marker, String msg, Throwable t) {
    }

    public void warn(String msg) {
    }

    public void warn(String format, Object arg) {
    }

    public void warn(String format, Object[] argArray) {
    }

    public void warn(String format, Object arg1, Object arg2) {
    }

    public void warn(String msg, Throwable t) {
    }

    public boolean isWarnEnabled(Marker marker) {
	return false;
    }

    public void warn(Marker marker, String msg) {
    }

    public void warn(Marker marker, String format, Object arg) {
    }

    public void warn(Marker marker, String format, Object arg1, Object arg2) {
    }

    public void warn(Marker marker, String format, Object[] argArray) {
    }

    public void warn(Marker marker, String msg, Throwable t) {
    }

    public void error(String msg) {
    }

    public void error(String format, Object arg) {
    }

    public void error(String format, Object arg1, Object arg2) {
    }

    public void error(String format, Object[] argArray) {
    }

    public void error(String msg, Throwable t) {
    }

    public boolean isErrorEnabled(Marker marker) {
	return false;
    }

    public void error(Marker marker, String msg) {
    }

    public void error(Marker marker, String format, Object arg) {
    }

    public void error(Marker marker, String format, Object arg1, Object arg2) {
    }

    public void error(Marker marker, String format, Object[] argArray) {
    }

    public void error(Marker marker, String msg, Throwable t) {
    }

}