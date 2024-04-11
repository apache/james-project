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

package org.apache.james.transport.mailets;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.Experimental;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

/**
 * Serialise the email and pass it to an HTTP call
 * 
 * Sample configuration:
 * 
 * <mailet match="All" class="SerialiseToHTTP">
 *         <name>URL</name> <value>url where serialised message will be posted</value>
 *         <name>ParameterKey</name> <value>An arbitrary parameter be added to the post</value>
 *         <name>ParameterValue</name> <value>A value for the arbitrary parameter</value>
 *         <name>MessageKeyName</name> <value>Field name for the serialised message</value>
 *         <name>passThrough</name> <value>true or false</value>
 * </mailet>
 * 
 */
@Experimental
public class SerialiseToHTTP extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(SerialiseToHTTP.class);

    /**
     * The name of the header to be added.
     */
    private String url;
    private String parameterKey = null;
    private String parameterValue = null;
    private String messageKeyName = "message";
    private boolean passThrough = true;

    @Override
    public void init() throws MessagingException {

        passThrough = (getInitParameter("passThrough", "true").compareToIgnoreCase("true") == 0);
        String targetUrl = getInitParameter("url");
        parameterKey = getInitParameter("parameterKey");
        parameterValue = getInitParameter("parameterValue");
        String m = getInitParameter("MessageKeyName");
        if (m != null) {
            messageKeyName = m;
        }

        // Check if needed config values are used
        if (targetUrl == null || targetUrl.equals("")) {
            throw new MessagingException(
                    "Please configure a targetUrl (\"url\")");
        } else {
            try {
                // targetUrl = targetUrl + ( targetUrl.contains("?") ? "&" :
                // "?") + parameterKey + "=" + parameterValue;
                url = new URI(targetUrl).toURL().toExternalForm();
            } catch (MalformedURLException | URISyntaxException e) {
                throw new MessagingException(
                        "Unable to contruct URL object from url");
            }
        }

        // record the result
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("I will attempt to deliver serialised messages to "
                    + targetUrl
                    + " as "
                    + messageKeyName
                    + ". "
                    + (parameterKey == null || parameterKey.length() < 2 ? "I will not add any fields to the post. " : "I will prepend: " + parameterKey + "=" + parameterValue + ". ")
                    + (passThrough ? "Messages will pass through."
                            : "Messages will be ghosted."));
        }
    }

    /**
     * Takes the message, serialises it and sends it to the URL
     * 
     * @param mail
     *            the mail being processed
     *
     */
    @Override
    public void service(Mail mail) {
        MimeMessage message;
        try {
            message = mail.getMessage();
        } catch (MessagingException me) {
            LOGGER.error("Messaging exception", me);
            addHeader(mail, false, me.getMessage());
            return;
        }
        String messageAsString;
        try {
            messageAsString = MimeMessageUtil.asString(message);
        } catch (Exception e) {
            LOGGER.error("Message to string exception", e);
            addHeader(mail, false, e.getMessage());
            return;
        }

        String result = httpPost(getNameValuePairs(messageAsString));
        if (passThrough) {
            addHeader(mail, Strings.isNullOrEmpty(result), result);
        } else {
            mail.setState(Mail.GHOST);
        }
    }

    private void addHeader(Mail mail, boolean success, String errorMessage) {
        try {
            MimeMessage message = mail.getMessage();
            message.setHeader("X-toHTTP", (success ? "Succeeded" : "Failed"));
            if (!success && errorMessage != null && errorMessage.length() > 0) {
                message.setHeader("X-toHTTPFailure", errorMessage);
            }
            message.saveChanges();
        } catch (MessagingException me) {
            LOGGER.error("Messaging exception", me);
        }
    }

    private String httpPost(NameValuePair[] data) {

        RequestBuilder requestBuilder = RequestBuilder.post(url);

        for (NameValuePair parameter : data) {
            requestBuilder.addParameter(parameter);
            LOGGER.debug("{}::{}", parameter.getName(), parameter.getValue());
        }


        try (CloseableHttpClient client = HttpClientBuilder.create().build();
             CloseableHttpResponse clientResponse = client.execute(requestBuilder.build())) {

            if (clientResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                LOGGER.debug("POST failed: {}", clientResponse.getStatusLine());
                return clientResponse.getStatusLine().toString();
            }
            return null;
        } catch (ClientProtocolException e) {
            LOGGER.error("Fatal protocol violation: ", e);
            return "Fatal protocol violation: " + e.getMessage();
        } catch (IOException e) {
            LOGGER.debug("Fatal transport error: ", e);
            return "Fatal transport error: " + e.getMessage();
        }
    }

    private NameValuePair[] getNameValuePairs(String message) {

        int l = 1;
        if (parameterKey != null && parameterKey.length() > 0) {
            l = 2;
        }

        NameValuePair[] data = new BasicNameValuePair[l];
        data[0] = new BasicNameValuePair(messageKeyName, message);
        if (l == 2) {
            data[1] = new BasicNameValuePair(parameterKey, parameterValue);
        }

        return data;
    }

    @Override
    public String getMailetInfo() {
        return "HTTP POST serialised message";
    }

}
