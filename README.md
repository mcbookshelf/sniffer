# Sniffer

## Overview

Sniffer is a debug adapter for Minecraft datapacks that allows you to debug your `.mcfunction` files directly from Visual Studio Code. It provides features like breakpoints, step execution, and variable inspection to make datapack development easier and more efficient.

## Features

- Set breakpoints in `.mcfunction` files
- Connect to a running Minecraft instance
- Inspect game state during debugging
- Step through command execution
- Path mapping between Minecraft and local files
- Debug commands started with `#!`
- Assert command
- Log command (Much more powerful than `tellraw` or `say`)
- Hot reload datapack changes

## Requirements

- Minecraft with Fabric Loader
- Visual Studio Code


<!-- ## Installation

### Minecraft Mod Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for your Minecraft version
2. Download the Sniffer mod JAR from the [releases page](https://github.com/mcbookshelf/sniffer/releases)
3. Place the JAR file in your Minecraft `mods` folder
4. Launch Minecraft with Fabric

### VSCode Extension Installation

1. Open Visual Studio Code
2. Go to the Extensions view (Ctrl+Shift+X)
3. Search for "Sniffer"
4. Click Install -->

## Mod Configuration
The mod can be configured through the in-game configuration screen, accessible via Mod Menu.
You can also configure the mod in the `config/sniffer.json` file.
The following options are available:

### Debug Server Settings
- **Server Port**: The port number for the debug server (default: 25599)
- **Server path**: The path to the debug server (default: `/dap`)

## Connecting to Minecraft

1. Open your datapack project in VSCode
2. Create a `.vscode/launch.json` file with the following configuration:

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "sniffer",
      "request": "attach",
      "name": "Connect to Minecraft",
      "address": "ws://localhost:25599/dap"
    }
  ]
}
```

In this case, after the game executes `say 2`, the game will be "frozen" because it meets the breakpoint. 

When the game is "frozen", you can still move around, do whatever you want, just like execute the command `tick freeze`.
So you can check the game state, or do some debugging.

* Step

### Breakpoint

The debugger can be controlled directly from Minecraft using the following commands:

* Continue

When the game is "frozen", you can use the command `/breakpoint move` to unfreeze the game and continue running.

* Get Macro Arguments

### Debug Command Line

Comment started with `#!` are recognized as debug commands. They will be executed as same as the normal commands in the game but without Sniffer, those debug commands will only be considered as comments.

Sniffer adds some useful commands for datapack developer. By using debug command, you can use those commands in your datapack without worrying about breaking your datapack in vanilla Minecraft.

For example, if you want to place a breakpoint in your function:

```mcfunction
say 1
say 2
say 3

#The function will stop at the line with `#!breakpoint`
#!breakpoint
say 4
say 5
```

Also you can execute normal commands in debug command line:

```mcfunction
say 1
say 2
say 3

#!execute if score @s test matches 1..10 run breakpoint
say 4
say 5
```

This breakpoint will only be triggerred when the score of the executor in test object is between 1 and 10.

### Assert

Assert command is used to check if a condition is true. If the condition is false, the mod will print an error message to the game chat.

Syntax: `assert <expr>`

```mcfunction
say 1
say 2
say 3

#!assert {(score @s test ) <= 10}
say 4
say 5
```

This assert command will check if the score of the executor in test object is less than or equal to 10.

#### Expression Argument

In sniffer command, you can use complex expression as command argument. Normally, an expression is surrounded by curly braces `{}`. Within an expression, you may obtain a value from an argument enclosed in parentheses and employ it in the expression’s evaluation.

Supported expression arguments:
- `score <score_holder> <objective>`: The same as `execute if score`. Returns an int value.
- `data entity <entity>/storage <id>/block <pos> path`: The same as `execute if data`. Returns a nbt value.
- `name <entity>`: return a text components with all names of the entities.

> [!WARNING] 
> Owing to inherent limitations in the command-parsing system, the closing parenthesis of an expression argument must be preceded by at least one space

Supported operators:
- `+`, `-`, `*`, `/`, `%`: Arithmetic operators
- `==`, `!=`, `<`, `<=`, `>`, `>=`: Comparison operators
- `&&`, `||`, `!`: Logical operators
- `is`: Check if a value is a certain type. Returns a boolean value. Available types: `nbt`, `text`, `string`, `number`, `byte`, `short`, `int`, `long`, `float`, `double`, `int_array`, `long_array`,` byte_array`, `list`, `compound`, `

> [!Note]
> No operator possesses intrinsic precedence; expressions are evaluated strictly from left to right. By nesting subexpressions, however, you can enforce prioritized evaluation

### Log

Log command is used to print a message to the game chat. The log message can contain expression arguments.

Syntax: `log <log>`

```mcfunction
say 1
say 2
say 3

#!log The score of @s in test objective is {(score @s test )}
say 4
say 5
```

This log command will print `The score of @s in test objective is 10` to the game chat.

### Hot reload

Sniffer can watch the changes of the datapack files (only `*.mcfunction` files currently) and reload them automatically. Hot reloading will not reload all resources, as it will only reload the function that is changed. It will not trigger functions with `#minecraft:load` tag too. In large-scale datapack projects, or if you installed kubejs mod, hot reloading can markedly mitigate unnecessary reload-induced pauses and stuttering.

Hot reloading will watch any changes, create or delete of the datapack files.

Hot reloading is not enabled by default. You must use the `watch start` command to start watching a datapack. Each time you re-enter the world, the watcher must be reestablished. After making any changes in the datapack, you can use the `watch reload` command to trigger hot reloading and apply changes in your datapack. A message will be sent to the game chat to tell you what changes have been actually applied.

If the command parser encounters any error while parsing a function, the changes in the function will not apply, and a message will be sent to the game chat to tell you the error.

Command Syntax:

- `watch start <Datapack Folder Name>`: Start watching a datapack folder.
- `watch stop <Datapack Folder Name>`: Stop watching a datapack folder.
- `watch reload`: Trigger the hot loading to apply changes.
- `watch auto [true|false]`: Set whether auto reloading is enabled. Auto reloading will apply any change once the watcher detects it. Default is `false`. If value is not provided, it will tell you if the auto reloading is enabled.

say start
#breakpoint
$say $(msg)
say end
```

After executing `function test:test_macro {"msg":"test"}`, we passed the value `test` to the macro argument `msg` and 
then the game will pause before `$say $(msg)`. At this time, you can use `/breakpoint get msg` to get the value `test`.

* Get Function Stack

By using `/breakpoint stack`, you can get the function stack of the current game. For example, if we have following two
functions:

```mcfunction
#test:test1

say 1
function test:test2
say 2

#test: test2
say A
#breakpoint
say B
```

When the game pauses at the breakpoint, you can use `/breakpoint stack` and the function stack will be printed in the
chat screen:

```
test:test2
test:test

```

* Run command in current context

- [Fabric](https://fabricmc.net/) - Mod loader for Minecraft
- [VSCode Debug Adapter](https://code.visualstudio.com/api/extension-guides/debugger-extension) - VSCode debugging API
- [Datapack Debugger](https://github.com/Alumopper/Datapack-Debugger/) by [Alumopper](https://github.com/Alumopper) - Original implementation of the debugger, without the DAP layer
