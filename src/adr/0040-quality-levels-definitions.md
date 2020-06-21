# 40. Quality levels definition

Date: 2020-06-21

## Status

Accepted (lazy consensus)

## Context

James as a project delivers several artifacts, and features. In order for project users to better
understand the underlying quality of the artifact they use, as well as the level of risk associated,
we need to better define some quality levels.

## Decision

By **supported** we mean that:

 - *interfaces* in components need a contract test suite
 - *implementation* of these interfaces need to pass this contract test suite which provides unit tests
 - Decent integration tests coverage is needed
 - Performance tests need to be conducted out
 - Quality Assurance with external clients needs to be conducted out
 - known existing production deployments/usages
 - usable documentation

This is the maximum quality level delivered by the James project. Users should feel confident using these
artifacts or features.

By **experimental** we designate a part of the code not matching yet the above requirements. However some
active contributors are willing to raise the quality level of this component, and eventually make it 
supported. Or at least are willing to support users.

Users should have low expectations regarding experimental artifacts. They are encouraged to contribute to this 
component in order to raise its code quality.

By **unsupported** we mean that a component do not match most of the *supported* quality conditions. Active 
contributors do not feel confident delivering support for it. This artifact or feature might be deprecated and 
removed from future James releases. Users are strongly encouraged to contribute to the artifact development.

## Consequences

Quality levels needs to be mentioned explicitly in the documentation, per product, and for each product, per feature.

We need to audit existing artifacts and features to make a list of experimental and unsupported components. We will maintain
JIRA tickets opened from them, detailing for each one of them expected actions to raise the quality level. Maintaining such 
tickets will encourage contributions on experimental and unsupported components.

## References

 - Mailing list discussion: https://www.mail-archive.com/server-dev@james.apache.org/msg66909.html
