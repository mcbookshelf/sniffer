# 🔁 Watch

**`/watch`**

Watches the files of a datapack and reloads the functions that changed, without a full `/reload`.

Only the changed `.mcfunction` files are re-parsed and spliced into the running game, so nothing else is reloaded: no other resource is touched, and the `#minecraft:load` functions are not run again.
On a large datapack, or alongside a mod like KubeJS, this saves the pause a full reload costs.

Watching is off by default and has to be started again every time you re-enter the world.

```{note}
A function that does not parse is not applied: the error is reported in the chat and the previous version stays in place.
```

---

## 🔧 Subcommands

You can find below all the subcommands available.

---

### Start

```{describe} /watch start <datapack>

Watch a datapack folder for created, modified, and deleted files.

:Arguments:
  **`datapack`**: Name of the datapack folder to watch.
```

*Example:*

```mcfunction
/watch start my_datapack
```

---

### Stop

```{describe} /watch stop <datapack>

Stop watching a datapack folder.

:Arguments:
  **`datapack`**: Name of the watched datapack folder.
```

---

### Reload

```{describe} /watch reload

Apply the changes detected since the last reload.

The chat reports what was actually applied, so a change that did not make it through is visible rather than silently missing.
```

---

### Auto

```{describe} /watch auto [<bool>]

Apply changes as soon as they are detected, instead of waiting for a `reload`.

:Arguments:
  **`bool`**: Whether to reload automatically. Defaults to `false`, and reports the current value when omitted.
```
