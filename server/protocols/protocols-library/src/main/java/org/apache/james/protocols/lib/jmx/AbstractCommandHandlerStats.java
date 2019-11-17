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
package org.apache.james.protocols.lib.jmx;

import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.StandardMBean;

import org.apache.james.lifecycle.api.Disposable;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.CommandHandler;

/**
 * Expose statistics for {@link CommandHandler} via JMX
 */
public abstract class AbstractCommandHandlerStats extends StandardMBean implements CommandHandlerStatsMBean, Disposable {

    private final AtomicLong all = new AtomicLong(0);
    private final AtomicLong disconnect = new AtomicLong();

    private final String name;
    private final String handlerName;
    private final MBeanServer mbeanserver;
    private final String[] commands;

    public AbstractCommandHandlerStats(Class<?> jmxClass, String jmxName, String handlerName, String[] commands) throws NotCompliantMBeanException, MalformedObjectNameException, NullPointerException, InstanceAlreadyExistsException, MBeanRegistrationException {
        super(jmxClass);
        this.handlerName = handlerName;
        this.commands = commands;

        name = "org.apache.james:type=server,name=" + jmxName + ",chain=handlerchain,handler=commandhandler,commandhandler=" + handlerName;
        mbeanserver = ManagementFactory.getPlatformMBeanServer();
        ObjectName baseObjectName = new ObjectName(name);
        mbeanserver.registerMBean(this, baseObjectName);
    }

    /**
     * Increment stats based on the given response
     */
    public void increment(Response response) {
        if (response.isEndSession()) {
            disconnect.incrementAndGet();
        }

        all.incrementAndGet();
        incrementStats(response);
    }

    /**
     * Subclasses need to implement this to handle more precise stats
     */
    protected abstract void incrementStats(Response response);

    @Override
    public long getAll() {
        return all.get();
    }

    @Override
    public String getName() {
        return handlerName;
    }

    @Override
    public void dispose() {
        try {
            mbeanserver.unregisterMBean(new ObjectName(name));
        } catch (Exception e) {
            // ignore here;
        }
    }

    @Override
    public String[] getCommands() {
        return commands;
    }

    @Override
    public long getDisconnect() {
        return disconnect.get();
    }
}
