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

package org.apache.james.protocols.smtp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * The MailEnvelope of a SMTP-Transaction
 * 
 * 
 */
public interface MailEnvelope {

    /**
     * Return the size of the message. If the message is "empty" it will return
     * -1
     * 
     * @return size
     */
    long getSize();

    /**
     * Return the recipients which where supplied in the RCPT TO: command
     * 
     * @return recipients
     */
    List<MailAddress> getRecipients();

    /**
     * Return the sender of the mail which was supplied int the MAIL FROM:
     * command. If its a "null" sender, null will get returned
     * 
     * @return sender
     */
    MailAddress getSender();

    /**
     * Return the OutputStream of the message
     * 
     * TODO: Think about how to remove this!
     * 
     * @return out
     * @throws IOException
     */
    OutputStream getMessageOutputStream() throws IOException;

    /**
     * Return the InputStream of the message
     * 
     * @return in
     * @throws IOException
     */
    InputStream getMessageInputStream() throws IOException;
}
