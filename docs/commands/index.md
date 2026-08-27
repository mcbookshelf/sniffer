---
hide-sidebar-secondary: true
---

# ⌨️ Commands

Sniffer adds a handful of commands that drive the debugger from inside the game.
They are the second entrypoint of the mod: everything they do can also be done from a DAP client such as the VSCode extension, and both go through the same dispatcher, so the two stay in sync.

All of them require permission level 2 (gamemaster), like `/gamerule` or `/give`.

---

## 📖 Key Definitions

Debug directive
: A comment starting with `#!` inside a `.mcfunction` file.
  Sniffer rewrites those lines into real commands when the function is loaded, so the exact same datapack stays valid on a vanilla server, where they remain plain comments.

  ```mcfunction
  say 1
  #!breakpoint
  say 2
  ```

Halt
: The state the debugger enters when a breakpoint is hit.
  The commands left to run are parked aside, so the server keeps ticking and players keep playing, while that execution stays frozen exactly where it stopped.
  It resumes from that very point, and a function called in the meantime gets its own execution, which is not debugged.

Frame
: One function call. Calling a function from a function pushes a frame, returning pops it.
  The call stack is the list of frames, innermost first, and `/breakpoint stack` prints it.

Depth
: The number of frames currently on the stack.
  It is what distinguishes the three stepping commands: stepping *in* stops at any depth, stepping *over* refuses to stop deeper than the current frame, and stepping *out* only stops once the current frame has been left.

Expression
: A `{ ... }` value read from the world, used by [`/log`](log) and [`/assert`](assert).
  See [Expressions](expression).

---

```{toctree}
:hidden:
:caption: Debugging

breakpoint
debugmode
```

```{toctree}
:hidden:
:caption: Development

log
assert
jvmtimer
watch
expression
```
