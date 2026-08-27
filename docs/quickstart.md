# 🚀 Quickstart

From nothing to a first paused function, in five steps.
Every step is detailed in the [VS Code](vscode) page.

## 1. Install

Sniffer is two pieces working together: the Fabric mod, running inside Minecraft, and the VS Code extension, driving it.

* **Mod**: install [Fabric Loader](https://fabricmc.net/use/) for your Minecraft version, then drop the Sniffer jar into your `mods` folder alongside [Fabric API](https://modrinth.com/mod/fabric-api) and [Cloth Config](https://modrinth.com/mod/cloth-config) (Kotlin support is bundled in the jar, no separate download needed).
* **Extension**: install *Sniffer* (publisher `Gunivers`) from the [VS Code Marketplace](https://marketplace.visualstudio.com/vscode).

## 2. Configure

Open your datapack folder, the one containing `pack.mcmeta`, in VS Code and accept the notification offering to add a debug configuration.
It writes this `.vscode/launch.json`:

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

On a multiplayer server, add `"user"` with your Minecraft username.

## 3. Attach

Load your world, then run *Connect to Minecraft* from the *Run and Debug* view and accept the authorization dialog that pops up in game.

## 4. Set a breakpoint

Open a `.mcfunction` file and click just left of a line number.
You can also put a [`#!breakpoint`](commands/breakpoint) directly in the function.

## 5. Run the function

Call your function in game, with `/function` for instance.
The execution halts before the marked command, and VS Code shows the call stack and the variables in scope.
Drive it from there with *Continue*, *Step Over*, *Step Into* and *Step Out*.

---

Next: the [VS Code](vscode) page for the full debugging workflow, and the [commands reference](commands/index) for everything Sniffer adds in game.
