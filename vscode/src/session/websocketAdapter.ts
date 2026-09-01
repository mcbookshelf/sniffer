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

import WebSocketStream from 'websocket-stream';
import { DebugProtocol } from '@vscode/debugprotocol';
import { ProtocolServer } from '@vscode/debugadapter/lib/protocol';
import * as vscode from 'vscode';

const SOCKET_TIMEOUT = 10000;

/**
 * The RFC 6455 "policy violation" code, which the server sends for every auth rejection:
 * a missing or blank user parameter, a player not online or not an operator,
 * a prompt rejected or timed out, a connection superseded.
 */
const WS_POLICY_VIOLATION = 1008;

/**
 * The mod speaks DAP over a WebSocket, so the message framing an adapter would get for free on a pipe
 * is written out here.
 */
export class WebsocketDebugAdapter extends ProtocolServer {
    private readonly stream: NodeJS.ReadWriteStream;

    public constructor(address: string) {
        super();
        this.stream = this.createStream(toWebsocketUrl(address));

        this.stream.on('error', (error) => {
            console.error('Stream error in DebugAdapter:', error);
            this.emit('error');
        });

        this.stream.on('close', () => this.emit('close'));

        super.start(this.stream, this.stream);
    }

    public handleMessage(message: DebugProtocol.ProtocolMessage): void {
        this.emit('message', message);
    }

    public send(message: DebugProtocol.ProtocolMessage) {
        const json = JSON.stringify(message);
        this.stream.write(`Content-Length: ${Buffer.byteLength(json, 'utf8')}\r\n\r\n${json}`, 'utf8');
    }

    private createStream(address: string): NodeJS.ReadWriteStream {
        const stream = WebSocketStream(address, {
            handshakeTimeout: SOCKET_TIMEOUT,
            perMessageDeflate: false  // Disable compression for better compatibility
        });

        // The close frame carries why the server refused, and the stream only reports the write that
        // failed afterwards. Without reading it here, a rejected connection surfaces as "write after end".
        let closeCode: number | undefined;
        let closeReason: string | undefined;
        const ws: any = (stream as any).socket;
        if (ws && typeof ws.on === 'function') {
            ws.on('close', (code: number, reason: Buffer | string) => {
                closeCode = code;
                closeReason = reason
                    ? (typeof reason === 'string' ? reason : reason.toString('utf8'))
                    : undefined;
            });
        }

        stream.on('error', (error) => {
            const message = formatConnectionError(error, closeCode, closeReason);
            console.error(`WebSocket connection error: ${message}`);
            vscode.window.showErrorMessage(`Cannot attach to the Minecraft debug server at ${address}: ${message}`);
        });

        stream.on('connect', () => {
            this.emit('connect');
            vscode.window.showInformationMessage(`Connected to the Minecraft debug server at ${address}`);
        });

        return stream;
    }
}

/**
 * A launch configuration names the server as readily the way a browser would, or with no scheme at all,
 * as with the `ws://` the socket wants.
 */
function toWebsocketUrl(address: string): string {
    if (/^wss?:\/\//.test(address)) {
        return address;
    }
    const scheme = /^https:\/\//.test(address) ? 'wss' : 'ws';
    return `${scheme}://${address.replace(/^https?:\/\//, '')}`;
}

function formatConnectionError(error: any, closeCode?: number, closeReason?: string): string {
    if (closeReason) {
        return closeCode === WS_POLICY_VIOLATION
            ? `Debug connection rejected by server — ${closeReason}.`
            : `Connection closed (${closeCode ?? 'unknown code'}): ${closeReason}`;
    }
    if (closeCode !== undefined) {
        return `Connection closed (code ${closeCode}) before the handshake completed.`;
    }
    return error ? (error.message || JSON.stringify(error)) : 'Unknown error';
}
