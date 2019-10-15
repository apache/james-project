# 1. [JAMES-2909] Record architecture decisions

Date: 2019-10-02

## Status

Proposed

## Context

In order to be more community-oriented, we should adopt a process to have a structured way to have open architectural decisions.

Using an Architectural Decision Records-based process as a support of discussion on the developers mailing-lists.

## Decision

We will use Architecture Decision Records, as [described by Michael Nygard](https://web.archive.org/web/20190824074401/http://thinkrelevance.com/blog/2011/11/15/documenting-architecture-decisions).

Each ADR will be discussed on the Apache James' developers mailing-list before being accepted.

Following [Apache Decision Making process](https://community.apache.org/committers/decisionMaking.html), we provide the following possible status, with their associated meaning:
 - `Proposed`: The decision is being discussed on the mailing list.
 - `Accepted (lazy consensus)` : the architecture decision was proposed on the mailing list, and a consensus emerged from people involved in the discussion on the mailing list.
 - `Accepted (voted)` : the architecture undergo a voting process.
 - `Rejected` : Consensus built up against that proposal.


## Consequences

See Michael Nygard's article, linked above. For a lightweight ADR toolset, see Nat Pryce's [adr-tools](https://github.com/npryce/adr-tools).

We should provide in a mutable `References` section links to related JIRA meta-ticket (not necessarily to all related sub-tickets) as well as a link to the mail archive discussion thread.

JIRA tickets implementing that architecture decision should also link the related Architecture Decision Record.

## References

 * [JAMES-2909](https://jira.apache.org/jira/browse/JAMES-2909)
