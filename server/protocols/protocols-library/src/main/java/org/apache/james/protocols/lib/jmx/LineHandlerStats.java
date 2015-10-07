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

/**
 * Gather stats for LineHandlers
 */
public class LineHandlerStats extends StandardMBean implements HandlerStatsMBean, Disposable {

    private String name;
    private String handlerName;
    private MBeanServer mbeanserver;
    private final AtomicLong all = new AtomicLong(0);
    private final AtomicLong disconnect = new AtomicLong(0);

    public LineHandlerStats(String jmxName, String handlerName) throws NotCompliantMBeanException, MalformedObjectNameException, NullPointerException, InstanceAlreadyExistsException, MBeanRegistrationException {
        super(HandlerStatsMBean.class);
        this.handlerName = handlerName;

        this.name = "org.apache.james:type=server,name=" + jmxName + ",chain=handlerchain,handler=linehandler,linehandler=" + handlerName;
        mbeanserver = ManagementFactory.getPlatformMBeanServer();
        ObjectName baseObjectName = new ObjectName(name);
        mbeanserver.registerMBean(this, baseObjectName);
    }

    /**
     * Increment the stats
     * 
     * @param response
     */
    public void increment(Response response) {
        all.incrementAndGet();
        if (response.isEndSession()) {
            disconnect.incrementAndGet();
        }
    }

    /**
     * @see org.apache.james.protocols.lib.jmx.HandlerStatsMBean#getAll()
     */
    public long getAll() {
        return all.get();
    }

    /**
     * @see org.apache.james.protocols.lib.jmx.HandlerStatsMBean#getName()
     */
    public String getName() {
        return handlerName;
    }

    /**
     * @see org.apache.james.protocols.lib.jmx.HandlerStatsMBean#getDisconnect()
     */
    public long getDisconnect() {
        return disconnect.get();
    }

    /**
     * @see org.apache.james.lifecycle.api.Disposable#dispose()
     */
    public void dispose() {
        try {
            mbeanserver.unregisterMBean(new ObjectName(name));
        } catch (Exception e) {
            // ignore here;
        }
    }

}
