# Agent guidance

Before making changes in this repository:

- read [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) before changing subsystem boundaries, data sources, caches, invalidation, threading, or dependencies between packages;
- read [`docs/roadmap.md`](docs/roadmap.md) when implementing planned product or production-hardening work;
- keep `README.md` focused on user-facing product capabilities rather than implementation details;
- preserve existing resolution semantics and public/proprietary icon behavior unless the task explicitly changes product behavior;
- keep expensive scanning, graph construction, PSI extraction, network access, and rendering off the EDT and outside cache locks;
- update `docs/ARCHITECTURE.md` in the same pull request when an architectural boundary or invariant changes;
- run the relevant tests and `./gradlew check buildPlugin` for production changes.

For local sandbox debugging and real-project testing, see [`docs/local-debugging.md`](docs/local-debugging.md).
