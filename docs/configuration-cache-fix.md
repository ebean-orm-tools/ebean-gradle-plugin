# Configuration-cache fix

## Summary

This change makes the Ebean Gradle plugin compatible with Gradle's configuration cache and preserves enhancement for packaged outputs such as `jar` and `testFixturesJar`.

## Root cause

The original implementation attached Groovy closures directly to existing tasks:

- `task.doLast { ... enhanceTaskOutputs(project, ...) }`
- `testTask.doFirst { ... createClassPath(project, ...) }`

Those closures captured `Project` and other mutable Gradle model objects at execution time. With configuration cache enabled, Gradle attempted to serialize those task actions and failed because they retained `DefaultProject` references.

## What changed

### Dedicated enhancement task

Enhancement now runs in a dedicated `EbeanEnhanceTask` instead of ad-hoc `doFirst`/`doLast` closures.

The task declares:

- enhancement classpath as `@Classpath`
- compiled class directories as `@InputFiles`
- debug level as `@Input`

This gives Gradle a configuration-cache-safe model of the work to run.

### Task graph wiring

Instead of enhancing classes as a late finalizer, the plugin now registers explicit enhancement tasks for:

- `main`
- `test`
- `testFixtures` (when present)

and wires consumers to depend on enhancement before they package or execute those classes.

That is important for `testFixturesJar`, because packaging before enhancement leaves the jar with stale, unenhanced bytecode.

### Enhanced outputs for downstream project consumers

A second issue remained after the initial configuration-cache fix: enhancement still rewrote raw compiler output directories in place.

That allowed downstream project dependencies to snapshot or package those raw class directories while enhancement was mutating them, which could surface as intermittent `No such file or directory` failures in Gradle/Kotlin transforms.

The plugin now:

- merges raw compiler outputs into a dedicated enhanced output directory per source set
- exposes that enhanced directory as the source set's consumable classes output
- keeps raw compile directories internal
- excludes raw compile directories from jar packaging

This removes the producer/consumer race by ensuring downstream consumers and packaging tasks only read stable enhanced outputs.

### Publishing metadata modernization

The build keeps Gradle Plugin Portal publishing support, but updates it to the modern `com.gradle.plugin-publish` 2.x configuration style:

- `pluginBundle` metadata moved into `gradlePlugin`
- plugin portal compatibility metadata declares `configurationCache = true`

This keeps publishing support in place while matching current portal guidance.

## Validation

The patch was validated in a consumer build with Gradle 9.4.1 by checking that:

1. `test --configuration-cache` stores the configuration cache successfully
2. a second `test --configuration-cache` run reuses the cache
3. `testFixturesJar` contains an enhanced entity class, not stale pre-enhancement bytecode
4. downstream project dependencies resolve enhanced producer classes instead of raw compile outputs

## Notes

- The local consumer validation that motivated this change used Ebean `17.4.0`, so the branch aligns the embedded `ebean-agent` dependency to `17.4.0` as well.
- If maintainers prefer to manage the published plugin version separately, the structural fix in `EnhancePlugin`/`EbeanEnhanceTask` can be applied independently from release-version decisions.