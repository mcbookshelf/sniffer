# Sniffer

## Overview

Sniffer is a debug adapter for Minecraft datapacks that allows you to debug your `.mcfunction` files directly from Visual Studio Code. It provides features like breakpoints, step execution, and variable inspection to make datapack development easier and more efficient.

## Features

- Set breakpoints in `.mcfunction` files
- Connect to a running Minecraft instance
- Inspect game state during debugging
- Step through command execution
- Path mapping between Minecraft and local files

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

3. Start Minecraft with the Sniffer mod installed
4. In VSCode, press F5 or click the "Run and Debug" button
5. Select "Connect to Minecraft" from the dropdown menu

You can now place breakpoints in your `.mcfunction` files and execute it from the game to step through the code.

## Usage in Minecraft

The debugger can be controlled directly from Minecraft using the following commands:

- `/breakpoint continue`: Resume execution after hitting a breakpoint
- `/breakpoint step`: Execute the next command and pause
- `/breakpoint step_over`: Skip to the next command in the current function
- `/breakpoint step_out`: Continue execution until the current function returns

All commands require operator permissions (level 2) to use.

When execution is paused at a breakpoint, the gametick will be freezed.



## Development

### Project Structure

- `src/main`: Main mod code for Minecraft
- `src/client`: Client-side mod code
- `vscode`: VSCode extension source code

### Building the Project

To build the Minecraft mod:

```bash
./gradlew build
```

To build the VSCode extension:

```bash
cd vscode
npm install
npm run build
```

## License

This project is licensed under the MPL-2.0 License - see the [LICENSE](LICENSE) file for details.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Acknowledgements

- [Fabric](https://fabricmc.net/) - Mod loader for Minecraft
- [VSCode Debug Adapter](https://code.visualstudio.com/api/extension-guides/debugger-extension) - VSCode debugging API
- [Datapack Debugger](https://github.com/Alumopper/Datapack-Debugger/) by [Alumopper](https://github.com/Alumopper) - Original implementation of the debugger, without the DAP layer
