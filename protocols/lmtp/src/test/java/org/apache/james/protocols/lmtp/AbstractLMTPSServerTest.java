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
package org.apache.james.protocols.lmtp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.apache.commons.net.smtp.RelayPath;
import org.apache.commons.net.smtp.SMTPClient;
import org.apache.commons.net.smtp.SMTPReply;
import org.apache.commons.net.smtp.SMTPSClient;
import org.apache.james.protocols.api.utils.BogusSslContextFactory;
import org.apache.james.protocols.api.utils.BogusTrustManagerFactory;

public abstract class AbstractLMTPSServerTest extends AbstractLMTPServerTest{

    @Override
    protected SMTPClient createClient() {
        LMTPSClient client = new LMTPSClient(true, BogusSslContextFactory.getClientContext());
        client.setTrustManager(BogusTrustManagerFactory.getTrustManagers()[0]);
        
        return client;
    }

    protected final class LMTPSClient extends SMTPSClient implements LMTPClient{

        private final List<Integer> replies = new ArrayList<Integer>();
        private int rcptCount = 0;
        

        public LMTPSClient(boolean implicit, SSLContext ctx) {
            super(implicit, ctx);
        }


        @Override
        public boolean addRecipient(String address) throws IOException {
            boolean ok = super.addRecipient(address);
            if (ok) {
                rcptCount++;
            }
            return ok;
        }

        @Override
        public boolean addRecipient(RelayPath path) throws IOException {
            boolean ok = super.addRecipient(path);
            if (ok) {
                rcptCount++;
            }
            return ok;
        }

        /**
         * Issue the LHLO command
         */
        @Override
        public int helo(String hostname) throws IOException {
            return sendCommand("LHLO", hostname);
        }

        public int[] getReplies() throws IOException
        {
            int[] codes = new int[replies.size()];
            for (int i = 0; i < codes.length; i++) {
                codes[i] = replies.remove(0);
            }
            return codes;
        }
        
        @Override
        public boolean completePendingCommand() throws IOException
        {
            for (int i = 0; i < rcptCount; i++) {
                replies.add(getReply());
            }

            return replies.stream()
                .mapToInt(code -> code)
                .anyMatch(SMTPReply::isPositiveCompletion);
        }

        
    }
}
