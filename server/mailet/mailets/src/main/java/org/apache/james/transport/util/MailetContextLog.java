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
package org.apache.james.transport.util;

import org.apache.mailet.MailetContext;
import org.slf4j.Logger;
import org.slf4j.Marker;

/**
 * {@link Logger} implementation which delegate the logging to a
 * {@link MailetContext}
 */
public class MailetContextLog implements Logger {

    private final boolean isDebug;
    private final MailetContext context;

    public MailetContextLog(MailetContext context, boolean isDebug) {
        this.context = context;
        this.isDebug = isDebug;
    }

    public MailetContextLog(MailetContext context) {
        this(context, false);

    }

    /**
     * Only log if {@link #isDebugEnabled()} is true
     */
    public void debug(String arg0) {
        if (isDebug) {
            debugLog(arg0);
        }
    }

    /**
     * Only log if {@link #isDebugEnabled()} is true
     */
    public void debug(String arg0, Throwable arg1) {
        if (isDebug) {
            debugLog(arg0, arg1);
        }
    }

    /**
     * @see org.slf4j.Logger#error(java.lang.String)
     */
    public void error(String arg0) {
        errorLog(arg0);

    }

    /**
     * @see org.slf4j.Logger#error(java.lang.String, java.lang.Throwable)
     */
    public void error(String arg0, Throwable arg1) {
        errorLog(arg0, arg1);

    }

    /**
     * @see org.slf4j.Logger#info(java.lang.String)
     */
    public void info(String arg0) {
        infoLog(arg0);

    }

    /**
     * @see org.slf4j.Logger#info(java.lang.String, java.lang.Throwable)
     */
    public void info(String arg0, Throwable arg1) {
        infoLog(arg0, arg1);

    }

    /**
     * Return true if debug logging is enabled
     */
    public boolean isDebugEnabled() {
        return isDebug;
    }

    /**
     * Enabled, return true
     */
    public boolean isErrorEnabled() {
        return true;
    }

    /**
     * Enabled, return true
     */
    public boolean isFatalEnabled() {
        return true;
    }

    /**
     * Enabled, return true
     */
    public boolean isInfoEnabled() {
        return true;

    }

    /**
     * Not enabled return false
     */
    public boolean isTraceEnabled() {
        return false;
    }

    /**
     * Enabled, return true
     */
    public boolean isWarnEnabled() {
        return true;
    }

    /**
     * @see org.slf4j.Logger#warn(java.lang.String)
     */
    public void warn(String arg0) {
        warnLog(arg0);

    }

    /**
     * @see org.slf4j.Logger#warn(java.lang.String, java.lang.Throwable)
     */
    public void warn(String arg0, Throwable arg1) {
        warnLog(arg0, arg1);

    }

    /**
     * @see org.slf4j.Logger#getName()
     */
    public String getName() {
        return context.toString();
    }

    /**
     * Do nothing
     */
    public void trace(String msg) {
    }

    /**
     * Do nothing
     */
    public void trace(String format, Object arg) {
    }

    /**
     * Do nothing
     */
    public void trace(String format, Object arg1, Object arg2) {
    }

    /**
     * Do nothing
     */
    public void trace(String format, Object[] argArray) {
    }

    /**
     * Do nothing
     */
    public void trace(String msg, Throwable t) {
    }

    /**
     * Do nothing
     */
    public boolean isTraceEnabled(Marker marker) {
        return false;
    }

    /**
     * Do nothing
     */
    public void trace(Marker marker, String msg) {
    }

    /**
     * Do nothing
     */
    public void trace(Marker marker, String format, Object arg) {
    }

    /**
     * Do nothing
     */
    public void trace(Marker marker, String format, Object arg1, Object arg2) {
    }

    /**
     * Do nothing
     */
    public void trace(Marker marker, String format, Object[] argArray) {
    }

    /**
     * Do nothing
     */
    public void trace(Marker marker, String msg, Throwable t) {
    }

    /**
     * @see org.slf4j.Logger#debug(java.lang.String, java.lang.Object)
     */
    public void debug(String format, Object arg) {
        if (isDebug) {
            debugLog(format, arg);
        }
    }

