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

package org.apache.james.webadmin.data.jmap.dto;


import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.apache.james.jmap.api.identity.IdentityCreationRequest;
import org.apache.james.jmap.api.identity.IdentityUpdateRequest;
import org.apache.james.jmap.api.model.Identity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;

import scala.jdk.javaapi.CollectionConverters;
import scala.jdk.javaapi.OptionConverters;

public class UserIdentity {
    public static UserIdentity from(Identity identity) {
        return new UserIdentity(
            identity.name(),
            identity.email().asString(),
            identity.id().id().toString(),
            identity.mayDelete(),
            identity.textSignature(),
            identity.htmlSignature(),
            identity.sortOrder(),
            getBccFromIdentity(identity),
            getReplyFromIdentity(identity));
    }

    public static class EmailAddress {
        public static EmailAddress from(org.apache.james.jmap.api.model.EmailAddress scala) {
            return new EmailAddress(scala.nameAsString(), scala.email());
        }

        @JsonProperty("name")

        private String emailerName;

        @JsonProperty("email")
        private String mailAddress;

        public EmailAddress(String emailerName, MailAddress mailAddress) {
            this.emailerName = emailerName;
            this.mailAddress = mailAddress.asString();
        }

        @JsonCreator
        public EmailAddress(@JsonProperty("name") String emailerName,
                            @JsonProperty("email") String mailAddress) {
            this.emailerName = emailerName;
            this.mailAddress = mailAddress;
        }

        public String getEmailerName() {
            return emailerName;
        }

        public String getMailAddress() {
            return mailAddress;
        }

        public org.apache.james.jmap.api.model.EmailAddress asScalaEmailAddress() throws AddressException {
            return org.apache.james.jmap.api.model.EmailAddress.from(Optional.ofNullable(getEmailerName()), new MailAddress(getMailAddress()));
        }
    }

    public static class UserIdentityUpsert {
        private final String name;
        private final String email;
        private final String textSignature;
        private final String htmlSignature;
        private final Integer sortOrder;
        private final List<EmailAddress> bcc;
        private final List<EmailAddress> replyTo;

        @JsonCreator
        public UserIdentityUpsert(@JsonProperty("name") String name,
                                  @JsonProperty("email") String email,
                                  @JsonProperty("textSignature") String textSignature,
                                  @JsonProperty("htmlSignature") String htmlSignature,
                                  @JsonProperty("sortOrder") Integer sortOrder,
                                  @JsonProperty("bcc") List<EmailAddress> bcc,
                                  @JsonProperty("replyTo") List<EmailAddress> replyTo) {
            this.name = name;
            this.email = email;
            this.textSignature = textSignature;
            this.htmlSignature = htmlSignature;
            this.sortOrder = sortOrder;
            this.bcc = bcc;
            this.replyTo = replyTo;
        }

        public IdentityCreationRequest asCreationRequest() throws AddressException {
            Preconditions.checkArgument(email != null, "email must be not null");
            return IdentityCreationRequest.fromJava(
                new MailAddress(email),
                Optional.ofNullable(name),
                Optional.ofNullable(replyTo)
                    .map(rt -> rt.stream()
                        .map(Throwing.function(EmailAddress::asScalaEmailAddress))
                        .collect(Collectors.toList())),
                Optional.ofNullable(bcc)
                    .map(rt -> rt.stream()
                        .map(Throwing.function(EmailAddress::asScalaEmailAddress))
                        .collect(Collectors.toList())),
                Optional.ofNullable(sortOrder),
                Optional.ofNullable(textSignature),
                Optional.ofNullable(htmlSignature));
        }

        public IdentityUpdateRequest asUpdateRequest() {
            return IdentityUpdateRequest.fromJava(
                Optional.ofNullable(name),
                Optional.ofNullable(replyTo)
                    .map(rt -> rt.stream()
                        .map(Throwing.function(EmailAddress::asScalaEmailAddress))
                        .collect(Collectors.toList())),
                Optional.ofNullable(bcc)
                    .map(rt -> rt.stream()
                        .map(Throwing.function(EmailAddress::asScalaEmailAddress))
                        .collect(Collectors.toList())),
                Optional.ofNullable(sortOrder),
                Optional.ofNullable(textSignature),
                Optional.ofNullable(htmlSignature));
        }
    }

    private static List<EmailAddress> getBccFromIdentity(Identity identity) {
        return OptionConverters.toJava(identity.bcc())
            .map(CollectionConverters::asJava)
            .orElseGet(List::of)
            .stream()
            .map(EmailAddress::from)
            .collect(Collectors.toList());
    }

    private static List<EmailAddress> getReplyFromIdentity(Identity identity) {
        return OptionConverters.toJava(identity.replyTo())
            .map(CollectionConverters::asJava)
            .orElseGet(List::of)
            .stream()
            .map(EmailAddress::from)
            .collect(Collectors.toList());
    }

    private String name;
    private String email;
    private String id;
    private Boolean mayDelete;
    private String textSignature;
    private String htmlSignature;
    private Integer sortOrder;
    private List<EmailAddress> bcc;
    private List<EmailAddress> replyTo;

    public UserIdentity(String name, String email, String id,
                        Boolean mayDelete, String textSignature, String htmlSignature,
                        Integer sortOrder, List<EmailAddress> bcc, List<EmailAddress> replyTo) {
        this.name = name;
        this.email = email;
        this.id = id;
        this.mayDelete = mayDelete;
        this.textSignature = textSignature;
        this.htmlSignature = htmlSignature;
        this.sortOrder = sortOrder;
        this.bcc = bcc;
        this.replyTo = replyTo;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getId() {
        return id;
    }

    public Boolean getMayDelete() {
        return mayDelete;
    }

    public String getTextSignature() {
        return textSignature;
    }

    public String getHtmlSignature() {
        return htmlSignature;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public List<EmailAddress> getBcc() {
        return bcc;
    }

    public List<EmailAddress> getReplyTo() {
        return replyTo;
    }
}
