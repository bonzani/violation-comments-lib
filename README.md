# Violation Comments Lib [![Build Status](https://travis-ci.org/tomasbjerre/violation-comments-lib.svg?branch=master)](https://travis-ci.org/tomasbjerre/violation-comments-lib) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/se.bjurr.violations/violation-comments-lib/badge.svg)](https://maven-badges.herokuapp.com/maven-central/se.bjurr.violations/violation-comments-lib) [ ![Bintray](https://api.bintray.com/packages/tomasbjerre/tomasbjerre/se.bjurr.violations%3Aviolation-comments-lib/images/download.svg) ](https://bintray.com/tomasbjerre/tomasbjerre/se.bjurr.violations%3Aviolation-comments-lib/_latestVersion)

This is a library that helps working with comments from static code analysis.

It supports the same formats as [Violations Lib](https://github.com/tomasbjerre/violations-lib).

It is used by these libraries:
 * [Violation Comments to GitHub Lib](https://github.com/tomasbjerre/violation-comments-to-github-lib).
 * [Violation Comments to GitLab Lib](https://github.com/tomasbjerre/violation-comments-to-gitlab-lib).
 * [Violation Comments to Bitbucket Server Lib](https://github.com/tomasbjerre/violation-comments-to-bitbucket-server-lib).

## Template

It uses a template to render each violation comment. There is a default that can be replaced with a custom template. 

The context available when the template is rendered is:

 * `violation` that is an instance of [Violation](https://github.com/tomasbjerre/violations-lib/blob/master/src/main/java/se/bjurr/violations/lib/model/Violation.java).

A template may look like:

```
**Reporter**: {{violation.reporter}}{{#violation.rule}}

**Rule**: {{violation.rule}}{{/violation.rule}}
**Severity**: {{violation.severity}}
**File**: {{violation.file}} L{{violation.startLine}}{{#violation.source}}

**Source**: {{violation.source}}{{/violation.source}}

{{violation.message}}
```
