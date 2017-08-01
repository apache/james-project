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

package org.apache.mailet.base;

import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.MailetConfig;
import org.apache.mailet.MailetContext;
import org.slf4j.Logger;

import com.google.common.base.Optional;

public class MailetPipelineLogging {

    public static void logBeginOfMailetProcess(final Mailet mailet, final Mail mail) {
        getLogger(mailet)
        .transform(logger -> {
            logger.debug("Entering mailet: {}\n\tmail state {}", mailet.getMailetInfo(), mail.getState());
            return true;
        });
    }

    public static void logEndOfMailetProcess(final Mailet mailet, final Mail mail) {
        getLogger(mailet)
            .transform(logger -> {
                logger.debug("End of mailet: {}\n\tmail state {}", mailet.getMailetInfo(), mail.getState());
                return true;
            });
    }

    private static Optional<Logger> getLogger(Mailet mailet) {
        MailetConfig mailetConfig = mailet.getMailetConfig();
        if (mailetConfig == null) {
            return Optional.absent();
        }
        MailetContext mailetContext = mailetConfig.getMailetContext();
        if (mailetContext == null) {
            return Optional.absent();
        }
        return Optional.fromNullable(mailetContext.getLogger());
    }
}
