# Changelog

## [Unreleased]

### Phase 0 — Setup
- Gradle convention plugins (`build-logic/`), version catalog, 23-module skeleton.
- detekt + ktlint (Spotless) + Kover coverage rules + Android Lint wired into `check`.
- `verifyNoNetworkPermission` fails `check` if any merged manifest asks for network access.
- ADRs 0001–0003, `scripts/ci.sh`, pre-commit hook.
