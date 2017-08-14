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
package org.apache.james.container.spring.lifecycle;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

/**
 * Provide a Log object for components
 *
 * @deprecated Prefer using SLF4J LoggingFactory to get a Logger in each class
 */
@Deprecated
public class LogProviderImpl implements LogProvider, InitializingBean, LogProviderManagementMBean {

    private final ConcurrentHashMap<String, Logger> logMap = new ConcurrentHashMap<>();
    private Map<String, String> logs;
    private final static String PREFIX = "james.";

    /**
     * Use {@link Logger} to create the Log
     * 
     * @param loggerName
     * @return log
     */
    protected Logger createLog(String loggerName) {
        return LoggerFactory.getLogger(loggerName);
    }

    public void setLogMappings(Map<String, String> logs) {
        this.logs = logs;
    }

    /**
     * @see
     * org.springframework.beans.factory.InitializingBean#afterPropertiesSet()
     */
    public void afterPropertiesSet() throws Exception {
        if (logs != null) {
            for (String key : logs.keySet()) {
                String value = logs.get(key);
                registerLog(key, createLog(PREFIX + value));
            }
        }
    }

    /**
     * @see
     * org.apache.james.container.spring.lifecycle.LogProvider#getLog(java.lang.String)
     */
    public Logger getLog(String name) {
        logMap.putIfAbsent(name, createLog(PREFIX + name));
        return logMap.get(name);
    }

    /**
     * @see
     * org.apache.james.container.spring.lifecycle.LogProvider#registerLog(java.lang.String, org.slf4j.Logger)
     */
    public void registerLog(String beanName, Logger log) {
        logMap.put(beanName, log);
    }

    /**
     * @see LogProviderManagementMBean#getSupportedLogLevels()
     */
    public List<String> getSupportedLogLevels() {
        return Arrays.asList("DEBUG", "INFO", "WARN", "ERROR", "OFF");
    }

    /**
     * @see LogProviderManagementMBean#getLogLevels()
     */
    public Map<String, String> getLogLevels() {
        TreeMap<String, String> levels = new TreeMap<>();
        for (String name : logMap.keySet()) {
            String level = getLogLevel(name);
            if (level != null)
                levels.put(name, level);
        }
        return levels;

    }

    /**
     * @see LogProviderManagementMBean#getLogLevel(java.lang.String)
     */
    public String getLogLevel(String component) {
        Logger log = logMap.get(component);
        if (log == null) {
            throw new IllegalArgumentException("No Log for component " + component);
        }
        org.apache.log4j.Logger logger = org.apache.log4j.Logger.getRootLogger();
        if (logger == null || logger.getLevel() == null) {
            return null;
        }
        Level level = logger.getLevel();
        return level.toString();
    }

    /**
     * @see LogProviderManagementMBean#setLogLevel(String, String)
     */
    public void setLogLevel(String component, String loglevel) {
        if (!getSupportedLogLevels().contains(loglevel)) {
            throw new IllegalArgumentException("Not supported loglevel given");
        } else {
            ((org.apache.log4j.Logger) logMap.get(component)).setLevel(Level.toLevel(loglevel));
        }
    }

}
