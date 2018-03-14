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

package org.apache.james.mailetcontainer.impl.camel;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.mailet.Mail;

/**
 * Processor which dispose body object if needed
 */
public class DisposeProcessor implements Processor {

    @Override
    public void process(Exchange arg0) throws Exception {
        Mail mail = arg0.getIn().getBody(Mail.class);
        LifecycleUtil.dispose(mail.getMessage());
        LifecycleUtil.dispose(mail);

        // stop routing
        arg0.setProperty(Exchange.ROUTE_STOP, true);

    }

}
