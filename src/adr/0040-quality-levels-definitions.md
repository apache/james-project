# 40. Quality levels definition

Date: 2020-06-21

## Status

Accepted (lazy consensus)

## Context

We hereby define as an artifact compiled artifact that external people consumes. This includes:

 - libraries
 - Mail servers
 - Extensions for James Mail Servers
 - Command line tools

We designate as a feature an optional, opt-in behaviour of a James server that can be configured by 
user willing to rely on it.

James as a project delivers several artifacts, and features. In order for project users to better
understand the underlying quality of the artifact they use, as well as the level of risk associated,
we need to better define some quality levels.

## Decision

For a given artifact or feature, by **mature** we mean that:

 - *interfaces* in components need a contract test suite
 - *interfaces* have several implementations
 - *implementation* of these interfaces need to pass this contract test suite which provides unit tests
 - Decent integration tests coverage is needed
 - Performance tests need to be conducted out
 - Quality Assurance with external clients needs to be conducted out
 - known existing production deployments/usages
 - usable documentation

This is the maximum quality level delivered by the James project. Users should feel confident using these
artifacts or features.

By **experimental** we designate an artifact or feature not matching yet the above requirements. However some
active contributors are willing to raise the quality level of this component, and eventually make it 
mature. Or at least are willing to support users.

Users should have low expectations regarding experimental artifacts or features. They are encouraged to contribute to them 
in order to raise its quality.

By **unsupported** we mean that an artifact or feature do not match most of the *mature* quality conditions. Active 
contributors do not feel confident delivering support for it. This artifact or feature might be deprecated and 
removed from future James releases. Users are strongly encouraged to contribute to the artifact development.

## Consequences

Quality levels need to be mentioned explicitly in the documentation, per artifact, and per feature.

We need to audit existing artifacts and features to make a list of experimental and unsupported artifacts. We will maintain
JIRA tickets opened from them, detailing for each one of them expected actions to raise the quality level. Maintaining such 
tickets will encourage contributions on experimental and unsupported components.

## References

 - Mailing list discussion: https://www.mail-archive.com/server-dev@james.apache.org/msg66909.html
 - [PR discussing this ADR](https://github.com/apache/james-project/pull/219)