    /**
     * @see org.slf4j.Logger#debug(java.lang.String, java.lang.Object,
     * java.lang.Object)
     */
    public void debug(String format, Object arg1, Object arg2) {
        if (isDebug) {
            debugLog(format, arg1, arg2);
        }
    }

    /**
     * @see org.slf4j.Logger#debug(java.lang.String, java.lang.Object[])
     */
    public void debug(String format, Object[] argArray) {
        if (isDebug) {
            debugLog(format, argArray);
        }
    }

    /**
     * @see org.slf4j.Logger#isDebugEnabled(org.slf4j.Marker)
     */
    public boolean isDebugEnabled(Marker marker) {
        return isDebug;
    }

    /**
     * @see org.slf4j.Logger#debug(org.slf4j.Marker, java.lang.String)
     */
    public void debug(Marker marker, String msg) {
        if (isDebug) {
            debugLog(marker, msg);
        }
    }

    /**
     * @see org.slf4j.Logger#debug(org.slf4j.Marker, java.lang.String,
     * java.lang.Object)
     */
    public void debug(Marker marker, String format, Object arg) {
        if (isDebug) {
            debugLog(marker, format, arg);
        }
    }

    /**
     * @see org.slf4j.Logger#debug(org.slf4j.Marker, java.lang.String,
     * java.lang.Object, java.lang.Object)
     */
    public void debug(Marker marker, String format, Object arg1, Object arg2) {
        if (isDebug) {
            debugLog(marker, format, arg1, arg2);
        }
    }

    /**
     * @see org.slf4j.Logger#debug(org.slf4j.Marker, java.lang.String,
     * java.lang.Object[])
     */
    public void debug(Marker marker, String format, Object[] argArray) {
        if (isDebug) {
            debugLog(marker, format, argArray);
        }
    }

    /**
     * @see org.slf4j.Logger#debug(org.slf4j.Marker, java.lang.String,
     * java.lang.Throwable)
     */
    public void debug(Marker marker, String msg, Throwable t) {
        if (isDebug) {
            debugLog(marker, msg, t);
        }
    }

    /**
     * @see org.slf4j.Logger#info(java.lang.String, java.lang.Object)
     */
    public void info(String format, Object arg) {
        infoLog(format, arg);
    }

    /**
     * @see org.slf4j.Logger#info(java.lang.String, java.lang.Object,
     * java.lang.Object)
     */
    public void info(String format, Object arg1, Object arg2) {
        infoLog(format, arg1, arg2);
    }

    /**
     * @see org.slf4j.Logger#info(java.lang.String, java.lang.Object[])
     */
    public void info(String format, Object[] argArray) {
        infoLog(format, argArray);
    }

    /**
     * @see org.slf4j.Logger#isInfoEnabled(org.slf4j.Marker)
     */
    public boolean isInfoEnabled(Marker marker) {
        return true;
    }

    /**
     * @see org.slf4j.Logger#info(org.slf4j.Marker, java.lang.String)
     */
    public void info(Marker marker, String msg) {
        infoLog(marker, msg);
    }

    /**
     * @see org.slf4j.Logger#info(org.slf4j.Marker, java.lang.String,
     * java.lang.Object)
     */
    public void info(Marker marker, String format, Object arg) {
        infoLog(marker, format, arg);
    }

    /**
     * @see org.slf4j.Logger#info(org.slf4j.Marker, java.lang.String,
     * java.lang.Object, java.lang.Object)
     */
    public void info(Marker marker, String format, Object arg1, Object arg2) {
        infoLog(marker, format, arg1, arg2);
    }

    /**
     * @see org.slf4j.Logger#info(org.slf4j.Marker, java.lang.String,
     * java.lang.Object[])
     */
    public void info(Marker marker, String format, Object[] argArray) {
        infoLog(marker, format, argArray);
    }

    /**
     * @see org.slf4j.Logger#info(org.slf4j.Marker, java.lang.String,
     * java.lang.Throwable)
     */
    public void info(Marker marker, String msg, Throwable t) {
        infoLog(marker, msg, t);
    }

    /**
     * @see org.slf4j.Logger#warn(java.lang.String, java.lang.Object)
     */
    public void warn(String format, Object arg) {
        warnLog(format, arg);
    }

