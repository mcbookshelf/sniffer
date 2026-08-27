<h1 align="center">
  <picture>
    <source media="(prefers-color-scheme: light)" srcset="https://raw.githubusercontent.com/mcbookshelf/sniffer/master/docs/_static/banner-light.png">
    <img src="https://raw.githubusercontent.com/mcbookshelf/sniffer/master/docs/_static/banner-dark.png" alt="Sniffer" width="400">
  </picture>
</h1>

<div align="center">
  <a href="https://github.com/mcbookshelf/sniffer/actions/workflows/test.yml"><img src="https://img.shields.io/github/actions/workflow/status/mcbookshelf/sniffer/test.yml?style=for-the-badge&label=tests&colorA=363a4f&colorB=926bd1&logo=githubactions&logoColor=cad3f5" alt="Tests"></a>
  &nbsp;
  <a href="https://marketplace.visualstudio.com/items?itemName=gunivers.sniffer"><img src="https://img.shields.io/visual-studio-marketplace/v/gunivers.sniffer?style=for-the-badge&label=vs%20code&colorA=363a4f&colorB=0078d7&logo=visualstudiocode&logoColor=cad3f5" alt="VS Code Marketplace"></a>
  &nbsp;
  <a href="https://discord.gg/MkXytNjmBt"><img src="https://img.shields.io/discord/1247513995376726116?style=for-the-badge&color=%237289DA&labelColor=363a4f&logo=discord&logoColor=cad3f5" alt="Discord"></a>
</div>

<br/>

Sniffer turns a running Minecraft game into a debugger for your `.mcfunction` datapacks.
This extension is the VS Code side of it: it attaches to a game running the [Sniffer mod](https://github.com/mcbookshelf/sniffer) over the Debug Adapter Protocol, so breakpoints, stepping and variable inspection work in `.mcfunction` files the way they do in any other language.

![A function paused on a breakpoint](https://raw.githubusercontent.com/mcbookshelf/sniffer/master/docs/_static/images/vscode-running.png)

## Requirements

Minecraft with [Fabric Loader](https://fabricmc.net/use/), the Sniffer mod, [Fabric API](https://modrinth.com/mod/fabric-api) and [Cloth Config](https://modrinth.com/mod/cloth-config).

## Getting started

Open your datapack, the folder containing `pack.mcmeta`, and accept the notification offering to add a debug configuration:

```json
{
  "type": "sniffer",
  "request": "attach",
  "name": "Connect to Minecraft",
  "address": "ws://localhost:25599/dap"
}
```

Load your world, run *Connect to Minecraft* from the *Run and Debug* view, and accept the authorization dialog that pops up in game.
Set a breakpoint in a `.mcfunction` file, call that function with `/function`, and the execution halts before the marked command.

`address` must match the port and path configured in the mod, `25599` and `/dap` by default.
On a multiplayer server, add `"user"` with your Minecraft username: that player is the one prompted to accept the session, and must be an operator.

## Documentation

The [VS Code guide](https://github.com/mcbookshelf/sniffer/blob/master/docs/vscode.md) covers the launch configuration, the debug toolbar, the variables and the supported breakpoint kinds.
The [commands reference](https://github.com/mcbookshelf/sniffer/blob/master/docs/commands/index.md) covers what the mod adds in game.

## License

[MPL-2.0](LICENSE)
