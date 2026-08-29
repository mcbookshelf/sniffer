# 🕸️ Trace

**`/trace`**

Records the call graph of an execution and streams it to the attached editor as it is walked.

Where a breakpoint answers *what is the state here*, a trace answers *how did we get here*: 
which functions were entered, from which line, and how often.

The graph only exists in the editor, so an editor has to be attached.
See [the VS Code page](../vscode.md#trace) for what it looks like there.

---

## 🔧 Subcommands

You can find below all the subcommands available.

---

### Run

```{describe} /trace run <command>

Run `command` and record every function it enters.

:Arguments:
  **`command`**: The command to trace, written exactly as you would type it.
```

The command is an ordinary one, run with your own execution context, and it keeps its own result and error handling.
Only what it does is watched.

*Example: trace a function directly:*

```mcfunction
/trace run function my_pack:main
```

*Example: trace what a function does for every player:*

```mcfunction
/trace run execute as @a run function my_pack:per_player
```

---

## ⚠️ Limits

**One at a time.**
A second trace is refused while one is running, whether it was started from the game or from the editor.

**An editor has to be attached.**
There is nowhere else for the graph to go, so the command is refused when nothing is connected.

**The command has to be able to call a function.**
`/trace run say hi` is refused: it can enter no function, so there would be no graph.
The check looks for a `function` anywhere in the command, so `/execute` and `/return run` reach it fine, but a command that calls a function indirectly, such as `/reload` running `#minecraft:load`, is refused.
Wrap it in `/execute ... run function ...` when you need that.
