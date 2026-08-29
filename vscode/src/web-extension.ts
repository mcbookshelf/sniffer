/*---------------------------------------------------------
 * Copyright (C) Microsoft Corporation. All rights reserved.
 *--------------------------------------------------------*/
/*
 * web-extension.ts (and activateMockDebug.ts) forms the "plugin" that plugs into VS Code and contains the code that
 * connects VS Code with the debug adapter.
 * 
 * web-extension.ts launches the debug adapter "inlined" because that's the only supported mode for running the debug adapter in the browser.
 */

import * as vscode from 'vscode';
import { configureLaunch, offerSetup } from './configureLaunch';
import { TracePanel } from './tracePanel';

export function activate(context: vscode.ExtensionContext) {
	context.subscriptions.push(
		vscode.commands.registerCommand('sniffer.configureLaunch', () => configureLaunch())
	);
	TracePanel.register(context);
	void offerSetup(context);
}

export function deactivate() {
	// nothing to do
}
