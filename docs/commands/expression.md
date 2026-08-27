# 🧮 Expressions

**`{ ... }`**

A small language for reading values out of the world, shared by [`/log`](log) and [`/assert`](assert).

An expression is written between curly braces.
Inside it, values read from the game are written between parentheses, and everything else is a literal NBT value.

```mcfunction
#!assert {(score @s test) <= 10}
```

```{note}
No operator has precedence over another: an expression is evaluated strictly from left to right.
Nest sub-expressions with `{ }` to force an order.
```

---

## 🔧 Values

You can find below all the values readable from the world.

---

### Score

```{describe} (score <holder> <objective> )

Read a score, as `execute if score` does. Returns an integer.
```

*Example:*

```mcfunction
#!log Score of @s: {(score @s test )}
```

---

### Data

```{describe} (data block <pos> <path> )
```

```{describe} (data entity <target> <path> )
```

```{describe} (data storage <id> <path> )

Read an NBT value, as `execute if data` does. Returns that value.
```

*Example:*

```mcfunction
#!log Held item: {(data entity @s SelectedItem.id )}
```

---

### Name

```{describe} (name <targets> )

Read the display names of the matched entities. Returns a text component.
```

*Example:*

```mcfunction
#!log Nearest player: {(name @p )}
```

---

## 🔧 Operators

You can find below all the operators available.

---

### Arithmetic

```{describe} + - * /

Compute over numbers. The result keeps the widest type of the two operands.

`+` also concatenates: text components, strings, lists, and compound tags (NBT merge).
```

---

### Comparison

```{describe} == != < <= > >=

Compare two values. Returns a boolean.
```

---

### Logical

```{describe} && || !

Combine booleans.

Every operator here is infix, `!` included: it negates its right operand but still needs one on its left, so `{true && !(score @s flag)}` works but `!` cannot start an expression.
```

---

### Type Check

```{describe} <value> is <type>

Check the type of a value. Returns a boolean.

:Types:
  `nbt`, `text`, `string`, `number`, `byte`, `short`, `int`, `long`, `float`, `double`, `byte_array`, `int_array`, `long_array`, `list`, `compound`.
```

*Example:*

```mcfunction
#!assert {(data storage my:store Value ) is int}
```
