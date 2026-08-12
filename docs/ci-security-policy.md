# CI Security Policy

This document defines the security checks used by Fuel Finder's GitHub Actions workflows and separates merge-blocking gates from informational findings.

## Workflow trust boundaries

All workflows deny token permissions by default with `permissions: {}` and grant only the permissions required by each job. Repository checkout does not persist credentials. Pull request workflows use `pull_request`, never `pull_request_target`, and do not receive repository or runtime secrets.

Gradle cache entries are read-only on pull request runs. Trusted pushes to the default branch may update the existing Gradle cache. Pull requests build and scan a local container image without registry authentication or publication. Only the master publishing job receives `packages: write`; it authenticates with `GITHUB_TOKEN` and never passes registry credentials to the Docker build.

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

Trivy scans the final local image on pull requests. On trusted pushes to `master`, the workflow first reconciles the immutable full-commit-SHA tag in GHCR. A missing tag causes the image to be built once and scanned locally before it is pushed. An existing tag is never rebuilt or overwritten; its revision metadata and digest are validated, and Trivy scans the canonical digest-qualified remote image instead.

The table report and SARIF contain High and Critical operating-system and Java-library findings. High findings remain informational. A separate Critical-only Trivy invocation blocks publication or reuse when a Critical vulnerability is present. Docker build failures and Trivy execution failures also block the workflow.

The workflow attempts to upload SARIF for pull requests targeting `master` and trusted pushes to `master`. GitHub applies the effective `security-events` permission for each event. On the publishing path, SARIF upload uses `always()` and is non-blocking: the report remains visible when the Critical gate fails, while an upload service failure cannot by itself block a verified image. The table report remains available in the job log.

## Container publication

The runtime image is published only as `ghcr.io/bellisaidev/fuel-finder:<full-git-sha>`. The workflow creates no mutable tags and uploads no image workflow artifact. New images carry `org.opencontainers.image.source` and `org.opencontainers.image.revision` labels supplied by the build command; the Dockerfile remains environment-agnostic.

SHA tags are treated as write-once. After authenticating, the master job proceeds to a build only when registry inspection returns an explicit not-found result. It confirms the tag is still absent after scanning and immediately before its single push. Any ambiguous registry failure or tag appearing during validation is handled as an error without overwriting the remote artifact. If the tag already exists, its revision label must equal the current full Git SHA and its manifest digest must be valid. The workflow scans that digest, verifies that the tag has not moved, and reports the existing artifact as reused without building or pushing.

After a new push, the same remote revision and digest checks are required. The job summary reports whether the artifact was published or reused, the SHA tag, and the digest-qualified reference. A future staging workflow must consume `ghcr.io/bellisaidev/fuel-finder@sha256:<digest>` so it selects the verified registry manifest; this workflow does not deploy anything.

GHCR does not enforce immutable container tags. Package write access must therefore remain limited to trusted maintainers and the master publishing workflow.

### First-publication package settings

The package remains private while the first published artifact is checked. Verify its full-SHA tag, revision label, registry digest, and digest-qualified reference before deliberately changing the package visibility to **Public**. Then confirm that the package is connected to `bellisaidev/fuel-finder`, repository Actions access is correct, and both the SHA tag and digest can be pulled anonymously.

## Remediation and exceptions

Critical and High findings receive remediation priority. A future exception must identify a specific CVE, document its justification, and have a time limit. This increment defines no exceptions and includes no Trivy ignore file.

Dependency update automation, dependency locking and verification, Gradle Wrapper checksums, dependency submission, Trivy ignore files, Docker base-image digest pinning, mutable or semantic-version image tags, SBOMs, attestations, signing, multi-platform builds, registry retention automation, and deployment are outside this increment. Dependency update automation is deferred to a separate dependency-governance increment.
