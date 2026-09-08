# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

A SonarQube plugin (Java, Maven) that enables analysis of Dart and Flutter projects. It does **not** implement its own linter: it shells out to `dart analyze` / `flutter analyze` (or reuses a report file), parses the output, and feeds issues, metrics, tests and coverage into SonarQube.

Requirements: JDK 11+ (CI builds with 17), Maven 3.8+, and a local SonarQube instance for manual testing. Target API is `sonar-plugin-api` 7.9 (`provided` scope) — keep compatibility with SonarQube 7.9+.

## Commands

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

`AnalyzerExecutable.analyze()` has a side effect on the analyzed project: unless `sonar.dart.analyzer.options.override=false`, it renames any existing `analysis_options.yaml` to `analysis_options.yaml.sonar`, writes the plugin's bundled `dart-lang/src/main/resources/dartanalyzer/analysis_options.yaml`, and restores the original in a `finally` block. Changes here must keep that restore path intact.

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

`develop` is the integration branch (every push updates the matching `-SNAPSHOT` GitHub release via `.github/workflows/maven.yml`); stable releases come from merging `develop` into `main` (non-squash) and pushing a `0.*` tag, which triggers `.github/workflows/release.yml`. After a release, bump with `mvn versions:set -DnewVersion=X.Y-SNAPSHOT` on `develop`. The version is duplicated in all three `pom.xml` files — `versions:set` keeps them in sync.
