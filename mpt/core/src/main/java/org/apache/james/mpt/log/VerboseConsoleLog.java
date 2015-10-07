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

public class VerboseConsoleLog implements Logger {

    public String getName() {
        return "MockLogger to System out";
    }

    public boolean isTraceEnabled() {
        return true;
    }

    public void trace(String msg) {
        SysPrint(msg);
    }

    public void trace(String format, Object arg) {
        SysPrint(format, arg);
    }

    public void trace(String format, Object arg1, Object arg2) {
        SysPrint(format, arg1, arg2);
    }

    public void trace(String format, Object[] argArray) {
        SysPrint(format, argArray);
    }

    public void trace(String msg, Throwable t) {
        SysPrint(msg, t);
    }

    public boolean isTraceEnabled(Marker marker) {
        return true;
    }

    public void trace(Marker marker, String msg) {
        SysPrint(marker, msg);
    }

    public void trace(Marker marker, String format, Object arg) {
        SysPrint(marker, format, arg);
    }

    public void trace(Marker marker, String format, Object arg1, Object arg2) {
        SysPrint(marker, format, arg1, arg2);
    }

    public void trace(Marker marker, String format, Object[] argArray) {
        SysPrint(marker, format, argArray);
    }

    public void trace(Marker marker, String msg, Throwable t) {
        SysPrint(marker, msg, t);
    }

    public boolean isDebugEnabled() {
        return true;
    }

    public void debug(String msg) {
        SysPrint(msg);
    }

    public void debug(String format, Object arg) {
        SysPrint(format, arg);
    }

    public void debug(String format, Object arg1, Object arg2) {
        SysPrint(format, arg1, arg2);
    }

    public void debug(String format, Object[] argArray) {
        SysPrint(format, argArray);
    }

    public void debug(String msg, Throwable t) {
        SysPrint(msg, t);
    }

    public boolean isDebugEnabled(Marker marker) {
        return true;
    }

    public void debug(Marker marker, String msg) {
        SysPrint(marker, msg);
    }

    public void debug(Marker marker, String format, Object arg) {
        SysPrint(marker, format, arg);
    }

    public void debug(Marker marker, String format, Object arg1, Object arg2) {
        SysPrint(marker, format, arg1, arg2);
    }

    public void debug(Marker marker, String format, Object[] argArray) {
        SysPrint(marker, format, argArray);
    }

    public void debug(Marker marker, String msg, Throwable t) {
        SysPrint(marker, msg, t);
    }

    public boolean isInfoEnabled() {
        return true;
    }

    public void info(String msg) {
        SysPrint(msg);
    }

    public void info(String format, Object arg) {
        SysPrint(format, arg);
    }

    public void info(String format, Object arg1, Object arg2) {
        SysPrint(format, arg1, arg2);
    }

    public void info(String format, Object[] argArray) {
        SysPrint(format, argArray);
    }

    public void info(String msg, Throwable t) {
        SysPrint(msg, t);
    }

    public boolean isInfoEnabled(Marker marker) {
        return true;
    }

    public void info(Marker marker, String msg) {
        SysPrint(marker, msg);
    }

    public void info(Marker marker, String format, Object arg) {
        SysPrint(marker, format, arg);
    }

    public void info(Marker marker, String format, Object arg1, Object arg2) {
        SysPrint(marker, format, arg1, arg2);
    }

    public void info(Marker marker, String format, Object[] argArray) {
        SysPrint(marker, format, argArray);
    }

    public void info(Marker marker, String msg, Throwable t) {
        SysPrint(marker, msg, t);
    }

    public boolean isWarnEnabled() {
        return true;
    }

    public void warn(String msg) {
        SysPrint(msg);
    }

    public void warn(String format, Object arg) {
        SysPrint(format, arg);
    }

    public void warn(String format, Object[] argArray) {
        SysPrint(format, argArray);
    }

    public void warn(String format, Object arg1, Object arg2) {
        SysPrint(format, arg1, arg2);
    }

    public void warn(String msg, Throwable t) {
        SysPrint(msg, t);
    }

    public boolean isWarnEnabled(Marker marker) {
        return true;
    }

    public void warn(Marker marker, String msg) {
    }

    public void warn(Marker marker, String format, Object arg) {
        SysPrint(marker, format, arg);
    }

    public void warn(Marker marker, String format, Object arg1, Object arg2) {
        SysPrint(marker, format, arg1, arg2);
    }

    public void warn(Marker marker, String format, Object[] argArray) {
        SysPrint(marker, format, argArray);
    }

    public void warn(Marker marker, String msg, Throwable t) {
        SysPrint(marker, msg, t);
    }

    public boolean isErrorEnabled() {
        return true;
    }

    public void error(String msg) {
        SysPrint(msg);
    }

    public void error(String format, Object arg) {
        SysPrint(format, arg);
    }

    public void error(String format, Object arg1, Object arg2) {
        SysPrint(format, arg1, arg2);
    }

    public void error(String format, Object[] argArray) {
        SysPrint(format, argArray);
    }

    public void error(String msg, Throwable t) {
        SysPrint(msg, t);
    }

    public boolean isErrorEnabled(Marker marker) {
        return true;
    }

    public void error(Marker marker, String msg) {
        SysPrint(marker, msg);
    }

    public void error(Marker marker, String format, Object arg) {
        SysPrint(marker, format, arg);
    }

    public void error(Marker marker, String format, Object arg1, Object arg2) {
        SysPrint(marker, format, arg1, arg2);
    }

    public void error(Marker marker, String format, Object[] argArray) {
        SysPrint(marker, format, argArray);
    }

    public void error(Marker marker, String msg, Throwable t) {
        SysPrint(marker, msg, t);
    }

    private void SysPrint(Marker marker, String msg, Object... obj) {
        if (obj != null) {
            Throwable t = null;
            StringBuffer s = new StringBuffer("[");
            s.append(marker.toString()).append("] ").append(msg);
            s.append(" args=[");
            boolean first = true;
            for (Object o : obj) {
                if (o instanceof Throwable) {
                    t = (Throwable) o;
                } else {
                    if (first) {
                        s.append(o.toString());
                        first = false;
                    } else {
                        s.append(", ").append(o.toString());
                    }
                }
            }
            System.out.println(s.toString());
            if (t != null) {
                t.printStackTrace();
            }
        } else {
            System.out.println(msg);
        }
    }

    private void SysPrint(String msg, Object... obj) {
        if (obj != null) {
            Throwable t = null;
            StringBuffer s = new StringBuffer(msg);
            s.append(" args=[");
            boolean first = true;
            for (Object o : obj) {
                if (o instanceof Throwable) {
                    t = (Throwable) o;
                } else {
                    if (first) {
                        s.append(o.toString());
                        first = false;
                    } else {
                        s.append(", ").append(o.toString());
                    }
                }
            }
            System.out.println(s.toString());
            if (t != null) {
                t.printStackTrace();
            }
        } else {
            System.out.println(msg);
        }
    }

}
