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
package org.apache.james.app.spring;

import java.util.Calendar;

import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;
import org.apache.james.container.spring.context.JamesServerApplicationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bootstraps James using a Spring container.
 */
public class JamesAppSpringMain implements Daemon {

    private static final Logger log = LoggerFactory.getLogger(JamesAppSpringMain.class.getName());
    private JamesServerApplicationContext context;

    public static void main(String[] args) throws Exception {

        long start = Calendar.getInstance().getTimeInMillis();

        JamesAppSpringMain main = new JamesAppSpringMain();
        main.init(null);

        long end = Calendar.getInstance().getTimeInMillis();

        log.info("Apache James Server is successfully started in {} milliseconds.", end - start);

    }

    @Override
    public void destroy() {
    }

    @Override
    public void init(DaemonContext arg0) throws Exception {
        context = new JamesServerApplicationContext(new String[] { "META-INF/org/apache/james/spring-server.xml" });
        context.registerShutdownHook();
        context.start();
    }

    @Override
    public void start() throws Exception {
        context.start();
    }

    @Override
    public void stop() throws Exception {
        if (context != null) {
            context.stop();
        }
    }

}
