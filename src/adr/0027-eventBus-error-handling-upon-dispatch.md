# 27. EventBus error handling upon dispatch

Date: 2020-04-03

## Status

Accepted (lazy consensus) & implemented

## Context

James allows asynchronous processing for mailbox events via MailboxListener. This processing is abstracted by the 
EventBus.

If the processing of an event via a mailbox listener fails, it is retried, until it succeeds. If a maxRetries parameter 
is exceeded, the event is stored in deadLetter and no further processing is attended.

The administrator can then look at the content of deadLetter to diagnose processing issues and schedule a reDelivery in 
order to retry their processing via webAdmin APIs.

However no such capabilities are supported upon dispatching the event on the eventbus. A failed dispatch will result in message loss.

More information about this component can be found in [ADR 0036](0037-eventbus.md).

## Decision

Upon dispatch failure, the eventBus should save events in dead letter using a dedicated group.

Reprocessing this group an admin can re-trigger these events dispatch.

In order to ensure auto healing, James will periodically check the corresponding group in deadLetter is empty. If not a
re-dispatching of these events will be attempted. 

## Consequence

In distributed James Guice project an administrator have a way to be eventually consistent upon rabbitMQ failure.

## References

 - [JIRA](https://issues.apache.org/jira/browse/JAMES-3139)