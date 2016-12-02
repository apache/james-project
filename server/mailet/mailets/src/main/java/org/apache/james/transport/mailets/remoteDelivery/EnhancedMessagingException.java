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

package org.apache.james.transport.mailets.remoteDelivery;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

public class EnhancedMessagingException {

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
        return Optional.fromNullable(messagingException.getMessage())
            .transform(startWith5())
            .or(false);
    }

    private Function<String, Boolean> startWith5() {
        return new Function<String, Boolean>() {
            @Override
            public Boolean apply(String input) {
                return input.startsWith("5");
            }
        };
    }

    private Optional<Integer> computeReturnCode() {
        if (messagingException.getClass().getName().endsWith(".SMTPSendFailedException")
            || messagingException.getClass().getName().endsWith(".SMTPAddressSucceededException")) {
            try {
                return Optional.of ((Integer) invokeGetter(messagingException, "getReturnCode"));
            } catch (ClassCastException cce) {
            } catch (IllegalArgumentException iae) {
            } catch (IllegalStateException ise) {
            }
        }
        return Optional.absent();
    }

    public Optional<String> computeCommand() {
        if (hasReturnCode()) {
            try {
                return Optional.of((String) invokeGetter(messagingException, "getCommand"));
            } catch (ClassCastException cce) {
            } catch (IllegalArgumentException iae) {
            } catch (IllegalStateException ise) {
            }
        }
        return Optional.absent();
    }

    public Optional<InternetAddress> computeAddress() {
        if (hasReturnCode()) {
            try {
                return Optional.of((InternetAddress) invokeGetter(messagingException, "getAddress"));
            } catch (ClassCastException cce) {
            } catch (IllegalArgumentException iae) {
            } catch (IllegalStateException ise) {
            }
        }
        return Optional.absent();
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
                return Optional.absent();
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
        } catch (NoSuchMethodException nsme) {
            // An SMTPAddressFailedException with no getAddress method.
        } catch (IllegalAccessException iae) {
        } catch (IllegalArgumentException iae) {
        } catch (InvocationTargetException ite) {
            // Other issues with getAddress invokation.
        }
        return new IllegalStateException("Exception invoking " + getter + " on a " + target.getClass() + " object");
    }
}
