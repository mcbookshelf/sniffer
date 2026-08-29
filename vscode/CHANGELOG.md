# Changelog

## 1.0.0

First public release.

- Attach to a running Minecraft game over the Debug Adapter Protocol.
- Breakpoints in `.mcfunction` files, including conditional ones.
- Step over, step into, step out and continue.
- Call stack, and inspection of the executor, the location and the macro arguments of the paused execution.
- Notification offering to generate the `.vscode/launch.json` when a datapack is opened.
- Path mapping for datapacks that do not live on the machine running the editor.
- Trace a command and see the call graph of its execution, drawn as it runs, from *Sniffer: Trace a command*.
The panel also opens on its own for a trace started in game with `/trace run`.
