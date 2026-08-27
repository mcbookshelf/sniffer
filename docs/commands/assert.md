# ✅ Assert

**`/assert`**

Checks that an expression holds, and reports where it did not when it does not.

A failing assertion broadcasts a red message with the expression and the call stack that led to it, and the command itself fails, so `execute if` and `execute store success` see the failure instead of reading it as a pass.

---

## 🔧 Syntax

```{describe} /assert <expression>

Check that an expression evaluates to a true boolean.

:Arguments:
  **`expression`**: The expression to check. See [Expressions](expression).

:Note:
  An expression that does not evaluate to a boolean fails too, and the reported value tells you what it evaluated to instead.
```

*Example: check that a score stayed within its expected range:*

```mcfunction
say 1
#!assert {(score @s test ) <= 10}
say 2
```

*Fails with:*

```
Assertion failed: the result is zero
Expression: {(score @s test ) <= 10}
Stack:
  test:test2
  test:test1
```
