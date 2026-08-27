# ⏱️ JVM Timer

**`/jvmtimer`**

Measures how long a piece of a datapack takes, from the JVM side.

A timer is a named pair of `start`/`end` calls placed around the code to measure.
Every pass through the pair is recorded, so what you read back is not a single measure but the total, the number of passes, the average, and the extremes.

```{note}
Timers are process wide and live as long as the game does: they are shared by everyone and survive a datapack reload.
Use `reset` before a measurement campaign rather than trusting a fresh id.
```

---

## 🔧 Subcommands

You can find below all the subcommands available.

---

### Start

```{describe} /jvmtimer start <id>

Start the timer, creating it if it does not exist yet.

:Arguments:
  **`id`**: Name of the timer.

:Note:
  Starting a timer twice without ending it in between is a mistake that would corrupt the measure, so the timer disables itself instead and says so in the log.
```

---

### End

```{describe} /jvmtimer end <id>

Stop the timer and record the elapsed time as one more pass.

:Arguments:
  **`id`**: Name of the timer.
```

*Example: measure a function, one pass per call:*

```mcfunction
#!jvmtimer start my_function
function test:expensive
#!jvmtimer end my_function
```

---

### Get

```{describe} /jvmtimer get <id>

Print what the timer recorded: total time, number of passes, average time, and the longest and shortest pass.

:Arguments:
  **`id`**: Name of the timer.
```

*Prints:*

```
Timer id: my_function
Total time: 42.5ms
Count: 100
Average time: 0.425ms
Max/Min time: 981.0μs/112.0μs
```

---

### Reset

```{describe} /jvmtimer reset <id>

Throw away everything the timer recorded, and enable it again if it had disabled itself.

:Arguments:
  **`id`**: Name of the timer.
```

---

### Disable

```{describe} /jvmtimer disable <id>

Clear the timer and make it ignore its `start` and `end` calls.

Use it to silence a timer whose directives are still in the datapack, without editing the functions. `reset` turns it back on.

:Arguments:
  **`id`**: Name of the timer.
```
