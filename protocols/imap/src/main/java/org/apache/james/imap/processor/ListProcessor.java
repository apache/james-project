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

package org.apache.james.imap.processor;

import static org.apache.james.imap.message.request.ListRequest.ListSelectOption.RECURSIVEMATCH;
import static org.apache.james.mailbox.MailboxManager.MailboxSearchFetchType.Minimal;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.display.ModifiedUtf7;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.MailboxType;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.ListRequest;
import org.apache.james.imap.message.response.ListResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.model.search.PrefixedRegex;
import org.apache.james.mailbox.model.search.Wildcard;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ListProcessor<T extends ListRequest> extends AbstractMailboxProcessor<T> implements CapabilityImplementingProcessor {
    public static final boolean RETURN_SUBSCRIBED = true;
    public static final boolean RETURN_NON_EXISTENT = true;
    private static final Logger LOGGER = LoggerFactory.getLogger(ListProcessor.class);
    private static final List<Capability> CAPA = ImmutableList.of(Capability.of("LIST-EXTENDED"), Capability.of("LIST-STATUS"));

    private final SubscriptionManager subscriptionManager;
    private final StatusProcessor statusProcessor;

    public ListProcessor(MailboxManager mailboxManager, StatusResponseFactory factory,
                         MetricFactory metricFactory, SubscriptionManager subscriptionManager,
                         StatusProcessor statusProcessor) {
        this((Class<T>) ListRequest.class, mailboxManager, factory, metricFactory, subscriptionManager, statusProcessor);
    }

    public ListProcessor(Class<T> clazz, MailboxManager mailboxManager, StatusResponseFactory factory,
                         MetricFactory metricFactory, SubscriptionManager subscriptionManager,
                         StatusProcessor statusProcessor) {
        super(clazz, mailboxManager, factory, metricFactory);
        this.subscriptionManager = subscriptionManager;
        this.statusProcessor = statusProcessor;
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        return CAPA;
    }

    /**
     * (from rfc3501)<br>
     * The LIST command returns a subset of names from the complete set of all
     * names available to the client. Zero or more untagged LIST replies are
     * returned, containing the name attributes, hierarchy delimiter, and name;
     * see the description of the LIST reply for more detail.<br>
     * ...<br>
     * An empty ("" string) mailbox name argument is a special request to return
     * the hierarchy delimiter and the root name of the name given in the
     * reference. The value returned as the root MAY be the empty string if the
     * reference is non-rooted or is an empty string.
     */

    @Override
    protected Mono<Void> processRequestReactive(T request, ImapSession session, Responder responder) {
        MailboxSession mailboxSession = session.getMailboxSession();

        return respond(session, responder, request, mailboxSession)
            .then(Mono.fromRunnable(() -> okComplete(request, responder)))
            .onErrorResume(MailboxException.class, e -> {
                no(request, responder, HumanReadableText.SEARCH_FAILED);
                return ReactorUtils.logAsMono(() -> LOGGER.error("List failed for mailboxName {}", request.getMailboxPattern(), e));
            })
            .then();
    }

    private Mono<Void> respond(ImapSession session, Responder responder, T request, MailboxSession mailboxSession) {
        if (request.getMailboxPattern().length() == 0) {
            return Mono.fromRunnable(() -> respondNamespace(request.getBaseReferenceName(), responder, mailboxSession));
        } else {
            return respondMailboxList(request, session, responder, mailboxSession);
        }
    }

    protected ImapResponseMessage createResponse(MailboxMetaData.Children children, MailboxMetaData.Selectability selectability, String name,
                                                 char hierarchyDelimiter, MailboxType type) {
        return new ListResponse(children, selectability, name, hierarchyDelimiter, !RETURN_SUBSCRIBED,
            !RETURN_NON_EXISTENT, EnumSet.noneOf(ListResponse.ChildInfo.class));
    }

    private void respondNamespace(String referenceName, Responder responder, MailboxSession mailboxSession) {
        // An empty mailboxName signifies a request for the hierarchy
        // delimiter and root name of the referenceName argument
        String referenceRoot = ModifiedUtf7.decodeModifiedUTF7(computeReferenceRoot(referenceName, mailboxSession));

        responder.respond(createResponse(
            MailboxMetaData.Children.CHILDREN_ALLOWED_BUT_UNKNOWN,
            MailboxMetaData.Selectability.NOSELECT,
            referenceRoot,
            mailboxSession.getPathDelimiter(),
            MailboxType.OTHER));
    }

    private String computeReferenceRoot(String referenceName, MailboxSession mailboxSession) {
        if (referenceName.length() > 0 && referenceName.charAt(0) == MailboxConstants.NAMESPACE_PREFIX_CHAR) {
            // A qualified reference name - get the root element
            int firstDelimiter = referenceName.indexOf(mailboxSession.getPathDelimiter());
            if (firstDelimiter == -1) {
                return referenceName;
            } else {
                return referenceName.substring(0, firstDelimiter);
            }
        } else {
            // A relative reference name, return "" to indicate it is
            // non-rooted
            return "";
        }
    }

    private Mono<Void> respondMailboxList(T request, ImapSession session,
                                          Responder responder, MailboxSession mailboxSession) {
        if (request.selectRemote()) {
            // https://www.rfc-editor.org/rfc/rfc5258.html. NOT YET SUPPORT `REMOTE`
            return Mono.empty();
        }

        // If the mailboxPattern is fully qualified, ignore the
        // reference name.
        String finalReferencename = request.getBaseReferenceName();
        if (request.getMailboxPattern().charAt(0) == MailboxConstants.NAMESPACE_PREFIX_CHAR) {
            finalReferencename = "";
        }
        // Is the interpreted (combined) pattern relative?
        // Should the namespace section be returned or not?
        boolean isRelative = ((finalReferencename + request.getMailboxPattern()).charAt(0) != MailboxConstants.NAMESPACE_PREFIX_CHAR);

        MailboxQuery mailboxQuery = mailboxQuery(computeBasePath(session, finalReferencename, isRelative),
            request.getMailboxPattern(), mailboxSession);

        if (request.selectSubscribed()) {
            return processWithSubscribed(session, request, responder, mailboxSession, isRelative, mailboxQuery);
        } else {
            return processWithoutSubscribed(session, request, responder, mailboxSession, isRelative, mailboxQuery);
        }
    }

    private Mono<Void> processWithoutSubscribed(ImapSession session, T request, Responder responder, MailboxSession mailboxSession, boolean isRelative, MailboxQuery mailboxQuery) {
        return getMailboxManager().search(mailboxQuery, Minimal, mailboxSession)
            .doOnNext(metaData -> responder.respond(
                createResponse(metaData.inferiors(),
                    metaData.getSelectability(),
                    mailboxName(isRelative, metaData.getPath(), metaData.getHierarchyDelimiter()),
                    metaData.getHierarchyDelimiter(),
                    getMailboxType(session, metaData.getPath()))))
            .flatMap(metaData -> request.getStatusDataItems().map(statusDataItems -> statusProcessor.sendStatus(metaData.getPath(), statusDataItems, responder, session, mailboxSession)).orElse(Mono.empty()))
            .then();
    }

    private Mono<Void> processWithSubscribed(ImapSession session, T request, Responder responder, MailboxSession mailboxSession, boolean isRelative, MailboxQuery mailboxQuery) {
        return Mono.zip(getMailboxManager().search(mailboxQuery, Minimal, mailboxSession).collectList()
                    .map(searchedResultList -> searchedResultList.stream().collect(Collectors.toMap(MailboxMetaData::getPath, Function.identity()))),
                Flux.from(Throwing.supplier(() -> subscriptionManager.subscriptionsReactive(mailboxSession)).get()).collectList())
            .map(tuple -> getListResponseForSelectSubscribed(tuple.getT1(), tuple.getT2(), request, mailboxSession, isRelative, mailboxQuery))
            .flatMapIterable(list -> list)
            .doOnNext(pathAndResponse -> responder.respond(pathAndResponse.getRight()))
            .flatMap(pathAndResponse -> request.getStatusDataItems().map(statusDataItems -> statusProcessor.sendStatus(pathAndResponse.getLeft(), statusDataItems, responder, session, mailboxSession)).orElse(Mono.empty()))
            .then();
    }

    private List<Pair<MailboxPath, ListResponse>> getListResponseForSelectSubscribed(Map<MailboxPath, MailboxMetaData> searchedResultMap, List<MailboxPath> allSubscribedSearch,
                                                                                     ListRequest listRequest, MailboxSession mailboxSession, boolean relative, MailboxQuery mailboxQuery) {
        ImmutableList.Builder<Pair<MailboxPath, ListResponse>> responseBuilders = ImmutableList.builder();
        List<Pair<MailboxPath, ListResponse>> listRecursiveMatch = listRecursiveMatch(searchedResultMap, allSubscribedSearch, mailboxSession, relative, listRequest);
        responseBuilders.addAll(listRecursiveMatch);
        Set<MailboxPath> listRecursiveMatchPath = listRecursiveMatch.stream().map(Pair::getKey).collect(Collectors.toUnmodifiableSet());

        allSubscribedSearch.stream()
            .filter(subscribed -> !listRecursiveMatchPath.contains(subscribed))
            .filter(mailboxQuery::isPathMatch)
            .map(subscribed -> buildListResponse(searchedResultMap, mailboxSession, relative, subscribed))
            .forEach(responseBuilders::add);

        return responseBuilders.build();
    }

    private Pair<MailboxPath, ListResponse> buildListResponse(Map<MailboxPath, MailboxMetaData> searchedResultMap, MailboxSession mailboxSession, boolean relative, MailboxPath subscribed) {
        return Pair.of(subscribed, Optional.ofNullable(searchedResultMap.get(subscribed))
            .map(mailboxMetaData -> ListResponse.builder()
                .returnSubscribed(RETURN_SUBSCRIBED)
                .forMetaData(mailboxMetaData)
                .name(mailboxName(relative, subscribed, mailboxMetaData.getHierarchyDelimiter()))
                .returnNonExistent(!RETURN_NON_EXISTENT))
            .orElseGet(() -> ListResponse.builder().nonExitingSubscribedMailbox(subscribed))
            .build());
    }

    private List<Pair<MailboxPath, ListResponse>> listRecursiveMatch(Map<MailboxPath, MailboxMetaData> searchedResultMap,
                                                                     List<MailboxPath> allSubscribedSearch, MailboxSession mailboxSession,
                                                                     boolean relative, ListRequest listRequest) {
        if (!listRequest.getSelectOptions().contains(RECURSIVEMATCH)) {
            return List.of();
        }

        Set<MailboxPath> allSubscribedSearchParent = allSubscribedSearch.stream()
            .flatMap(mailboxPath -> mailboxPath.getParents(mailboxSession.getPathDelimiter()).stream())
            .collect(Collectors.toSet());

        return searchedResultMap.entrySet().stream()
            .filter(pair -> allSubscribedSearchParent.contains(pair.getKey()))
            .map(pair -> {
                MailboxMetaData metaData = pair.getValue();
                ListResponse listResponse = ListResponse.builder()
                    .forMetaData(metaData)
                    .name(mailboxName(relative, metaData.getPath(), metaData.getHierarchyDelimiter()))
                    .childInfos(ListResponse.ChildInfo.SUBSCRIBED)
                    .returnSubscribed(allSubscribedSearch.contains(pair.getKey()))
                    .build();
                return Pair.of(pair.getKey(), listResponse);
            })
            .collect(Collectors.toList());
    }

    private MailboxQuery mailboxQuery(MailboxPath basePath, String mailboxName, MailboxSession mailboxSession) {
        if (basePath.getNamespace().equals(MailboxConstants.USER_NAMESPACE)
            && basePath.getUser().equals(mailboxSession.getUser())
            && basePath.getName().isEmpty()
            && mailboxName.equals("*")) {

            return MailboxQuery.builder()
                .userAndNamespaceFrom(basePath)
                .expression(Wildcard.INSTANCE)
                .build();
        }

        return MailboxQuery.builder()
            .userAndNamespaceFrom(basePath)
            .expression(new PrefixedRegex(
                basePath.getName(),
                ModifiedUtf7.decodeModifiedUTF7(mailboxName),
                mailboxSession.getPathDelimiter()))
            .build();
    }

    private MailboxPath computeBasePath(ImapSession session, String finalReferencename, boolean isRelative) {
        String decodedName = ModifiedUtf7.decodeModifiedUTF7(finalReferencename);
        if (isRelative) {
            return MailboxPath.forUser(session.getUserName(), decodedName);
        } else {
            return PathConverter.forSession(session).buildFullPath(decodedName);
        }
    }


    /**
     * retrieve mailboxType for specified mailboxPath using provided
     * MailboxTyper
     *
     * @param session current imap session
     * @param path    mailbox's path
     * @return MailboxType value
     */
    protected MailboxType getMailboxType(ImapSession session, MailboxPath path) {
        return MailboxType.OTHER;
    }

    @Override
    protected MDCBuilder mdc(T request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "LIST")
            .addToContext("base", request.getBaseReferenceName())
            .addToContext("pattern", request.getMailboxPattern());
    }
}
