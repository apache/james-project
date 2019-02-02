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
package org.apache.james.pop3server.jmx;

import java.util.concurrent.atomic.AtomicLong;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;

import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.lib.jmx.AbstractCommandHandlerStats;
import org.apache.james.protocols.pop3.POP3Response;

public class POP3CommandHandlerStats extends AbstractCommandHandlerStats implements POP3CommandHandlerStatsMBean {

    private final AtomicLong error = new AtomicLong(0);
    private final AtomicLong ok = new AtomicLong(0);

    public POP3CommandHandlerStats(String jmxPath, String handlerName, String[] commands) throws NotCompliantMBeanException, MalformedObjectNameException, NullPointerException, InstanceAlreadyExistsException, MBeanRegistrationException {
        super(POP3CommandHandlerStatsMBean.class, jmxPath, handlerName, commands);
    }

    @Override
    protected void incrementStats(Response response) {
        String code = response.getRetCode();
        if (POP3Response.OK_RESPONSE.equals(code)) {
            ok.incrementAndGet();
        } else if (POP3Response.ERR_RESPONSE.equals(code)) {
            error.incrementAndGet();
        }
    }

    @Override
    public long getError() {
        return error.get();
    }

    @Override
    public long getOk() {
        return ok.get();
    }

}
