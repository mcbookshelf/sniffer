import * as vscode from 'vscode';
import { configureLaunch, offerSetup } from './launch/configureLaunch';
import { TracePanel } from './trace/tracePanel';

/**
 * What both entry points do, the desktop one adding the WebSocket adapter a browser cannot run.
 * Shared rather than written twice, since anything registered in one and forgotten in the other
 * is a feature missing from the web build with nothing to say so.
 */
export function activateCommon(context: vscode.ExtensionContext): void {
    context.subscriptions.push(
        vscode.commands.registerCommand('sniffer.configureLaunch', () => configureLaunch()),
    );
    TracePanel.register(context);
    void offerSetup(context);
}
