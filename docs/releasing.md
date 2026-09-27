# Releasing

Release automation is tracked by [#53](https://github.com/taiga-family-labs/taiga-design-tokens-plugin/issues/53).

The release workflow deliberately separates ordinary CI from Marketplace publishing:

- pull requests and pushes never publish;
- a manually dispatched **Release** workflow builds, verifies, signs, verifies the signature, and uploads artifacts, but does not publish;
- publishing happens only when a GitHub Release is published;
- GitHub prereleases go to the JetBrains Marketplace `beta` channel;
- full GitHub releases go to the Marketplace `default` channel.

## Marketplace prerequisite

JetBrains requires the first plugin publication to be created/uploaded manually before subsequent versions can be published through Gradle.

Before enabling automated publishing:

1. Create the plugin entry in JetBrains Marketplace and complete its required metadata.
2. Upload the first signed distribution manually.
3. Generate a Marketplace publishing token.
4. Add the signing and publishing credentials as GitHub Actions secrets.

## Required GitHub Actions secrets

Configure these repository or protected-environment secrets:

| Secret | Purpose |
| --- | --- |
| `CERTIFICATE_CHAIN` | X.509 certificate chain used by `signPlugin` |
| `PRIVATE_KEY` | PEM private key used to sign the plugin |
| `PRIVATE_KEY_PASSWORD` | Password for the private key |
| `PUBLISH_TOKEN` | JetBrains Marketplace publishing token |

The certificate chain and private key must never be committed. The IntelliJ Platform Gradle Plugin accepts the signing values through environment variables; Base64-encoded values are also supported and are convenient for multiline CI secrets.

## Dry-run a release in CI

Run **Actions → Release → Run workflow**.

Provide:

- an explicit non-SNAPSHOT version, for example `0.1.0-beta.1`;
- the target validation channel, normally `beta` before the production release.

A manual run executes the same release checks and signing steps but intentionally skips `publishPlugin`.

The workflow preserves the generated ZIP files as the `plugin-release-<version>` Actions artifact so the signed distribution can be inspected or installed locally before publishing.

## Publish a release

Create and publish a GitHub Release from the exact commit/tag to ship.

The workflow derives the plugin version from the release tag:

```text
v0.1.0-beta.1 -> 0.1.0-beta.1
0.1.0        -> 0.1.0
```

Channel mapping is fixed:

| GitHub Release | Marketplace channel |
| --- | --- |
| prerelease | `beta` |
| full release | `default` |

The release job runs the regular checks, Plugin Verifier, plugin signing, and signature verification before `publishPlugin`. The same workspace artifact is reused for publishing and is also attached to the GitHub Release.

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
  -PpluginVersion=0.1.0-beta.1 \
  -PmarketplaceChannel=beta
```

This does not publish anything.

## Local publish

Only run this after inspecting the signed artifact and confirming the version/channel:

```bash
export PUBLISH_TOKEN='...'

./gradlew publishPlugin \
  -PpluginVersion=0.1.0-beta.1 \
  -PmarketplaceChannel=beta
```

`publishPlugin` refuses versions ending in `-SNAPSHOT`. The default Marketplace channel in Gradle is `beta`; production publishing must explicitly use `-PmarketplaceChannel=default` or a full GitHub Release.
