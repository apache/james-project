# 50. Implement web push for JMAP

Date: 2021-10-18

## Status

Accepted (lazy consensus).

Implemented.

## Context

**Notification for newly received emails** is a common feature of modern mail applications. Furthermore,
**real time** updates across devices are a common feature. We wish to provide support for this on top
of Apache James JMAP implementation for a large variety of devices.

Dealing with mobile devices yield a unique set of challenges at the protocol level, including 
low bandwidth connection, long latencies, and the need to save battery. As such, mobile devices 
operating systems tend to limit background connections for applications. Thus, in order to receive
notifications when the application is running on the background, one cannot rely on 
[ADR 0047](0047-jmap-push-over-websockets.md) (JMAP websocket push) - as it would imply 
the application maintaining a persistent connection to the server, which is battery/network
consuming.

As such, [RFC-8620 section 7.2](https://jmap.io/spec-core.html#pushsubscription) `JMAP specification for web push`
introduces the use of [RFC-8030](https://datatracker.ietf.org/doc/html/rfc8030) `Generic Event Delivery Using HTTP Push`
using an intermediate push server: a Push Gateway is used by the device to multiplex all push of all applications on a 
single connection. The device then register the URL of its push gateway on the application server (here JMAP server) for
it to forward `StateChange` events to the push gateway.

## Decision

Implement [RFC-8620 section 7.2](https://jmap.io/spec-core.html#pushsubscription) `JMAP specification for web push`.

We will store StateChanges, provide a Cassandra implementation of this storage for the Distributed application.

We will implement a PushClient based on reactor-netty to send events to the push gateway.

We will rely on a Group listener plugged on the JMAP event bus to handle the web push - See 
[ADR 37: Event bus](0037-eventbus.md).

We will implement the `PushSubscription/get` and `PushSubscription/set` JMAP methods.

We will also implement the verification code mechanism (a first round-trip to verify the push gateway works well).

We also decided to allow a single push subscription per devideId (which includes device and APP identifier...)

## Consequences

The notification traffic transits by some third party infrastructure which can both be a security and privacy risk. 
[RFC-8291](https://datatracker.ietf.org/doc/html/rfc8291) `Message Encryption for Web Push` introduces encryption 
for web push messages and is integrated to JMAP web push mechanisms. This SHOULD be implemented. `google/tink` library
provides utilities to easily do this.

Our testing strategy will rely on a [mock HTTP server](https://www.mock-server.com/) mimicking the behavior of a push 
gateway.

The push gateway can support rate limiting and might reject some of the messages pushed to it (HTTP `429` too many
requests). Please note JMAP `StateChanges` is resilient to message loss (as only the latest state of
the underlying object is transmitted, missing intermediate states does not imply missing changes). We do not plan on 
implementing retries nor adaptive push rate. 

## References

 - [RFC-8620 section 7.2](https://jmap.io/spec-core.html#pushsubscription) JMAP specification for web push
 - [PR of this ADR](https://github.com/apache/james-project/pull/xxx)
 - [JIRA ticket about this ADR](https://issues.apache.org/jira/browse/JAMES-3539)
 - [RFC-8030](https://datatracker.ietf.org/doc/html/rfc8030) `Generic Event Delivery Using HTTP Push`
 - [RFC-8291](https://datatracker.ietf.org/doc/html/rfc8291) `Message Encryption for Web Push`
 - [google/tink webpush encryption](https://github.com/google/tink/blob/master/apps/webpush/src/main/java/com/google/crypto/tink/apps/webpush/WebPushHybridDecrypt.java)