## Summary

Describe what this change does and why. Reference any related issues (e.g. `Fixes #123`).

## Checklist

- [ ] Clean-room verified: no Apache Velocity implementation code copied or de-compiled
- [ ] Code formatted via `./gradlew spotlessApply` (or `./mvnw spotless:apply`)
- [ ] Gradle build passes: `./gradlew clean build`
- [ ] Maven build passes: `./mvnw clean verify`
- [ ] Dual-build parity verified: `./scripts/verify-build-parity.sh`
- [ ] Differential TCK passes in strict mode: `./gradlew :viet-template-tck:test`
- [ ] Architecture rules pass (ArchUnit verified)
- [ ] Documentation updated where needed

## Notes

Add migration notes, benchmark evidence, or follow-up work when applicable.
