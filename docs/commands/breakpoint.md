# 🔴 Breakpoint

**`/breakpoint`**

Halts the execution of the running function and drives it from there.
Used on its own it is the breakpoint itself, and its subcommands are the debugger controls: stepping, resuming, and reading the state of the halted execution.

While the game is halted, the world stops ticking but players keep moving, so you can look around, check blocks, and read scores before letting the function continue.

---

## 🔧 Subcommands

You can find below all the subcommands available.

---

### Trigger

```{describe} /breakpoint [if <condition>]

Halt the execution at this line, and tell every player about it.

:Arguments:
  **`condition`**: A command whose success decides whether to halt. Always halts when `if` is omitted.

:Note:
  Where the halt happens depends on where the command comes from. See below.
```

Written as a debug directive, it is the datapack equivalent of a breakpoint set from the editor: the halt happens at that exact line, every time the function reaches it.

Typed in the chat, there is no line to halt at, so it arms the debugger instead: the next function line to run, whichever function that turns out to be, halts.
It is the way to catch the next thing the game does without knowing beforehand what that will be. Use `/breakpoint clear` to disarm it.

*Example: halt in the middle of a function:*

```mcfunction
say 1
#!breakpoint
say 2
```

*Example: halt only for a player whose score is within a range:*

```mcfunction
#!breakpoint if execute if score @s test matches 1..10
```

After `if`, you write an ordinary command, read on the success channel exactly like a breakpoint condition set from the editor: the halt happens only when it succeeds.
A condition that simply fails says nothing, so a breakpoint left in a function running every tick stays quiet.

---

### Step

::::{tab-set}
:::{tab-item} Step In

```{describe} /breakpoint step [<lines>]

Run the next line, and halt again at any depth: a line calling a function stops on the first line of that function.

:Arguments:
  **`lines`**: Number of lines to run before halting again. Defaults to `1`.
```
:::
:::{tab-item} Step Over

```{describe} /breakpoint step_over [<lines>]

Run the next line, and halt again in the current function or above it: a line calling a function runs that whole function before halting.

:Arguments:
  **`lines`**: Number of lines to run before halting again. Defaults to `1`.
```
:::
:::{tab-item} Step Out

```{describe} /breakpoint step_out

Run the rest of the current function, and halt on the line following the call that led to it.
```
:::
::::

*Example: run three lines of the current function, whatever they call:*

```mcfunction
/breakpoint step_over 3
```

---

### Continue

```{describe} /breakpoint continue

Resume the execution and let it run until the next breakpoint.
```

---

### Clear

```{describe} /breakpoint clear

Forget the pending step, so the execution is no longer halted line by line.

The breakpoints themselves are untouched: this cancels the stepping in progress, not the places you asked to stop at.
```

---

### Get

```{describe} /breakpoint get [<key>]

Print the macro arguments of the halted function, or a single one of them.

:Arguments:
  **`key`**: Name of the macro argument to read. Prints all of them when omitted.

:Note:
  Only functions called with macro arguments have any: the command reports a failure on a function that has none.
```

*Example: read the `msg` argument the halted function was called with:*

```mcfunction
# In test:test_macro
say start
#!breakpoint
$say $(msg)

# Then, once halted after `function test:test_macro {"msg":"test"}`
/breakpoint get msg
```

---

### Stack

```{describe} /breakpoint stack

Print the call stack of the halted execution, innermost function first.
```

*Example: halting in `test:test2`, itself called by `test:test1`, prints:*

```
test:test2
test:test1
```

---

### Run

```{describe} /breakpoint run <command>

Run a command as the halted function would have run it, with its executor, position and rotation.

This is what makes `@s`, `~ ~ ~` and the like mean the same thing they mean inside the function, which a command typed in the chat cannot do.
```

*Example: check what `@s` resolves to inside the halted function:*

```mcfunction
/breakpoint run say I am the executor
```
