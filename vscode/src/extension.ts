import * as vscode from 'vscode';
import { activateCommon } from './activate';
import { SocketDescriptorFactory } from './session/SocketDescriptorFactory';

/** The desktop entry point, the only one able to open a WebSocket to the game. */
export function activate(context: vscode.ExtensionContext): void {
    context.subscriptions.push(
        vscode.debug.registerDebugAdapterDescriptorFactory('sniffer', new SocketDescriptorFactory()),
    );
    activateCommon(context);
}
