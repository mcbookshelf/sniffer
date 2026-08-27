# 📝 Log

**`/log`**

Prints a message to the chat of every player, mixing plain text with values read from the world.

It is meant to replace the `say`/`tellraw` pair developers usually resort to while debugging: no JSON to write, and values are read inline.

---

## 🔧 Syntax

```{describe} /log <message>

Broadcast a message to every player.

:Arguments:
  **`message`**: Free text, in which any `{ ... }` is evaluated and replaced by its value. See [Expressions](expression).
```

*Example: print a score inside a message:*

```mcfunction
#!log The score of @s in test objective is {(score @s test )}
```

*Prints:*

```
The score of @s in test objective is 10
```