    /**
     * @see org.slf4j.Logger#warn(java.lang.String, java.lang.Object[])
     */
    public void warn(String format, Object[] argArray) {
        warnLog(format, argArray);
    }

    /**
     * @see org.slf4j.Logger#warn(java.lang.String, java.lang.Object,
     * java.lang.Object)
     */
    public void warn(String format, Object arg1, Object arg2) {
        warnLog(format, arg1, arg2);
    }

    /**
     * @see org.slf4j.Logger#isWarnEnabled(org.slf4j.Marker)
     */
    public boolean isWarnEnabled(Marker marker) {
        return true;
    }

    /**
     * @see org.slf4j.Logger#warn(org.slf4j.Marker, java.lang.String)
     */
    public void warn(Marker marker, String msg) {
        warnLog(marker, msg);
    }

    /**
     * @see org.slf4j.Logger#warn(org.slf4j.Marker, java.lang.String,
     * java.lang.Object)
     */
    public void warn(Marker marker, String format, Object arg) {
        warnLog(marker, format, arg);
    }

    /**
     * @see org.slf4j.Logger#warn(org.slf4j.Marker, java.lang.String,
     * java.lang.Object, java.lang.Object)
     */
    public void warn(Marker marker, String format, Object arg1, Object arg2) {
        warnLog(marker, format, arg1, arg2);
    }

    /**
     * @see org.slf4j.Logger#warn(org.slf4j.Marker, java.lang.String,
     * java.lang.Object[])
     */
    public void warn(Marker marker, String format, Object[] argArray) {
        warnLog(marker, format, argArray);
    }

    /**
     * @see org.slf4j.Logger#warn(org.slf4j.Marker, java.lang.String,
     * java.lang.Throwable)
     */
    public void warn(Marker marker, String msg, Throwable t) {
        warnLog(marker, msg, t);
    }

    /**
     * @see org.slf4j.Logger#error(java.lang.String, java.lang.Object)
     */
    public void error(String format, Object arg) {
        errorLog(format, arg);
    }

    /**
     * @see org.slf4j.Logger#error(java.lang.String, java.lang.Object,
     * java.lang.Object)
     */
    public void error(String format, Object arg1, Object arg2) {
        errorLog(format, arg1, arg2);
    }

    /**
     * @see org.slf4j.Logger#error(java.lang.String, java.lang.Object[])
     */
    public void error(String format, Object[] argArray) {
        errorLog(format, argArray);
    }

    /**
     * @see org.slf4j.Logger#isErrorEnabled(org.slf4j.Marker)
     */
    public boolean isErrorEnabled(Marker marker) {
        return true;
    }

    /**
     * @see org.slf4j.Logger#error(org.slf4j.Marker, java.lang.String)
     */
    public void error(Marker marker, String msg) {
        errorLog(marker, msg);
    }

    /**
     * @see org.slf4j.Logger#error(org.slf4j.Marker, java.lang.String,
     * java.lang.Object)
     */
    public void error(Marker marker, String format, Object arg) {
        errorLog(marker, format, arg);
    }

    /**
     * @see org.slf4j.Logger#error(org.slf4j.Marker, java.lang.String,
     * java.lang.Object, java.lang.Object)
     */
    public void error(Marker marker, String format, Object arg1, Object arg2) {
        errorLog(marker, format, arg1, arg2);
    }

    /**
     * @see org.slf4j.Logger#error(org.slf4j.Marker, java.lang.String,
     * java.lang.Object[])
     */
    public void error(Marker marker, String format, Object[] argArray) {
        errorLog(marker, format, argArray);
    }

    /**
     * @see org.slf4j.Logger#error(org.slf4j.Marker, java.lang.String,
     * java.lang.Throwable)
     */
    public void error(Marker marker, String msg, Throwable t) {
        errorLog(marker, msg, t);
    }

    private void debugLog(Marker marker, String msg, Object... obj) {
        StringBuilder s = new StringBuilder("[");
        s.append(marker.toString()).append("] ").append(msg);
        Throwable t = null;
        if (obj != null) {
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
        }
        if (t != null) {
            // TODO Use with apache-mailet-2.5
            // context.log(MailetContext.LogLevel.DEBUG, s.toString(), t);
            context.log(s.toString(), t);
        } else {
            // TODO Use with apache-mailet-2.5
            // context.log(MailetContext.LogLevel.DEBUG, s.toString());
            context.log(s.toString());
        }
    }

