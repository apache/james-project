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

package org.apache.james.transport.mailets.remote.delivery;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Function;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;

import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.eclipse.angus.mail.smtp.SMTPSenderFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public class EnhancedMessagingException {

    private static final Logger logger = LoggerFactory.getLogger(EnhancedMessagingException.class);
    
    private final MessagingException messagingException;
    private final Optional<Integer> returnCode;
    private final Optional<Integer> nestedReturnCode;

    public EnhancedMessagingException(MessagingException messagingException) {
        this.messagingException = messagingException;
        this.returnCode = computeReturnCode();
        this.nestedReturnCode = computeNestedReturnCode();
    }

    public boolean hasReturnCode() {
        return returnCode.isPresent();
    }

    public boolean hasNestedReturnCode() {
        return nestedReturnCode.isPresent();
    }

    public boolean isServerError() {
        return isServerError(returnCode) || isServerError(nestedReturnCode);
    }

    private boolean isServerError(Optional<Integer> returnCode) {
        return (returnCode.isPresent()
            && returnCode.get() >= 500
            && returnCode.get() <= 599)
            || messageIndicatesServerException();
    }

    private boolean messageIndicatesServerException() {
        return Optional.ofNullable(messagingException.getMessage())
            .map(startWith5())
            .orElse(false);
    }

    private Function<String, Boolean> startWith5() {
        return message -> message.startsWith("5");
    }

    private Optional<Integer> computeReturnCode() {
        if (messagingException instanceof SMTPAddressFailedException) {
            SMTPAddressFailedException addressFailedException = (SMTPAddressFailedException) this.messagingException;
            return Optional.of(addressFailedException.getReturnCode());
        }
        if (messagingException instanceof SMTPSendFailedException) {
            SMTPSendFailedException sendFailedException = (SMTPSendFailedException) this.messagingException;
            return Optional.of(sendFailedException.getReturnCode());
        }
        if (messagingException instanceof SMTPSenderFailedException) {
            SMTPSenderFailedException senderFailedException = (SMTPSenderFailedException) this.messagingException;
            return Optional.of(senderFailedException.getReturnCode());
        }
        if (messagingException.getClass().getName().endsWith(".SMTPSendFailedException")
            || messagingException.getClass().getName().endsWith(".SMTPAddressSucceededException")) {
            try {
                return Optional.of((Integer)invokeGetter(messagingException, "getReturnCode"));
            } catch (ClassCastException | IllegalArgumentException | IllegalStateException e) {
                logger.error("unexpected exception", e);
            }
        }
        return Optional.empty();
    }

    public Optional<String> computeCommand() {
        if (hasReturnCode()) {
            try {
                return Optional.of((String) invokeGetter(messagingException, "getCommand"));
            } catch (ClassCastException | IllegalArgumentException | IllegalStateException e) {
                logger.error("unexpected exception", e);
            }
        }
        return Optional.empty();
    }

    public Optional<InternetAddress> computeAddress() {
        if (hasReturnCode()) {
            try {
                return Optional.of((InternetAddress) invokeGetter(messagingException, "getAddress"));
            } catch (ClassCastException | IllegalArgumentException | IllegalStateException e) {
                logger.error("unexpected exception", e);
            }
        }
        return Optional.empty();
    }

    public String computeAction() {
        return messagingException.getClass().getName().endsWith(".SMTPAddressFailedException") ? "FAILED" : "SUCCEEDED";
    }

    public Optional<Integer> getReturnCode() {
        return returnCode;
    }

    private Optional<Integer> computeNestedReturnCode() {
        EnhancedMessagingException currentMessagingException = this;
        while (true) {
            Optional<Integer> returnCode = currentMessagingException.computeReturnCode();
            if (returnCode.isPresent()) {
                return returnCode;
            }
            if (currentMessagingException.hasNestedMessagingException()) {
                currentMessagingException = currentMessagingException.getNestedMessagingException();
            } else {
                return Optional.empty();
            }
        }
    }

    private boolean hasNestedMessagingException() {
        return messagingException.getNextException() != null
            && messagingException.getNextException() instanceof MessagingException;
    }

    private EnhancedMessagingException getNestedMessagingException() {
        Preconditions.checkState(hasNestedMessagingException());
        return new EnhancedMessagingException((MessagingException) messagingException.getNextException());
    }

    private Object invokeGetter(Object target, String getter) {
        try {
            Method getAddress = target.getClass().getMethod(getter);
            return getAddress.invoke(target);
        } catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            return new IllegalStateException("Exception invoking " + getter + " on a " + target.getClass() + " object");            
        }
    }
}
