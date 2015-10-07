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

package org.apache.james.protocols.api.utils;

import org.apache.james.protocols.api.logger.Logger;

public class MockLogger implements Logger {

    public String getName() {
        return "MockLogger to System out";
    }

    public boolean isTraceEnabled() {
        return true;
    }

    public void trace(String msg) {
        SysPrint(msg);
    }

    public void trace(String msg, Throwable t) {
        SysPrint(msg, t);
    }

    public boolean isDebugEnabled() {
        return true;
    }

    public void debug(String msg) {
        SysPrint(msg);
    }

    public void debug(String msg, Throwable t) {
        SysPrint(msg, t);
    }

    public boolean isInfoEnabled() {
        return true;
    }

    public void info(String msg) {
        SysPrint(msg);
    }

    public void info(String msg, Throwable t) {
        SysPrint(msg, t);
    }

    public boolean isWarnEnabled() {
        return true;
    }

    public void warn(String msg) {
        SysPrint(msg);
    }

    public void warn(String msg, Throwable t) {
        SysPrint(msg, t);
    }

    public boolean isErrorEnabled() {
        return true;
    }

    public void error(String msg) {
        SysPrint(msg);
    }

    public void error(String msg, Throwable t) {
        SysPrint(msg, t);
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
            s.append("]");
            System.out.println(s.toString());
            if (t != null) {
                t.printStackTrace();
            }
        } else {
            System.out.println(msg);
        }
    }

    public boolean isFatalEnabled() {
        return true;
    }

    public void fatal(String message) {
        SysPrint(message);
    }

    public void fatal(String message, Throwable t) {
        SysPrint(message, t);
    }
}
