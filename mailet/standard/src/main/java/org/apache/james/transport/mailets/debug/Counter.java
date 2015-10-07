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



package org.apache.james.transport.mailets.debug;

import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.Mail;

/**
 * A simple in memory counter.  Designed to count messages sent to this recipient
 * for debugging purposes.
 *
 */
public class Counter extends GenericMailet {

    /**
     * The number of mails processed by this mailet
     */
    int counter = 0;

    /**
     * Count processed mails, marking each mail as completed after counting.
     *
     * @param mail the mail to process
     */
    public void service(Mail mail) {
        counter++;
        log(counter + "");
        mail.setState(Mail.GHOST);
    }

    /**
     * Return a string describing this mailet.
     *
     * @return a string describing this mailet
     */
    public String getMailetInfo() {
        return "Counter Mailet";
    }
}
