# Getting Started

The full contributor guide, build/test commands, code layout, and release process, lives in [CONTRIBUTING.md](https://github.com/mcbookshelf/sniffer/blob/master/.github/CONTRIBUTING.md) at the repository root.

In short: a JDK (25+) builds and runs the mod (`./gradlew build`, `./gradlew runClient`), and Node.js builds the VS Code extension (`cd vscode && npm install`). `./gradlew test` runs the JUnit suite, `./gradlew runGametest` runs the headless game tests that cover the mixin layer.
