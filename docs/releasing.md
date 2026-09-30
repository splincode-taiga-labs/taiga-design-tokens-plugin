# Releasing

Release automation is tracked by [#53](https://github.com/splincode-taiga-labs/taiga-design-tokens-plugin/issues/53).

The release workflow is intentionally explicit and single-entry:

- pull requests and ordinary pushes never publish;
- a release starts only from **Actions → Release → Run workflow** on `main`;
- the workflow generates GitHub release notes from commits/PRs since the previous tag;
- those notes are inserted into `CHANGELOG.md`;
- the workflow creates a local `chore(release): <version>` commit and verifies that exact commit;
- only after build, tests, Plugin Verifier, signing, and signature verification succeed does the workflow atomically push the release commit and tag;
- the verified signed artifact is published to JetBrains Marketplace;
- after Marketplace publishing succeeds, the workflow creates the GitHub Release and attaches the same signed ZIP.

This keeps `main`, the release tag, `CHANGELOG.md`, the Marketplace version, and the GitHub Release aligned to the same release commit.

## Marketplace prerequisite

JetBrains requires the first plugin publication to be created/uploaded manually before subsequent versions can be published through Gradle.

Before enabling automated publishing:

1. Create the plugin entry in JetBrains Marketplace and complete its required metadata.
2. Upload the first signed distribution manually.
3. Generate a Marketplace publishing token.
4. Add the signing and publishing credentials as GitHub Actions secrets.

## Required GitHub Actions secrets

Configure these repository or protected-environment secrets:

| Secret                 | Purpose                                      |
| ---------------------- | -------------------------------------------- |
| `CERTIFICATE_CHAIN`    | X.509 certificate chain used by `signPlugin` |
| `PRIVATE_KEY`          | PEM private key used to sign the plugin      |
| `PRIVATE_KEY_PASSWORD` | Password for the private key                 |
| `PUBLISH_TOKEN`        | JetBrains Marketplace publishing token       |

The certificate chain and private key must never be committed. The IntelliJ Platform Gradle Plugin accepts the signing values through environment variables; Base64-encoded values are also supported and are convenient for multiline CI secrets.

## Publish a release

Run **Actions → Release → Run workflow** from the `main` branch.

Provide:

- an explicit non-SNAPSHOT version, for example `0.1.4` or `0.2.0-beta.1`;
- the Marketplace channel:
  - `beta` creates a GitHub prerelease;
  - `default` creates a normal GitHub release.

The workflow refuses to continue when:

- it is started from a branch other than `main`;
- the version is invalid or ends in `-SNAPSHOT`;
- the release tag already exists;
- `CHANGELOG.md` already contains the version;
- there are no commits since the previous tag;
- `main` changes while the release candidate is being verified.

If `main` changes during verification, rerun the release workflow so the release is built from the latest `main`.

## Generated changelog

Release notes are generated through GitHub's Release Notes API using the previous reachable tag as the start point.

The workflow inserts them below `## Unreleased` as:

```md
## 0.1.4 - 2026-09-30

### What's Changed

- ...
```

GitHub's generated `Full Changelog` footer is omitted from `CHANGELOG.md` but preserved in the GitHub Release notes.

The changelog update is committed as:

```text
chore(release): 0.1.4
```

The release tag points to this commit.

## Local signed dry-run

Use an explicit release version and keep credentials in environment variables:

```bash
export CERTIFICATE_CHAIN='...'
export PRIVATE_KEY='...'
export PRIVATE_KEY_PASSWORD='...'

./gradlew \
  check \
  buildPlugin \
  verifyPluginStructure \
  verifyPluginProjectConfiguration \
  verifyPlugin \
  signPlugin \
  verifyPluginSignature \
  -PpluginVersion=0.1.4 \
  -PmarketplaceChannel=beta
```

This does not update `CHANGELOG.md`, push commits/tags, create a GitHub Release, or publish anything.

## Local publish

Only run this for recovery/debugging after confirming the exact version and channel:

```bash
export PUBLISH_TOKEN='...'

./gradlew publishPlugin \
  -PpluginVersion=0.1.4 \
  -PmarketplaceChannel=beta
```

`publishPlugin` refuses versions ending in `-SNAPSHOT`.
