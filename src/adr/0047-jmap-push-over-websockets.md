# 47. JMAP PUSH over WebSockets

Date: 2021-01-26

## Status

Accepted (lazy consensus).

Implemented.

Relates to [45. Support JMAP Push with Mailbox/changes implementation](0045-support-jmap-push-with-mailbox-changes-implementation.md)

Requires [46. Generalize EventBus](0046-generalize-event-bus.md)

## Context

JMAP Push notifications allow clients to efficiently update (almost) instantly to stay in sync with data changes on the server. 

In order to support this, we need to have the server emit newest state changes to the client over a PUSH channel.

## Decision

We will implement [RFC-8887 - A JSON Meta Application Protocol (JMAP) Subprotocol for WebSocket](https://tools.ietf.org/html/rfc8887) 
as a transport mechanism for PUSH notifications.

We will [generalize EventBus](0046-generalize-event-bus.md) in order to achieve an out-of-the box Publish-Subscribe 
system for JMAP related events, that does not fit in the mailbox API.

We will implement listeners registered on the JMAP event bus for WebSocket clients in order to carry over state changes
to the client.

## Consequences

We expect clients using the PUSH to lead to a drastic performance enhancement, as less data needs to be transmitted upon
resynchronisation.

As mentioned in RFC-8887 the usage of webSockets allows other performance optimizations:
 - Requests can easily be compressed, which is not doable over HTTP for most available implementation (HTTP request 
compression is not ubiquitous).
 - WebSockets being connected, authentication can be performed once, when establishing the connection. This can allow to
reduce the load, if needed, on authentication systems. It might ease the use for instance of custom OpenId connect 
providers.

People deploying JMAP need to be aware that load-balancing webSockets requires session stickiness.

## Sequence

 1. Bob authenticates against the `ws://` endpoints. Upgrade to websockets is granted.
 2. Bob registers Email and Mailbox updates. A listener listens for state changes related to Bob account.
 3. Bob receives a mail. The MailboxManager adds it to Bob's mailbox. An `Added` event is fired on the mailbox event bus.
 4. The `MailboxChangeListener` processes the Added event, handles delegation, records the state change, and fires related
 events for each account on the JMAP event bus, for both `Email` (as there is an addition) and `Mailbox` (as the counts 
 were updated).
 5. Bob's webSocket listener receives a message from RabbitMQ and pushes it to Bob.
 6. Bob's MUA is aware it needs to re-synchronize. It will perform resynch requests combining `Email/changes`, `Email/get`,
 `Mailbox/changes` and `Mailbox/get`.
 
Event bus listener are created for each socket upon client requests, and removed upon disconnection or when PUSH is
explicitly canceled by the client.

In a multi-node setup, the event bus registration key mechanism described in [this ADR](0037-eventbus.md) and its 
[distributed implementation](0038-distributed-eventbus.md) ensure events are routed to
the James servers holding client PUSH registration. We will use the AccountId as a base for a registration key.
 
## Alternatives

The [JMAP RFC](https://tools.ietf.org/html/rfc8620) defines [event source](https://www.w3.org/TR/eventsource/) (Server 
Sent Events) as a supported transport medium for PUSH notification. Yet:

 - Active JMAP contributors lack production experience on Event source while they do already deploy webSockets
 - Performance enhancements (authentication and request compression) unlocked by webSockets are not achievable via event 
 source.
 
 Note that nothing refrains about future implementation of event source mechanism, that, if need be can be ignored or
 disabled via a reverse proxy.
 
## References

- [Support JMAP WebSocket PUSH](https://issues.apache.org/jira/browse/JAMES-3491)
