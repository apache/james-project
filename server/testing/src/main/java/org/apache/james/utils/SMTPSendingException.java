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

package org.apache.james.utils;

import java.util.List;
import java.util.Objects;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;

public class SMTPSendingException extends RuntimeException {

    public static boolean isForStep(Throwable t, SmtpSendingStep step) {
        if (t instanceof SMTPSendingException) {
            SMTPSendingException e = (SMTPSendingException) t;
            return e.sendingStep.equals(step);
        }
        return false;
    }

    private static String sanitizeString(String lastServerMessage) {
        List<String> lines = Splitter.on("\r\n")
            .trimResults()
            .omitEmptyStrings()
            .splitToList(lastServerMessage);

        return Joiner.on("\n")
            .skipNulls()
            .join(lines);
    }

    private final SmtpSendingStep sendingStep;
    private final String lastServerMessage;

    public SMTPSendingException(SmtpSendingStep sendingStep, String lastServerMessage) {
        super(String.format("Error upon step %s: %s", sendingStep, lastServerMessage));
        this.sendingStep = sendingStep;
        this.lastServerMessage = sanitizeString(lastServerMessage);
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof SMTPSendingException) {
            SMTPSendingException that = (SMTPSendingException) o;

            return Objects.equals(this.sendingStep, that.sendingStep)
                && Objects.equals(this.lastServerMessage, that.lastServerMessage);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(sendingStep, lastServerMessage);
    }
}
