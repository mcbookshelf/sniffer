---
hide-sidebar-secondary: true
content-width: 58rem
---

```{image} _static/logo-sniffer.png
:height: 8.5rem
:align: right
```

# Sniffer

<p style="max-width: 38rem">
Sniffer is a Fabric mod that turns a running Minecraft game into a debugger for
<code>.mcfunction</code> datapacks: breakpoints, stepping, variable inspection and hot reload,
driven from Visual Studio Code.
</p>

::::{grid}
:gutter: 3

:::{grid-item}
:columns: auto

```{button-ref} quickstart
:color: primary
:class: sd-rounded-pill
:shadow:

🚀 Get Started
```
:::
:::{grid-item}
:columns: auto

```{button-link} https://github.com/mcbookshelf/sniffer
:color: secondary
:outline:

{octicon}`mark-github` View on GitHub
```
:::
::::

---

## 🤔 What is a debugger

Minecraft datapacks are getting more and more complex, and it's often hard to pinpoint why one doesn't work.
The usual approach is to add `/say` or `/tellraw` commands everywhere to see which part of the code runs and what
the current game state is (scoreboards, entities, data, …). This is time-consuming and creates a lot of friction and frustration.

Software engineering solved this problem decades ago with a dedicated tool: the debugger.
Instead of relying on log commands that often require several reruns to capture the right information,
a debugger lets you pause the execution flow and step through it to understand and analyze the current behavior.
It also exposes contextual data at the current execution point, and allows dynamic command execution while paused to query or mutate state.

Sniffer brings exactly this experience to `.mcfunction` datapacks, integrated with VS Code. 
Head over to the [quickstart](quickstart) guide to try it out.

## 🤝 Contribution

Have questions or want to talk about the project? Join the [Discord](https://discord.gg/MkXytNjmBt) server.

```{toctree}
:hidden:
:maxdepth: 1

quickstart
vscode
commands/index
changelog/index
contribute/index
```
