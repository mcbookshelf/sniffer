# VS Code

Visual Studio Code (VS Code) is a well-known code editor, made popular for Minecraft datapack creation thanks to Spyglass's Datapack Helper Plus (DHP).
Sniffer does not provide the same features as DHP at all.
DHP focuses on static tooling, i.e. tooling available when the datapack is not running, while Sniffer aims to close the gap on runtime tooling.
Both therefore use the same editor as a frontend, but they are in fact complementary.

This page presents the features Sniffer offers through VS Code.

## Initiate the debugger configuration

When opening a folder that contains a `pack.mcmeta`, VS Code shows a notification proposing to add a Sniffer debug configuration.
This configuration tells VS Code how to connect to your Minecraft instance.

![Sniffer configuration notification in VS Code](/_static/images/vscode-sniffer-init-notif.png)

Clicking *Configure* writes a `.vscode/launch.json` with a `sniffer` entry pointing at `ws://localhost:25599/dap` and opens it for you to adjust.
*Not now* dismisses the prompt, which is shown again the next time the folder is opened; *Don't ask again* silences it for this workspace.

You can also add or reopen this configuration at any time from the command palette, via *Sniffer: Configure Debug Configuration*.

## Anatomy of the launch configuration

A complete Sniffer entry looks like this:

```json
{
  "type": "sniffer",
  "request": "attach",
  "name": "Connect to Minecraft",
  "address": "ws://localhost:25599/dap",
  "user": "Steve",
  "pathMapping": {
    "/remote/path": "${workspaceFolder}"
  }
}
```

`type` and `request` are fixed: Sniffer only attaches to an already running game, it never launches one.

| Field | Required | Description |
| --- | --- | --- |
| `address` | yes | WebSocket address of the debug server, in the form `ws://hostname:port/path`. It must match the port and path configured in the mod (`25599` and `/dap` by default, changeable from Mod Menu or `config/sniffer.json`). |
| `user` | multiplayer only | Minecraft username to attach as. The game prompts that player to accept the session, so they must be online, and must be an operator on a multiplayer server. Can be omitted in singleplayer, where it defaults to the host. |
| `pathMapping` | no | Maps the paths reported by the game to paths on your machine. Only needed when the datapack does not live on the same filesystem as the editor, typically a remote or containerized server. |

## Attach the debugger

Once the configuration is in place, load your world with the Sniffer mod running, then open the *Run and Debug* view, select *Connect to Minecraft* and click the run button next to it.

![Starting the Sniffer debug configuration from the Run and Debug view](/_static/images/vscode-sniffer-run.png)

A dialog is then shown in game to you, or to the player named by `user`, asking to authorize the connection.
The session only starts once it is accepted.

![In game dialog asking to authorize the debug session](/_static/images/minecraft-authorization-dialog.png)

## Debugging

Open a `.mcfunction` file and set a breakpoint on the command where the execution should stop: hover just left of the line number to reveal a faded red dot, then click it.
Clicking the breakpoint again removes it.

![Setting a breakpoint on a line of an mcfunction file](/_static/images/vscode-add-breakpoint.png)

When you then run that function in Minecraft, through a `/function` for instance, the execution halts *before* the marked command runs.

While it is paused, the rest of the server keeps ticking: only the debugged execution is suspended.
A `/function` you type in the meantime gets its own execution and is deliberately not debugged.

![A paused execution, with the debug toolbar, the call stack and the variables](/_static/images/vscode-running.png)

### Debug toolbar

At the top, the debug toolbar drives the paused execution.

| Image | Name | Supported | Description |
| --- | --- | --- | --- |
| ![](/_static/images/vscode-continue-action.png) | Continue | ✓ | Resume the execution and let it go until the next breakpoint, or until the function ends. Breakpoints stay armed, and the execution resumes on the next server tick. |
| ![](/_static/images/vscode-step-over-action.png) | Step Over | ✓ | Execute the current command and stop on the next one, without stopping inside the called function if the command is a function call. |
| ![](/_static/images/vscode-step-into-action.png) | Step Into | ✓ | Execute the current command and, if it is a function call, stop on the first command of the called function. |
| ![](/_static/images/vscode-step-out-action.png) | Step Out | ✓ | Execute the rest of the current function and stop on the next command of its caller. |
| ![](/_static/images/vscode-rerun-action.png) | Restart | ✕ | Stop the current session and run it again from the start. Sniffer attaches to a running game and never replays a function, so there is nothing to restart: the button does nothing. |
| ![](/_static/images/vscode-detach-action.png) | Disconnect | ✓ | Detach the debugger from Minecraft. A paused execution resumes. |

### Run and Debug sidebar

Below the toolbar, the *Variables* section shows the execution context of the selected frame, under a *Function* scope:

- `executor`, the entity the commands run as, expandable into its type, name, UUID, position, rotation, world and NBT.
  It reads `server` when the function is not run as an entity.
- `location`, the position, rotation and world the commands run at.
- `macro`, the arguments the function was called with.
  It only appears on a macro function.

The *Call Stack* section lists the functions currently being executed, innermost first, each on the command it is stopped at.
Selecting a frame moves the editor to that command and updates the variables shown above, without advancing the execution.

The *Breakpoints* section lists every breakpoint of the workspace, and lets you disable or remove them.

### Game log

While the debugger is attached, everything the game writes to its log is mirrored to the *Debug Console*.

That covers the whole server log, not only Sniffer: Minecraft itself, Fabric, and every other mod installed.
What a datapack broadcasts lands there too, since the game logs its broadcasts, so the output of `/log` and of the `#!log` debug command shows up alongside the rest.

Lines keep the format they have in `logs/latest.log`, with their timestamp, thread, level and logger, so the *Filter* box of the console can narrow them down: type `WARN` to keep the warnings, or the name of a mod to keep only what it writes.


### Breakpoint kinds

VS Code offers several kinds of breakpoints, of which Sniffer supports some.
To change the kind of a breakpoint, right-click it, then pick *Edit Breakpoint*.

![The breakpoint kinds offered by VS Code](/_static/images/vscode-breakpoint-kinds.png)

| Name | Supported | Description                                                                                                                                                                                                                                                                                                                                                     |
| --- | --- |-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Standard | ✓ | Pause the execution every time the command is reached.                                                                                                                                                                                                                                                                                                          |
| Expression | ✓ | Pause only when the given expression holds. Here, an expression is a Minecraft command read on its success channel, the same thing `/breakpoint if <command>` takes. It runs with the execution context of the paused command, and its output is silenced. A command that does not parse leaves the breakpoint unverified, with the parse error as its message. |
| Hit Count | ✕ | Pause only once the command has been reached a given number of times. Currently, the count is ignored, so the breakpoint pauses every time, like a standard one.                                                                                                                                                                                                |
| Log Message | ✕ | Never pause, but log a message every time the command is reached. Currently, The message is ignored, and the breakpoint pauses instead of logging. Use the `#!log` debug command for that.                                                                                                                                                                      |
| Wait for Breakpoint | ✓ | Stay inactive until another given breakpoint has been hit.                                                                                                                                                                                                                                                                                                      |
