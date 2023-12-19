# 72.  Postgresql flags update concurrency control mechanism

Date: 2023-12-19

## Status

Not-Implemented

## Context

When IMAP server support THREAD_SAFE_FLAGS_UPDATE capability, it means that the server can update flags concurrently.
We are facing a concurrency issue when multiple queries from clients simultaneously access the `user_flags` column of the `message_mailbox` table in PostgreSQL.
Currently, the James fetches the current data, performs changes, and then updates to database.
However, this approach does not ensure thread safety and may lead to concurrency issues.

## Decision

To address the concurrency issue when clients make changes to the user_flags column,
we decide to use PostgreSQL's built-in functions to perform direct operations on the `user_flags` array column
(without fetching the current data and recalculating on James application).

Specifically, we will use PostgreSQL functions such as 
`array_remove`, `array_cat`, or `array_append` to perform specific operations as requested by the client (e.g., add, remove, replace elements).

Additionally, we will create a custom function, say `remove_elements_from_array`, 
for removing elements from the array since PostgreSQL does not support `array_remove` with an array input.

## Consequences

Pros:
- This solution reduces the complexity of working with the evaluate new user flags on James.
- Eliminates the step of fetching the current data and recalculating the new value of user_flags before updating.
- Ensures thread safety and reduces the risk of concurrency issues.

Cons:
- The performance will depend on the performance of the PostgreSQL functions.

## Alternatives

- Optimistic Concurrency Control (OCC): Using optimistic concurrency control to ensure that only one version of the data is updated at a time. 
However, this may increase the complexity of the code and require careful management of data versions.
The chosen solution using PostgreSQL functions was preferred for its simplicity and direct support for array operations.

## References

- [JIRA](https://issues.apache.org/jira/browse/JAMES-2586)
- [PostgreSQL Array Functions and Operators](https://www.postgresql.org/docs/current/functions-array.html)



