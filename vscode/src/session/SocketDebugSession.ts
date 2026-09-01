/*********************************************************************
 * Copyright (c) 2023 Arm Limited and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *********************************************************************/

/** Code from https://github.com/eclipse-cdt-cloud/vscode-websocket-adapter */

import { DebugProtocol } from '@vscode/debugprotocol';
import { ProtocolServer } from '@vscode/debugadapter/lib/protocol';
import * as vscode from 'vscode';
import { Response, TerminatedEvent } from '@vscode/debugadapter';
import { remapPaths } from './pathMapping';
import { WebsocketDebugAdapter } from './websocketAdapter';

/**
 * The adapter the editor talks to, forwarding both ways between it and the game.
 *
 * Everything that travels passes through [send] or [onMessage], which is what keeps the path mapping
 * from being applied twice, or not at all, as more is done to a message on its way past.
 */
export class SocketDebugSession extends ProtocolServer {
    private readonly adapter: WebsocketDebugAdapter;
    private connected = false;
    private terminated = false;

    public constructor(address: string, private readonly pathMapping?: Record<string, string>) {
        super();
        this.adapter = new WebsocketDebugAdapter(address);
        this.adapter.on('message', message => this.onMessage(message));
        this.adapter.on('connect', () => this.connected = true);
        this.adapter.on('error', () => this.terminateSession());
        this.adapter.on('close', () => this.terminateSession());
    }

    public dispose() {
        this.adapter.stop();
    }

    protected async dispatchRequest(request: DebugProtocol.Request): Promise<void> {
        if (this.terminated) {
            this.sendResponse(new Response(request));
            return;
        }
        this.send(request);
    }

    /** The one way to the game. */
    private send(message: DebugProtocol.ProtocolMessage): void {
        this.adapter.send(remapPaths(message, this.pathMapping, 'toGame'));
    }

    private onMessage(message: DebugProtocol.ProtocolMessage): void {
        message = remapPaths(message, this.pathMapping, 'toEditor');

        if (message.type === 'response') {
            const response = message as DebugProtocol.Response;
            // sendResponse expects seq not to be set as it will add it's own
            response.seq = 0;
            this.sendResponse(response);
        } else if (message.type === 'event') {
            this.sendEvent(message as DebugProtocol.Event);
        }
    }

    private terminateSession() {
        if (this.terminated) {
            return;
        }
        this.terminated = true;
        if (this.connected) {
            vscode.window.showWarningMessage('Lost the connection to Minecraft, ending the debug session.');
        }
        this.sendEvent(new TerminatedEvent());
        this.adapter.stop();
    }
}
