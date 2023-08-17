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
package org.apache.james.dnsservice.dnsjava;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.Name;
import org.xbill.DNS.SetResponse;
import org.xbill.DNS.Zone;

public class ZoneCacheLookupRecordsAnswer implements Answer<SetResponse> {

    private static final Logger LOG = LoggerFactory.getLogger(ZoneCacheLookupRecordsAnswer.class);

    private final Zone zone;

    public ZoneCacheLookupRecordsAnswer(Zone zone) {
        this.zone = zone;
    }

    @Override
    public SetResponse answer(InvocationOnMock invocation) throws Throwable {
        Object[] arguments = invocation.getArguments();
        LOG.info("Cache.lookupRecords {}, {}, {}", arguments[0], arguments[1], arguments[2]);
        assert arguments[0] instanceof Name;
        assert arguments[1] instanceof Integer;
        return zone.findRecords((Name) arguments[0], (Integer) arguments[1]);
    }
}
