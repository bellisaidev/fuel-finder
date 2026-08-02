# CI Security Policy

This document defines the security checks used by Fuel Finder's GitHub Actions workflows and separates merge-blocking gates from informational findings.

## Workflow trust boundaries

All workflows deny token permissions by default with `permissions: {}` and grant only the permissions required by each job. Repository checkout does not persist credentials. Pull request workflows use `pull_request`, never `pull_request_target`, and do not receive repository or runtime secrets.

Gradle cache entries are read-only on pull request runs. Trusted pushes to the default branch may update the existing Gradle cache. The container workflow builds and scans a local image only; it does not publish an image or create a deployable artifact.

## Dependency Review

Dependency Review runs on pull requests targeting `master`. It blocks a pull request that introduces a dependency with a known High or Critical vulnerability in a runtime, development, or unknown scope. The check evaluates dependency changes in the pull request rather than treating existing repository findings as newly introduced failures.

## CodeQL

The advanced CodeQL workflow analyzes the current Java-only codebase with build mode `none` and the default query suite. Before enabling or recreating this workflow, verify under **Settings > Code security** or through GitHub's default-setup API that CodeQL default setup is disabled. Default and advanced setup must not run together.

The workflow publishes analysis but does not establish merge protection by itself. A repository administrator must configure a ruleset for `master` that:

1. requires CodeQL code-scanning results;
2. sets the security-alert threshold to **High or higher**; and
3. requires the resulting CodeQL check before merge.

With those settings, new Critical and High CodeQL findings block merging. Medium and Low findings remain informational.

The repository currently contains Java only. If Kotlin is introduced, change CodeQL to `autobuild` or a manual build so Kotlin is compiled and analyzed; build mode `none` is not sufficient for Kotlin.

## Container vulnerability scanning

Trivy scans the locally built final image on every applicable CI pull request and push. It reports High and Critical operating-system and Java-library vulnerabilities in a table in the job log and generates SARIF containing only those severities. Findings are informational and Trivy exits successfully when vulnerabilities are present.

The workflow attempts to upload SARIF for `pull_request` runs targeting `master` and trusted pushes to `master`. GitHub applies the effective `security-events` permission for each event. The table report remains available in the job log even when SARIF cannot be uploaded. Do not introduce an event-specific upload exclusion unless an actual repository run demonstrates that it is required.

## Remediation and exceptions

Critical and High findings receive remediation priority. A future exception must identify a specific CVE, document its justification, and have a time limit. This increment defines no exceptions and includes no Trivy ignore file.

Dependency update automation, dependency locking and verification, Gradle Wrapper checksums, dependency submission, blocking Trivy gates, ignore files, Docker digest pinning, image publishing, and deployment are outside this increment. Dependency update automation is deferred to a separate dependency-governance increment.
