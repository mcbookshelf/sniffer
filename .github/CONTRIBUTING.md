# Contributing to Sniffer

Sniffer is two deliverables in one repository: the Fabric mod (`src/`, published to Modrinth) and the VSCode extension (`vscode/`, published to the Marketplace).

## Development setup

Requirements: a JDK (25+) and Node.js for the extension. Gradle comes with
the wrapper.

```sh
./gradlew build          # build the mod jar (build/libs/)
./gradlew runClient      # launch a dev client with the mod loaded
```

```sh
cd vscode
npm install
npm run test              # typecheck
npm run watch             # esbuild in watch mode
```

### Tests

- `./gradlew test` runs the JUnit suite: the expression mini-language only, since everything else touching the debugger needs a running game.
- `./gradlew runGametest` launches a headless game server with the mod loaded and exercises breakpoints, stepping, the DAP transport and `/breakpoint` for real.
  This is the suite that actually covers the mixin layer, since that layer never loads under plain JUnit.
- `./gradlew jacocoTestReport` merges both suites into one coverage report at `build/reports/jacoco/test/html/index.html`.

## Layout

```
src/main     Fabric mod (Kotlin/Java), server + common code
src/client   Client-only hooks
src/gametest Headless game tests (own source set, excluded from the shipped jar)
src/test     JUnit tests (expression mini-language)
vscode/      The VSCode debug adapter extension (TypeScript)
docs/        User documentation
```

The mod has two cooperating layers: 
a mixin interception layer that surgically hooks Minecraft's command execution pipeline to attach source metadata and enforce debug pauses, 
and a dispatch layer that routes entrypoints (the DAP server, in-game commands) to handlers which mutate shared debugger state. 
Entrypoints stay thin, handlers never inspect the command queue, and mixins never call handlers.

## Code style

Kotlin/Java: tabs, same-line braces, no line-length limit, following Fabric conventions. 
TypeScript in `vscode/` follows the existing eslint config (`npm run lint`).

Because Minecraft's command/function internals change every version, expect mixin targets and accessors to need updating on each Minecraft bump.

## Releases and versioning

The mod is versioned by `gradle.properties` (`mod_version` plus the targeted `minecraft_version`). 
Bump `mod_version` in your PR when the mod changes;
the VSCode extension's `package.json` version is synced from it automatically at package time. 
Follow semver: new functionality bumps minor, bugfixes bump patch, breaking changes bump major.

## Pull requests

- CI must be green: it builds the mod, runs the JUnit and game test suites, and typechecks the VSCode extension.
- Keep the two deliverables in sync: a mixin/handler change usually has no effect on the extension, 
- but a DAP protocol change on the mod side likely needs a matching update in `vscode/src`.
