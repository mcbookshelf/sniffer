import * as vscode from 'vscode';

const DISMISS_KEY = 'sniffer.setupDismissed';

/** A datapack is any folder containing a pack.mcmeta. */
const DATAPACK_MARKER = '**/pack.mcmeta';

const DEFAULT_CONFIG = {
    type: 'sniffer',
    request: 'attach',
    name: 'Connect to Minecraft',
    address: 'ws://localhost:25599/dap'
};

/** Offers, once per workspace, to write a Sniffer launch configuration if the workspace holds a datapack. */
export async function offerSetup(context: vscode.ExtensionContext): Promise<void> {
    if (context.workspaceState.get(DISMISS_KEY)) {
        return;
    }
    const folder = await findDatapackFolder();
    if (!folder || hasSnifferConfig(folder)) {
        return;
    }
    const choice = await vscode.window.showInformationMessage(
        'Datapack detected. Add a Sniffer debug configuration?',
        'Configure', 'Not now', 'Don\'t ask again'
    );
    if (choice === 'Configure') {
        await configureLaunch(folder);
    } else if (choice === 'Don\'t ask again') {
        await context.workspaceState.update(DISMISS_KEY, true);
    }
}

/** Adds the Sniffer configuration to the folder's launch.json, then opens it. */
export async function configureLaunch(folder?: vscode.WorkspaceFolder): Promise<void> {
    const target = folder ?? await findDatapackFolder() ?? vscode.workspace.workspaceFolders?.[0];
    if (!target) {
        vscode.window.showErrorMessage('Open a folder before configuring Sniffer.');
        return;
    }
    if (!hasSnifferConfig(target)) {
        const launch = vscode.workspace.getConfiguration('launch', target.uri);
        const configurations = launch.get<unknown[]>('configurations') ?? [];
        await launch.update(
            'configurations',
            [...configurations, DEFAULT_CONFIG],
            vscode.ConfigurationTarget.WorkspaceFolder
        );
    }
    const launchJson = vscode.Uri.joinPath(target.uri, '.vscode', 'launch.json');
    const document = await vscode.workspace.openTextDocument(launchJson);
    await vscode.window.showTextDocument(document);
}

async function findDatapackFolder(): Promise<vscode.WorkspaceFolder | undefined> {
    const [marker] = await vscode.workspace.findFiles(DATAPACK_MARKER, undefined, 1);
    return marker && vscode.workspace.getWorkspaceFolder(marker);
}

function hasSnifferConfig(folder: vscode.WorkspaceFolder): boolean {
    const configurations = vscode.workspace
        .getConfiguration('launch', folder.uri)
        .get<{ type?: string }[]>('configurations') ?? [];
    return configurations.some(configuration => configuration.type === 'sniffer');
}
