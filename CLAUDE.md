# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

A SonarQube plugin (Java, Maven) that enables analysis of Dart and Flutter projects. It does **not** implement its own linter: it shells out to `dart analyze` / `flutter analyze` (or reuses a report file), parses the output, and feeds issues, metrics, tests and coverage into SonarQube.

This repository is an independent copy of [insideapp-fr/sonar-flutter](https://github.com/insideapp-fr/sonar-flutter), LGPL-3.0-or-later. See `NOTICE.md` before touching license headers or third-party files.

Target API is `org.sonarsource.api.plugin:sonar-plugin-api` **13.8.0.4399** (`provided` scope) — note the groupId, `org.sonarsource.sonarqube:sonar-plugin-api` was superseded in 9.5. That is what **SonarQube Community Build 26.8** ships; the plugin does not run on anything older than 26.x. `pluginApiMinVersion` is deliberately left at 13.8 so one jar serves 26.8 and 26.9 (26.9 ships API 14.0, and every API the plugin uses survived that major bump).

Requirements: JDK 17+ to build (the plugin compiles to Java 17 bytecode; CB 26.8+ itself needs Java 21 on the server), Maven 3.8+, and a SonarQube instance for manual testing.

## Commands

The dockerized toolchain needs nothing installed on the host and matches the Java 21 runtime CB 26.x uses:

```bash
docker compose run --rm build              # full build + tests
docker compose run --rm build mvn test     # tests only
docker compose run --rm build mvn license:check
docker compose run --rm build bash         # interactive shell
```

Equivalent Maven invocations, if you have a local JDK 17+:

```bash
mvn package                      # build the plugin jar (sonar-flutter-plugin/target/*.jar)
mvn test                         # run all tests (JUnit 4 + AssertJ + Mockito, JaCoCo attached to test phase)
mvn test -pl dart-lang           # test a single module (also: -pl sonar-flutter-plugin)
mvn test -Dtest=AnalyzerExecutableTest              # single test class
mvn test -Dtest=AnalyzerExecutableTest#methodName   # single test method
mvn license:check                # CI gate — fails on missing LGPL headers
mvn license:format               # add missing headers to .java / .groovy
SONARQUBE_HOME=~/path/to/sonarqube mvn install      # build + deploy jar into the local SonarQube and restart it
```

Test output is redirected to files (`maven.test.redirectTestOutputToFile=true`), so check `target/surefire-reports/` when a test fails.

Tests drive scanner internals (`SensorContextTester`, `TestInputFileBuilder`, `DefaultInputFile`, `DefaultIssueLocation`) that come from `org.sonarsource.sonarqube:sonar-plugin-api-impl`, **pinned at 26.6.0.123539** because 26.7.0 dropped them with no published replacement. Test scope only — it never reaches the jar. That pin is also why `commons-lang3` must stay at 3.18.0+ (`SensorContextTester` needs `lang3.Strings`).

`mvn install` runs `sonar-flutter-plugin/ant.xml`, which copies the jar to `$SONARQUBE_HOME/extensions/plugins` and restarts SonarQube via `bin/macosx-universal-64/sonar.sh` — that path is macOS-only and will fail elsewhere; use `mvn package` on Windows/Linux and copy the jar manually.

## Module layout

- **`dart-lang`** — all Dart language logic: ANTLR-based parsing, metrics, highlighting, the dartanalyzer rule repository and report parsers. Has no SonarQube packaging of its own.
- **`sonar-flutter-plugin`** — `sonar-plugin` packaging. Depends on `dart-lang`, adds Flutter test/coverage sensors, and declares the plugin entry point `fr.insideapp.sonarqube.flutter.FlutterPlugin`.

Everything user-facing is wired in `FlutterPlugin.define()`: extensions (language, sensors, rules definition, quality profile) plus every `PropertyDefinition` shown in the SonarQube UI. **A new configuration option requires three coordinated edits**: the constant + read site on the sensor, a `PropertyDefinition` in `FlutterPlugin`, and a row in the README options table.

## Analysis pipeline

Four sensors, all registered in `FlutterPlugin`:

1. **`DartSensor`** (`dart-lang`) — parses each `.dart` file with the ANTLR grammar and runs visitors over the parse tree. `CustomTreeVisitor` composes `HighlighterVisitor` (syntax highlighting), `SourceLinesVisitor` (ncloc/comments) and `CyclomaticComplexityVisitor`. Test files get highlighting only.
2. **`PubSpecSensor` / `PubSpecParser`** — reads `pubspec.yaml`; `PubSpec.isFlutter()` drives analyzer auto-detection.
3. **`DartAnalyzerSensor`** — the issues path (see below).
4. **`FlutterTestSensor`** + **`FlutterCoverageSensor`** (`sonar-flutter-plugin`) — parse the `flutter test --machine` JSON report and the LCOV file. `FileLocator`/`ReversePathTree` map LCOV paths back to `InputFile`s.

### The analyzer strategy (`issues/dartanalyzer/`)

Two independent axes, both defaulting to `DETECT`:

- **Which executable** (`sonar.dart.analyzer.mode`) → `AnalyzerExecutable.create()` picks a subclass: `DartAnalyzerExecutable`, `FlutterAnalyzerExecutable`, `DartanalyzerAnalyzerExecutable` (legacy SDKs), or `ManualAnalyzerExecutable` (reads an existing report from `sonar.dart.analyzer.report.path`). `DETECT` resolves via `PubSpec.isFlutter()`.
- **Which output format** (`sonar.dart.analyzer.report.mode`) → `MACHINE` (Dart 2.12+) or `LEGACY` human-readable. `DETECT` probes the Dart SDK version on `$PATH` with semver4j.

`DartAnalyzerSensor.execute()` then chooses the parser: `FlutterAnalyzerReportParser` whenever the executable was Flutter, otherwise `DartAnalyzerMachineReportParser` or `DartAnalyzerLegacyReportParser`. Issues are matched to `InputFile`s by absolute path; files outside `sonar.sources` are logged and skipped.

Issue locations are built through `NewIssue.newLocation()`, which is why `DartAnalyzerReportIssue.toNewIssueLocationFor()` takes the owning `NewIssue`. Do not reach for the internal `DefaultIssueLocation` — it is not in the plugin API and was dropped from `sonar-plugin-api-impl` in 26.7.0.

`recordIssues()` drops two classes of issue rather than recording them, and both guards matter:

- **Issues on `analysis_options.yaml` when the plugin substituted it.** The analyzer saw the bundled 298-line copy while SonarQube indexed the project's own file, so the line numbers refer to different content. `AnalyzerOutput.isAnalysisOptionsOverridden()` carries that flag through.
- **Any issue whose line falls outside the indexed file.** One such issue used to throw out of `selectLine()` and abort the whole sensor, discarding every issue in the run.

### The analysis_options.yaml swap

`AnalyzerExecutable.analyze()` has a side effect on the analyzed project: unless `sonar.dart.analyzer.options.override=false`, it moves any existing `analysis_options.yaml` to `analysis_options.yaml.sonar`, writes the plugin's bundled `dart-lang/src/main/resources/dartanalyzer/analysis_options.yaml`, and restores the original in a `finally` block.

**Use `Files.move` with `REPLACE_EXISTING` here, never `File.renameTo`.** On Windows `renameTo` refuses to overwrite an existing destination and only returns `false`; the restore silently failed, fell through to deleting the file, and left the user's only copy in an orphan `.sonar` backup. Linux and macOS were unaffected, so this cannot be caught on CI — `AnalyzerExecutableTest` encodes the contract but a real check means a scan on Windows.

## Rules

`dart-lang/src/main/resources/dartanalyzer/rules.json` (~800 KB) is the single source of truth. `DartAnalyzerRulesDefinition` builds the `dartanalyzer` repository from it and `DartProfile` builds the default quality profile from the same file — a rule missing `name`, `severity`, `type` or `description` is skipped with a warning in both. Rule keys are lowercased when issues are recorded.

Do not hand-edit `rules.json` for linter updates. Regenerate it by scraping dart.dev:

```bash
mvn groovy:execute -Dsource=scripts/updateDartAnalyzerRules.groovy
```

The script (with helpers in `scripts/commons/`) fetches rules and descriptions, then prompts on the console for the severity/type/remediation-effort that cannot be derived. It processes at most `scripts.max-manual` rules per run (20, set in the root `pom.xml`) — rerun until no rules remain unfilled.

## ANTLR grammar

`dart-lang/src/main/antlr/Dart2.g4` is the grammar, but **there is no ANTLR Maven plugin** — the parser in `.../lang/antlr/generated/` is generated out-of-band and committed, with LGPL headers applied. Editing the grammar means regenerating those sources with an ANTLR 4.8 tool, re-running `mvn license:format`, and committing the result. The `generated` package is excluded from JaCoCo and from SonarCloud analysis.

## Branching and releases

`master` is the working branch and is protected: changes land through a pull request (1 approving review required), so direct pushes are rejected. `develop` still exists but is stale — it sits at the upstream state and has none of the 26.x work; the `-SNAPSHOT` automation in `.github/workflows/maven.yml` points at it.

**GitHub Actions is currently disabled on this repository**, so no workflow has run since the fork: neither `maven.yml` nor `release.yml` (both updated to Java 21) has been exercised, and pushing a `0.*` tag will not build anything. Releases have been cut by hand — build in the container, `gh release create <tag>` with the jar attached.

The version is duplicated across all three `pom.xml` files; `mvn versions:set -DnewVersion=X.Y.Z` keeps them in sync (pass `-DgenerateBackupPoms=false`). `master` currently sits on the released `0.6.0` rather than a snapshot, so builds off `master` produce a jar whose manifest claims to be the release — bump to `-SNAPSHOT` if that matters to you.

## Verifying against a real server

Unit tests do not catch the things that actually broke here — the crash that discarded 6609 issues and the destroyed `analysis_options.yaml` both went through a green test suite. For anything touching the analyzer, the file swap, or issue recording, run a real scan:

1. `docker compose run --rm build` and copy `sonar-flutter-plugin/target/*.jar` into a CB 26.x instance's `extensions/plugins/`, then restart it.
2. Check `web.log` for `Deploy Flutter / <version>` and zero `ERROR` lines.
3. Scan a real Flutter project and read the scanner log: `Analyzer produced N chars of output (M line(s))` tells you whether the capture or the parser is at fault when `Recording 0 issues` appears.
4. Confirm the project's `analysis_options.yaml` survived and no `.sonar` orphan is left behind.

A known-unexplained failure mode: one run logged `Recording 0 issues` while the analyzer had produced ~1.2 MB across 6528 lines. The parser was cleared by testing it against that exact output; the run never reproduced and the cause is still unknown. The output-size logging exists to diagnose a recurrence.
