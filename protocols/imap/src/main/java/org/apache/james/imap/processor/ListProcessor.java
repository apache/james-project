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
import static org.apache.james.imap.message.request.ListRequest.ListSelectOption.SPECIAL_USE;
import static org.apache.james.mailbox.MailboxManager.MailboxSearchFetchType.Minimal;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.display.ModifiedUtf7;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.StatusDataItems;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.MailboxType;
import org.apache.james.imap.api.process.MailboxTyper;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.MailboxName;
import org.apache.james.imap.message.request.ListRequest;
import org.apache.james.imap.message.response.ListResponse;
import org.apache.james.imap.message.response.MailboxStatusResponse;
import org.apache.james.imap.message.response.MyRightsResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.search.MailboxNameExpression;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.model.search.PrefixedRegex;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ListProcessor<T extends ListRequest> extends AbstractMailboxProcessor<T> implements CapabilityImplementingProcessor {
    public static final boolean RETURN_SUBSCRIBED = true;
    public static final boolean RETURN_NON_EXISTENT = true;
    private static final Logger LOGGER = LoggerFactory.getLogger(ListProcessor.class);
    private static final List<Capability> CAPA = ImmutableList.of(
        Capability.of("LIST-EXTENDED"),
        Capability.of("LIST-STATUS"),
        Capability.of("LIST-MYRIGHTS"),
        Capability.of("SPECIAL-USE"));

    private final SubscriptionManager subscriptionManager;
    private final StatusProcessor statusProcessor;
    protected final MailboxTyper mailboxTyper;

    private final PathConverter.Factory pathConverterFactory;

    @Inject
    public ListProcessor(MailboxManager mailboxManager, StatusResponseFactory factory,
                         MetricFactory metricFactory, SubscriptionManager subscriptionManager,
                         StatusProcessor statusProcessor, MailboxTyper mailboxTyper, PathConverter.Factory pathConverterFactory) {
        this((Class<T>) ListRequest.class, mailboxManager, factory, metricFactory, subscriptionManager, statusProcessor, mailboxTyper, pathConverterFactory);
    }

    public ListProcessor(Class<T> clazz, MailboxManager mailboxManager, StatusResponseFactory factory,
                         MetricFactory metricFactory, SubscriptionManager subscriptionManager,
                         StatusProcessor statusProcessor, MailboxTyper mailboxTyper, PathConverter.Factory pathConverterFactory) {
        super(clazz, mailboxManager, factory, metricFactory);
        this.subscriptionManager = subscriptionManager;
        this.statusProcessor = statusProcessor;
        this.mailboxTyper = mailboxTyper;
        this.pathConverterFactory = pathConverterFactory;
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
                return ReactorUtils.logAsMono(() -> LOGGER.error("List failed for mailboxName {}", request.getMailboxPatterns(), e));
            })
            .then();
    }

    private Mono<Void> respond(ImapSession session, Responder responder, T request, MailboxSession mailboxSession) {
        if (request.isHierarchyDelimiterRequest()) {
            return Mono.fromRunnable(() -> respondNamespace(request.getBaseReferenceName(), responder, mailboxSession));
        } else {
            return respondMailboxList(request, session, responder, mailboxSession);
        }
    }

    protected ImapResponseMessage createResponse(MailboxMetaData.Children children, MailboxMetaData.Selectability selectability, String name,
                                                 char hierarchyDelimiter, MailboxType type, boolean isSubscribed) {
        return new ListResponse(children, selectability, name, hierarchyDelimiter, isSubscribed,
            !RETURN_NON_EXISTENT, EnumSet.noneOf(ListResponse.ChildInfo.class), type);
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
            MailboxType.OTHER,
            !RETURN_SUBSCRIBED));
    }

    private String computeReferenceRoot(String referenceName, MailboxSession mailboxSession) {
        if (!referenceName.isEmpty() && referenceName.charAt(0) == MailboxConstants.NAMESPACE_PREFIX_CHAR) {
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

        if (request.getMailboxPatterns().isEmpty()) {
            // RFC 5258: empty patterns of a pattern list are ignored for the purpose of matching
            return Mono.empty();
        }

        ListPatterns patterns = new ListPatterns(request.getMailboxPatterns().stream()
            .map(mailboxPattern -> listPattern(request, mailboxPattern, session, mailboxSession))
            .collect(ImmutableList.toImmutableList()));

        if (request.selectSubscribed()) {
            return processWithSubscribed(session, request, responder, mailboxSession, patterns);
        } else if (request.getReturnOptions().contains(ListRequest.ListReturnOption.SUBSCRIBED)) {
            return Flux.from(Throwing.supplier(() -> subscriptionManager.subscriptionsReactive(mailboxSession)).get())
                .collect(ImmutableMap.toImmutableMap(path -> path, path -> path))
                .flatMap(subscribed -> processWithoutSubscribed(session, request, responder, mailboxSession, patterns, subscribed::containsKey));
        } else {
            return processWithoutSubscribed(session, request, responder, mailboxSession, patterns, any -> false);
        }
    }

    private ListPattern listPattern(T request, String mailboxPattern, ImapSession session, MailboxSession mailboxSession) {
        // Is the interpreted (combined) pattern relative?
        // Should the namespace section be returned or not?
        boolean isRelative = ((request.getBaseReferenceName() + mailboxPattern).charAt(0) != MailboxConstants.NAMESPACE_PREFIX_CHAR);

        MailboxQuery mailboxQuery = pathConverterFactory.forSession(session)
            .mailboxQuery(request.getBaseReferenceName(), mailboxPattern, session);

        return new ListPattern(mailboxQuery, isRelative, subscribedPredicate(mailboxSession, isRelative, request.getBaseReferenceName(), mailboxPattern));
    }

    private record ListPattern(MailboxQuery mailboxQuery, boolean isRelative, Predicate<MailboxPath> subscribedPredicate) {
    }

    /**
     * RFC 5258 section 3: a mailbox name matches a list of mailbox patterns if it matches at least one of them, and
     * a single LIST response is returned for a mailbox matching several patterns.
     *
     * The first matching pattern decides how the mailbox name is answered (relative or not).
     */
    private record ListPatterns(List<ListPattern> patterns) {
        /**
         * Rather than one search per pattern, a single search is done per distinct namespace and user restriction of the
         * patterns: it returns every mailbox of that scope, these results are then filtered against each pattern.
         */
        Flux<MailboxMetaData> search(MailboxManager mailboxManager, MailboxSession mailboxSession) {
            if (patterns.size() == 1) {
                return mailboxManager.search(patterns.get(0).mailboxQuery(), Minimal, mailboxSession);
            }
            List<MailboxQuery> scopeQueries = patterns.stream()
                .map(pattern -> scopeQuery(pattern.mailboxQuery()))
                .distinct()
                .collect(ImmutableList.toImmutableList());
            if (scopeQueries.size() == 1) {
                return mailboxManager.search(scopeQueries.get(0), Minimal, mailboxSession);
            }
            return Flux.fromIterable(scopeQueries)
                .concatMap(scopeQuery -> mailboxManager.search(scopeQuery, Minimal, mailboxSession))
                .collectMap(MailboxMetaData::getPath, Function.identity(), LinkedHashMap::new)
                .flatMapIterable(Map::values)
                .sort(MailboxMetaData.COMPARATOR);
        }

        private static MailboxQuery scopeQuery(MailboxQuery mailboxQuery) {
            MailboxQuery.Builder builder = MailboxQuery.builder();
            mailboxQuery.getNamespace().ifPresent(builder::namespace);
            mailboxQuery.getUser().ifPresent(builder::username);
            return builder.matchesAllMailboxNames().build();
        }

        Optional<ListPattern> matchingSearchResult(MailboxPath path) {
            if (patterns.size() == 1) {
                // The search already filtered the results against the pattern
                return Optional.of(patterns.get(0));
            }
            return patterns.stream()
                .filter(pattern -> pattern.mailboxQuery().isPathMatch(path))
                .findFirst();
        }

        Optional<ListPattern> matchingSubscription(MailboxPath subscribed) {
            return patterns.stream()
                .filter(pattern -> pattern.mailboxQuery().isPathMatch(subscribed))
                .filter(pattern -> pattern.subscribedPredicate().test(subscribed))
                .findFirst();
        }
    }

    private Mono<Void> processWithoutSubscribed(ImapSession session, T request, Responder responder, MailboxSession mailboxSession,
                                                ListPatterns patterns, Predicate<MailboxPath> isSubscribed) {
        return patterns.search(getMailboxManager(), mailboxSession)
            .mapNotNull(metaData -> patterns.matchingSearchResult(metaData.getPath())
                .map(pattern -> Pair.of(metaData, pattern.isRelative()))
                .orElse(null))
            .doOnNext(metaDataAndRelativity -> {
                MailboxMetaData metaData = metaDataAndRelativity.getLeft();
                boolean isRelative = metaDataAndRelativity.getRight();
                MailboxType mailboxType = getMailboxType(request, session, metaData.getPath());
                if (!request.getSelectOptions().contains(SPECIAL_USE) || mailboxType.getRfc6154attributeName() != null) {
                    pathConverterFactory.forSession(session).mailboxName(isRelative, metaData.getPath(), mailboxSession)
                            .ifPresent(mailboxName -> responder.respond(
                                createResponse(metaData.inferiors(),
                                    metaData.getSelectability(),
                                    mailboxName,
                                    metaData.getHierarchyDelimiter(),
                                    mailboxType,
                                    isSubscribed.test(metaData.getPath()))));
                }
            })
            .doOnNext(metaDataAndRelativity -> respondMyRights(request, responder, mailboxSession, metaDataAndRelativity.getLeft(), metaDataAndRelativity.getRight()))
            .map(Pair::getLeft)
            .concatMap(metaData -> request.getStatusDataItems().map(statusDataItems -> statusProcessor.sendStatus(retrieveMessageManager(metaData, mailboxSession), statusDataItems, responder, session, mailboxSession)).orElse(Mono.empty()))
            .then();
    }

    private MessageManager retrieveMessageManager(MailboxMetaData metaData, MailboxSession mailboxSession) {
        try {
            return getMailboxManager().getMailbox(metaData.getMailbox(), mailboxSession);
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        }
    }

    private Mono<Void> processWithSubscribed(ImapSession session, T request, Responder responder, MailboxSession mailboxSession, ListPatterns patterns) {
        return Mono.zip(patterns.search(getMailboxManager(), mailboxSession)
                    .filter(metaData -> patterns.matchingSearchResult(metaData.getPath()).isPresent())
                    .collectList()
                    .map(searchedResultList -> searchedResultList.stream().collect(Collectors.toMap(MailboxMetaData::getPath, Function.identity()))),
                Flux.from(Throwing.supplier(() -> subscriptionManager.subscriptionsReactive(mailboxSession)).get()).collectList())
            .map(tuple -> getListResponseForSelectSubscribed(session, tuple.getT1(), tuple.getT2(), request, mailboxSession, patterns))
            .flatMapIterable(list -> list)
            .doOnNext(pathAndResponse -> responder.respond(pathAndResponse.response()))
            .doOnNext(pathAndResponse -> pathAndResponse.metaData().ifPresent(mailboxMetaData -> respondMyRights(request, responder, mailboxSession, mailboxMetaData, pathAndResponse.isRelative())))
            .concatMap(pathAndResponse -> sendStatusWhenSubscribed(session, request, responder, mailboxSession, pathAndResponse))
            .then();
    }

    private record SubscribedListResponse(MailboxPath path, ListResponse response, Optional<MailboxMetaData> metaData, boolean isRelative) {
    }

    private Mono<MailboxStatusResponse> sendStatusWhenSubscribed(ImapSession session, T request, Responder responder, MailboxSession mailboxSession,
                                                                 SubscribedListResponse pathAndResponse) {
        return pathAndResponse.metaData()
            .map(metaData -> retrieveMessageManager(metaData, mailboxSession))
            .flatMap(messageManager -> request.getStatusDataItems()
                .map(statusDataItems -> statusProcessor.sendStatus(messageManager, statusDataItems, responder, session, mailboxSession)))
            .orElse(Mono.empty());
    }

    private List<SubscribedListResponse> getListResponseForSelectSubscribed(ImapSession session, Map<MailboxPath, MailboxMetaData> searchedResultMap, List<MailboxPath> allSubscribedSearch,
                                                                            ListRequest listRequest, MailboxSession mailboxSession, ListPatterns patterns) {
        ImmutableList.Builder<SubscribedListResponse> responseBuilders = ImmutableList.builder();
        List<Pair<MailboxPath, ListResponse>> listRecursiveMatch = listRecursiveMatch(session, searchedResultMap, allSubscribedSearch, mailboxSession, patterns, listRequest);

        listRecursiveMatch.forEach(pair -> responseBuilders.add(new SubscribedListResponse(pair.getLeft(), pair.getRight(), Optional.ofNullable(searchedResultMap.get(pair.getLeft())),
            isRelative(patterns, pair.getLeft()))));
        Set<MailboxPath> listRecursiveMatchPath = listRecursiveMatch.stream().map(Pair::getKey).collect(Collectors.toUnmodifiableSet());

        allSubscribedSearch.stream()
            .distinct()
            .filter(subscribed -> !listRecursiveMatchPath.contains(subscribed))
            .flatMap(subscribed -> patterns.matchingSubscription(subscribed)
                .flatMap(pattern -> buildListResponse(listRequest, searchedResultMap, session, pattern.isRelative(), subscribed)
                    .map(pair -> new SubscribedListResponse(pair.getLeft(), pair.getRight(), Optional.ofNullable(searchedResultMap.get(pair.getLeft())), pattern.isRelative())))
                .stream())
            .filter(response -> !listRequest.getSelectOptions().contains(SPECIAL_USE) || mailboxTyper.getMailboxType(session, response.path()).getRfc6154attributeName() != null)
            .forEach(responseBuilders::add);

        return responseBuilders.build();
    }

    private boolean isRelative(ListPatterns patterns, MailboxPath searchedPath) {
        return patterns.matchingSearchResult(searchedPath)
            .map(ListPattern::isRelative)
            .orElse(patterns.patterns().get(0).isRelative());
    }

    private Predicate<MailboxPath> subscribedPredicate(MailboxSession mailboxSession, boolean isRelative, String referenceName, String mailboxPattern) {
        MailboxNameExpression subscribeExpression = new PrefixedRegex(
            referenceName,
            ModifiedUtf7.decodeModifiedUTF7(mailboxPattern),
            mailboxSession.getPathDelimiter());

        PathConverter pathConverter = pathConverterFactory.forSession(mailboxSession);

        return subscribedMailboxPath -> pathConverter.mailboxName(isRelative, subscribedMailboxPath, mailboxSession)
            .map(subscribeExpression::isExpressionMatch).orElse(false);
    }

    private Optional<Pair<MailboxPath, ListResponse>> buildListResponse(ListRequest listRequest, Map<MailboxPath, MailboxMetaData> searchedResultMap, ImapSession session, boolean relative, MailboxPath subscribed) {
        return pathConverterFactory.forSession(session).mailboxName(relative, subscribed, session.getMailboxSession())
            .map(name -> Pair.of(subscribed, Optional.ofNullable(searchedResultMap.get(subscribed))
            .map(mailboxMetaData -> ListResponse.builder()
                .returnSubscribed(RETURN_SUBSCRIBED)
                .forMetaData(mailboxMetaData)
                .name(name)
                .returnNonExistent(!RETURN_NON_EXISTENT)
                .mailboxType(getMailboxType(listRequest, session, mailboxMetaData.getPath())))
            .orElseGet(() -> ListResponse.builder().nonExitingSubscribedMailbox(subscribed))
            .build()));
    }

    private List<Pair<MailboxPath, ListResponse>> listRecursiveMatch(ImapSession session, Map<MailboxPath, MailboxMetaData> searchedResultMap,
                                                                     List<MailboxPath> allSubscribedSearch, MailboxSession mailboxSession,
                                                                     ListPatterns patterns, ListRequest listRequest) {
        if (!listRequest.getSelectOptions().contains(RECURSIVEMATCH)) {
            return List.of();
        }

        Set<MailboxPath> allSubscribedSearchParent = allSubscribedSearch.stream()
            .flatMap(mailboxPath -> mailboxPath.getParents(mailboxSession.getPathDelimiter()).stream())
            .collect(Collectors.toSet());

        return searchedResultMap.entrySet().stream()
            .filter(pair -> allSubscribedSearchParent.contains(pair.getKey()))
            .flatMap(pair -> {
                MailboxMetaData metaData = pair.getValue();
                Optional<String> maybeMailboxName = pathConverterFactory.forSession(session).mailboxName(isRelative(patterns, metaData.getPath()), metaData.getPath(), mailboxSession);
                return maybeMailboxName.map(mailboxName -> Pair.of(pair.getKey(), ListResponse.builder()
                    .forMetaData(metaData)
                    .name(mailboxName)
                    .childInfos(ListResponse.ChildInfo.SUBSCRIBED)
                    .returnSubscribed(allSubscribedSearch.contains(pair.getKey()))
                    .mailboxType(getMailboxType(listRequest, session, metaData.getPath()))
                    .build()))
                    .stream();
            })
            .collect(Collectors.toList());
    }

    private void respondMyRights(T request, Responder responder, MailboxSession mailboxSession, MailboxMetaData metaData, boolean isRelative) {
        if (request.getReturnOptions().contains(ListRequest.ListReturnOption.MYRIGHTS)) {
            pathConverterFactory.forSession(mailboxSession)
                .mailboxName(isRelative, metaData.getPath(), mailboxSession)
                .map(MailboxName::new)
                .map(mailboxName -> new MyRightsResponse(mailboxName, getRfc4314Rights(mailboxSession, metaData)))
                .ifPresent(responder::respond);
        }
    }

    private MailboxACL.Rfc4314Rights getRfc4314Rights(MailboxSession mailboxSession, MailboxMetaData metaData) {
        if (metaData.getPath().belongsTo(mailboxSession)) {
            return MailboxACL.FULL_RIGHTS;
        }
        MailboxACL.EntryKey entryKey = MailboxACL.EntryKey.createUserEntryKey(mailboxSession.getUser());
        return metaData.getResolvedAcls().getEntries().get(entryKey);
    }

    /**
     * retrieve mailboxType for specified mailboxPath using provided
     * MailboxTyper
     *
     * @param session current imap session
     * @param path    mailbox's path
     * @return MailboxType value
     */
    protected MailboxType getMailboxType(ListRequest listRequest, ImapSession session, MailboxPath path) {
        if (listRequest.getReturnOptions().contains(ListRequest.ListReturnOption.SPECIAL_USE)) {
            return mailboxTyper.getMailboxType(session, path);
        }
        return MailboxType.OTHER;
    }

    @Override
    protected MDCBuilder mdc(T request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "LIST")
            .addToContext("base", request.getBaseReferenceName())
            .addToContext("pattern", String.join(" ", request.getMailboxPatterns()))
            .addToContext("returnOptions", request.getReturnOptions().toString())
            .addToContext("selectOptions", request.getSelectOptions().toString())
            .addToContextIfPresent("statusItems", request.getStatusDataItems().map(StatusDataItems::toString));
    }
}
