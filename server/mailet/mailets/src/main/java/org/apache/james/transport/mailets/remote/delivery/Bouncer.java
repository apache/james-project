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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Optional;

import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;
import org.apache.mailet.ProcessingState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

public class Bouncer {
    private static final Logger LOGGER = LoggerFactory.getLogger(Bouncer.class);

    public static final AttributeName DELIVERY_ERROR = AttributeName.of("delivery-error");
    public static final AttributeName DELIVERY_ERROR_CODE = AttributeName.of("delivery-error-code");
    public static final AttributeName IS_DELIVERY_PERMANENT_ERROR = AttributeName.of("is-delivery-permanent-error");

    private final RemoteDeliveryConfiguration configuration;
    private final MailetContext mailetContext;

    public Bouncer(RemoteDeliveryConfiguration configuration, MailetContext mailetContext) {
        this.configuration = configuration;
        this.mailetContext = mailetContext;
    }

    public void bounce(Mail mail, Exception ex) throws MessagingException {
        configuration.getBounceProcessor().ifPresentOrElse(
            Throwing.<ProcessingState>consumer(bounceProcessor -> {
                computeErrorCode(ex).ifPresent(mail::setAttribute);
                mail.setAttribute(new Attribute(DELIVERY_ERROR, AttributeValue.of(getErrorMsg(ex))));
                mailetContext.sendMail(mail, bounceProcessor.getValue());
            }).sneakyThrow(),
            Throwing.runnable(() -> bounceWithMailetContext(mail, ex)).sneakyThrow());
    }

    private Optional<Attribute> computeErrorCode(Exception ex) {
        return Optional.ofNullable(ex)
            .filter(MessagingException.class::isInstance)
            .map(MessagingException.class::cast)
            .map(EnhancedMessagingException::new)
            .flatMap(EnhancedMessagingException::getReturnCode)
            .map(code -> new Attribute(DELIVERY_ERROR_CODE, AttributeValue.of(code)));
    }

    private void bounceWithMailetContext(Mail mail, Exception ex) throws MessagingException {
        if (!mail.hasSender()) {
            LOGGER.debug("Null Sender: no bounce will be generated for {}", mail.getName());
        } else {
            LOGGER.debug("Sending failure message {}", mail.getName());
            mailetContext.bounce(mail, explanationText(mail, ex));
        }
    }

    public String explanationText(Mail mail, Exception ex) {
        StringWriter sout = new StringWriter();
        PrintWriter out = new PrintWriter(sout, true);
        out.println("Hi. This is the James mail server at " + resolveMachineName() + ".");
        out.println("I'm afraid I wasn't able to deliver your message to the following addresses.");
        out.println("This is a permanent error; I've given up. Sorry it didn't work out. Below");
        out.println("I include the list of recipients and the reason why I was unable to deliver");
        out.println("your message.");
        out.println();
        for (MailAddress mailAddress : mail.getRecipients()) {
            out.println(mailAddress);
        }
        if (ex instanceof MessagingException) {
            if (((MessagingException) ex).getNextException() == null) {
                out.println(sanitizeExceptionMessage(ex));
            } else {
                Exception ex1 = ((MessagingException) ex).getNextException();
                if (ex1 instanceof SendFailedException) {
                    out.println("Remote mail server told me: " + sanitizeExceptionMessage(ex1));
                } else if (ex1 instanceof UnknownHostException) {
                    out.println("Unknown host: " + sanitizeExceptionMessage(ex1));
                    out.println("This could be a DNS server error, a typo, or a problem with the recipient's mail server.");
                } else if (ex1 instanceof ConnectException) {
                    // Already formatted as "Connection timed out: connect"
                    out.println(sanitizeExceptionMessage(ex1));
                } else if (ex1 instanceof SocketException) {
                    out.println("Socket exception: " + sanitizeExceptionMessage(ex1));
                } else {
                    out.println(sanitizeExceptionMessage(ex1));
                }
            }
        }
        out.println();
        return sout.toString();
    }

    private String sanitizeExceptionMessage(Exception e) {
        if (e == null || e.getMessage() == null) {
            return "null";
        } else {
            return e.getMessage().trim();
        }
    }

    private String resolveMachineName() {
        try {
            return configuration.getHeloNameProvider().getHeloName();
        } catch (Exception e) {
            return "[address unknown]";
        }
    }

    public String getErrorMsg(Exception ex) {
        if (ex instanceof MessagingException) {
            return getNestedExceptionMessage((MessagingException) ex);
        } else {
            return sanitizeExceptionMessage(ex);
        }
    }

    public String getNestedExceptionMessage(MessagingException me) {
        if (me.getNextException() == null) {
            return sanitizeExceptionMessage(me);
        } else {
            Exception ex1 = me.getNextException();
            return sanitizeExceptionMessage(ex1);
        }
    }
}
