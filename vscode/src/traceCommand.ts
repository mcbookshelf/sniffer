import * as vscode from 'vscode';
import { TracePanel } from './tracePanel';

/**
 * Asks the attached session to trace a command, and opens the panel that draws what comes back.
 * A refusal is the mod saying a trace is already running, in which case nothing was executed.
 */
export async function traceCommand(): Promise<void> {
    const session = vscode.debug.activeDebugSession;
    if (session?.type !== 'sniffer') {
        void vscode.window.showWarningMessage('Attach to Minecraft before tracing a command.');
        return;
    }

    const command = await vscode.window.showInputBox({
        title: 'Trace a command',
        prompt: 'The command to trace, as you would type it in game',
        placeHolder: 'function my_pack:main',
    });
    if (!command) {
        return;
    }

    try {
        const response = await session.customRequest('snifferTrace', { command });
        if (response?.traceId === null || response?.traceId === undefined) {
            void vscode.window.showWarningMessage('Minecraft refused the trace: another one is already running.');
        }
    } catch (error: any) {
        void vscode.window.showErrorMessage(`Could not trace the command: ${error?.message ?? error}`);
    }
}

/** Wires the command and keeps the panel fed for as long as the extension lives. */
export function registerTrace(context: vscode.ExtensionContext): void {
    context.subscriptions.push(
        vscode.commands.registerCommand('sniffer.trace', () => traceCommand()),
        vscode.debug.onDidReceiveDebugSessionCustomEvent(
            event => TracePanel.handleEvent(event, context.extensionUri),
        ),
    );
}
