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
package org.apache.james.rrt.api;

import java.util.EnumSet;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.rrt.api.RecipientRewriteTable.ErrorMappingException;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.Mappings;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tells whether a recipient can be delivered to: either it has a local mailbox, or it is
 * covered by a {@link RecipientRewriteTable} entry.
 *
 * Shared by the protocols willing to reject unknown recipients rather than generating a bounce
 * (SMTP RCPT TO validation, JMAP EmailSubmission/set validation).
 */
public class RecipientValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecipientValidator.class);

    private static final String ENABLE_RECIPIENT_REWRITE_TABLE_PROPERTY = "enableRecipientRewriteTable";
    private static final String RECIPIENT_REWRITE_TABLE_CHECK_PROPERTY = "recipientRewriteTableCheck";

    public enum RecipientRewriteTableCheck {
        MAPPING_EXISTS,
        ANY_TARGET_HAS_LOCAL_MAILBOX,
        ALL_TARGETS_HAVE_LOCAL_MAILBOX;

        public static RecipientRewriteTableCheck parse(String value) throws ConfigurationException {
            return switch (value) {
                case "mappingExists" -> MAPPING_EXISTS;
                case "anyMappingValid" -> ANY_TARGET_HAS_LOCAL_MAILBOX;
                case "allMappingsValid" -> ALL_TARGETS_HAVE_LOCAL_MAILBOX;
                default -> throw new ConfigurationException("RecipientValidator.RecipientRewriteTableCheck: unsupported value '" + value + "'");
            };
        }
    }

    /**
     * How strict the validation should be. Supplied by each caller as validation strictness is
     * configured per protocol.
     */
    public record Policy(boolean supportsRecipientRewriteTable, RecipientRewriteTableCheck recipientRewriteTableCheck) {
        public static final Policy DEFAULT = new Policy(true, RecipientRewriteTableCheck.MAPPING_EXISTS);

        public static Policy from(Configuration config) throws ConfigurationException {
            return new Policy(
                config.getBoolean(ENABLE_RECIPIENT_REWRITE_TABLE_PROPERTY, true),
                RecipientRewriteTableCheck.parse(config.getString(RECIPIENT_REWRITE_TABLE_CHECK_PROPERTY, "mappingExists")));
        }
    }

    private final UsersRepository users;
    private final RecipientRewriteTable recipientRewriteTable;
    private final DomainList domains;

    @Inject
    public RecipientValidator(UsersRepository users, RecipientRewriteTable recipientRewriteTable, DomainList domains) {
        this.users = users;
        this.recipientRewriteTable = recipientRewriteTable;
        this.domains = domains;
    }

    /**
     * Return true if email for the given recipient should get accepted. Recipients of non local
     * domains are accepted as their validity cannot be assessed locally.
     */
    public boolean isValidRecipient(MailAddress recipient, Policy policy) throws DomainListException, UsersRepositoryException, RecipientRewriteTableException {
        return !isLocalDomain(recipient.getDomain()) || isValidLocalRecipient(recipient, policy);
    }

    /**
     * Return true if email for the given recipient of a local domain should get accepted
     */
    public boolean isValidLocalRecipient(MailAddress recipient, Policy policy) throws UsersRepositoryException, RecipientRewriteTableException {
        // Check existence of mailbox first to use RRT less often.
        if (mailboxExists(recipient)) {
            return true;
        } else {
            // Check whether there is a valid RRT entry for the recipient.
            return policy.supportsRecipientRewriteTable() && hasValidRRTEntry(recipient, policy.recipientRewriteTableCheck());
        }
    }

    /**
     * Return true if the domain is local
     */
    public boolean isLocalDomain(Domain domain) throws DomainListException {
        return domains.containsDomain(domain);
    }

    public boolean mailboxExists(MailAddress recipient) throws UsersRepositoryException {
        return users.contains(users.getUsername(recipient));
    }

    public boolean hasValidRRTEntry(MailAddress recipient, RecipientRewriteTableCheck check) throws RecipientRewriteTableException, UsersRepositoryException {
        LOGGER.debug("Unknown recipient {}, resolving it via RRT", recipient);

        try {
            return switch (check) {
                case MAPPING_EXISTS -> {
                    Mappings mappings = recipientRewriteTable.getResolvedMappings(recipient.getLocalPart(), recipient.getDomain());
                    yield !mappings.isEmpty();
                }
                case ANY_TARGET_HAS_LOCAL_MAILBOX -> {
                    // As long as there is any mapping to a local mailbox, this check passes.
                    // Error mappings are therefore irrelevant.
                    Mappings mappings = recipientRewriteTable.getResolvedMappings(
                        recipient.getLocalPart(),
                        recipient.getDomain(),
                        EnumSet.complementOf(EnumSet.of(Mapping.Type.Error))
                    );
                    yield anyResolvedMailboxExists(mappings);
                }
                case ALL_TARGETS_HAVE_LOCAL_MAILBOX -> {
                    Mappings mappings = recipientRewriteTable.getResolvedMappings(recipient.getLocalPart(), recipient.getDomain());
                    yield allResolvedMailboxesExist(mappings);
                }
            };
        } catch (ErrorMappingException e) {
            // Either an error mapping was encountered (ErrorMappingException) or the limit for recursively
            // resolving a mapping was reached (TooManyMappingException).
            return switch (check) {
                case MAPPING_EXISTS -> {
                    // An error during mapping means that a mapping exists.
                    LOGGER.info("Error while resolving recipient via RRT, allowing recipient {}: ", recipient, e);
                    yield true;
                }
                case ANY_TARGET_HAS_LOCAL_MAILBOX -> {
                    // It is unclear whether at least one mapping is valid.
                    LOGGER.info("Error while resolving recipient via RRT, refusing recipient {}: ", recipient, e);
                    yield false;
                }
                case ALL_TARGETS_HAVE_LOCAL_MAILBOX -> {
                    // It is unclear whether all mappings are valid.
                    LOGGER.info("Error while resolving recipient via RRT, refusing recipient {}: ", recipient, e);
                    yield false;
                }
            };
        }
    }

    private boolean anyResolvedMailboxExists(Mappings mappings) throws UsersRepositoryException {
        for (Mapping mapping : mappings) {
            Optional<MailAddress> email = mapping.asMailAddress();
            if (email.isPresent() && mailboxExists(email.get())) {
                return true;
            }
        }
        return false;
    }

    private boolean allResolvedMailboxesExist(Mappings mappings) throws UsersRepositoryException {
        for (Mapping mapping : mappings) {
            Optional<MailAddress> email = mapping.asMailAddress();
            if (email.isEmpty() || !mailboxExists(email.get())) {
                return false;
            }
        }
        return !mappings.isEmpty();
    }
}
