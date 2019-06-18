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
package org.apache.james.queue.activemq;

import org.apache.james.queue.jms.JMSSupport;
import org.apache.mailet.AttributeName;

/**
 * Interface which should get implemented by ActiveMQ depending implementions
 */
public interface ActiveMQSupport extends JMSSupport {

    /**
     * The name of the Queue the mail is stored in
     */
    AttributeName JAMES_QUEUE_NAME = AttributeName.of("JAMES_QUEUE_NAME");

    /**
     * The URL of the Blobmessage content
     */
    AttributeName JAMES_BLOB_URL = AttributeName.of("JAMES_BLOB_URL");

    /**
     * Indicate that the Blobmessage content is reused for a new message
     */
    AttributeName JAMES_REUSE_BLOB_URL = AttributeName.of(" JAMES_REUSE_BLOB_URL");

}