    private void debugLog(String msg, Object... obj) {
        StringBuilder s = new StringBuilder(msg);
        Throwable t = null;
        if (obj != null) {
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
        }
        if (t != null) {
            // TODO Use with apache-mailet-2.5
            // context.log(MailetContext.LogLevel.DEBUG, s.toString(), t);
            context.log(s.toString(), t);
        } else {
            // TODO Use with apache-mailet-2.5
            // context.log(MailetContext.LogLevel.DEBUG, s.toString());
            context.log(s.toString());
        }
    }

    private void errorLog(Marker marker, String msg, Object... obj) {
        StringBuilder s = new StringBuilder("[");
        s.append(marker.toString()).append("] ").append(msg);
        Throwable t = null;
        if (obj != null) {
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
        }
        if (t != null) {
            // TODO Use with apache-mailet-2.5
            // context.log(MailetContext.LogLevel.ERROR, s.toString(), t);
            context.log(s.toString(), t);
        } else {
            // TODO Use with apache-mailet-2.5
            // context.log(MailetContext.LogLevel.ERROR, s.toString());
            context.log(s.toString());
        }
    }

    private void errorLog(String msg, Object... obj) {
        StringBuilder s = new StringBuilder(msg);
        Throwable t = null;
        if (obj != null) {
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
        }
        if (t != null) {
            // TODO Use with apache-mailet-2.5
            // context.log(MailetContext.LogLevel.ERROR, s.toString(), t);
            context.log(s.toString(), t);
        } else {
            // TODO Use with apache-mailet-2.5
            // context.log(MailetContext.LogLevel.ERROR, s.toString());
            context.log(s.toString());
        }
    }

    private void infoLog(Marker marker, String msg, Object... obj) {
        StringBuilder s = new StringBuilder("[");
        s.append(marker.toString()).append("] ").append(msg);
        Throwable t = null;
        if (obj != null) {
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
        }
        if (t != null) {
            // TODO Use with apache-mailet-2.5
            // context.log(MailetContext.LogLevel.INFO, s.toString(), t);
            context.log(s.toString(), t);
        } else {
            // TODO Use with apache-mailet-2.5
            // context.log(MailetContext.LogLevel.INFO, s.toString());
            context.log(s.toString());
        }
    }

    private void infoLog(String msg, Object... obj) {
        StringBuilder s = new StringBuilder(msg);
        Throwable t = null;
        if (obj != null) {
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
        }
        if (t != null) {
            // TODO Use with apache-mailet-2.5
            // context.log(MailetContext.LogLevel.INFO, s.toString(), t);
            context.log(s.toString(), t);
        } else {
            // TODO Use with apache-mailet-2.5
            // context.log(MailetContext.LogLevel.INFO, s.toString());
            context.log(s.toString());
        }
    }

    private void warnLog(Marker marker, String msg, Object... obj) {
        StringBuilder s = new StringBuilder("[");
        s.append(marker.toString()).append("] ").append(msg);
        Throwable t = null;
        if (obj != null) {
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
        }
        if (t != null) {
            // TODO Use with apache-mailet-2.5
            // context.log(MailetContext.LogLevel.WARN, s.toString(), t);
            context.log(s.toString(), t);
        } else {
            // TODO Use with apache-mailet-2.5
            // context.log(MailetContext.LogLevel.WARN, s.toString());
            context.log(s.toString());
        }
    }

    private void warnLog(String msg, Object... obj) {
        StringBuilder s = new StringBuilder(msg);
        Throwable t = null;
        if (obj != null) {
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
        }
        if (t != null) {
            // TODO Use with apache-mailet-2.5
            // context.log(MailetContext.LogLevel.WARN, s.toString(), t);
            context.log(s.toString(), t);
        } else {
            // TODO Use with apache-mailet-2.5
            // context.log(MailetContext.LogLevel.WARN, s.toString());
            context.log(s.toString());
        }
    }
}
