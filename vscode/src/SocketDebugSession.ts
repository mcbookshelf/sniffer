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
import { TerminatedEvent } from '@vscode/debugadapter';

const WEBSOCKET_REGEX = /^wss?:\/\/|^https?:\/\//;
const SOCKET_TIMEOUT = 10000;
const PATH_PREFIX = '"path":"';

export class SocketDebugSession extends ProtocolServer {
    protected adapter: DebugAdapter;

    public constructor(address: string, protected pathMapping?: { [key: string]: string }) {
        console.log('SocketDebugSession constructor', address);
        super();
        if (WEBSOCKET_REGEX.test(address)) {
            this.adapter = new WebsocketDebugAdapter(address);
        } else {
            throw new Error('Raw socket connections are not supported, use a websocket address');
        }
        this.adapter.on('message', message => this.onMessage(message));
        this.adapter.on('error', (event: DebugProtocol.Event) => {
            this.emit(event.event, event);
            this.terminateSession();
        });
        this.adapter.on('close', (event: DebugProtocol.Event) => {
            this.emit(event.event, event);
            this.terminateSession();
        });
    }

    public dispose() {
        this.adapter.stop();
    }

    protected async dispatchRequest(request: DebugProtocol.Request): Promise<void> {
        const message = this.replacePaths(request, 'outgoing');
        this.adapter.send(message);
    }

    protected onMessage(message: DebugProtocol.ProtocolMessage): void {
        message = this.replacePaths(message, 'incoming');

        if (message.type === 'request') {
        } else if (message.type === 'response') {
            const msg = message as DebugProtocol.Response;
            // sendResponse expects seq not to be set as it will add it's own
            msg.seq = 0;
            this.sendResponse(msg);
        } else if (message.type === 'event') {
            this.sendEvent(message as DebugProtocol.Event);
        }
    }

    protected replacePaths(message: DebugProtocol.ProtocolMessage, direction: 'incoming' | 'outgoing'): DebugProtocol.ProtocolMessage {
        if (!this.pathMapping) {
            return message;
        }

        let json = JSON.stringify(message);

        for (const [remote, local] of Object.entries(this.pathMapping)) {
            if (direction === 'incoming') {
                json = json.replace(new RegExp(PATH_PREFIX + remote, 'g'), PATH_PREFIX + local);
            } else {
                json = json.replace(new RegExp(PATH_PREFIX + local, 'g'), PATH_PREFIX + remote);
            }
        }

        return JSON.parse(json);
    }

    private terminateSession() {
        console.log('WebSocket connection closed, terminating debug session');
        this.sendEvent(new TerminatedEvent());
        // vscode.window.showWarningMessage('Connection reset detected, terminating debug session');
    }
}

abstract class DebugAdapter extends ProtocolServer {
    protected stream: NodeJS.ReadWriteStream;

    public constructor(address: string) {
        super();
        this.stream = this.createStream(address);
        this.stream.on('error', (error) => {
            // Capture and log the complete error
            console.error('Stream error in DebugAdapter:', error);
            this.emit('error', {
                seq: 0,
                type: 'event',
                event: 'error',
                body: `Stream error: ${error ? (error.message || JSON.stringify(error)) : 'Unknown error'}`
            });
        });
        
        this.stream.on('close', () => {
            console.log('Stream closed');
            this.emit('close', {
                seq: 0,
                type: 'event',
                event: 'close'
            });
        });
        
        super.start(this.stream, this.stream);
    }

    public handleMessage(message: DebugProtocol.ProtocolMessage): void {
        this.emit('message', message);
    }

    public send(message: DebugProtocol.ProtocolMessage) {
        const json = JSON.stringify(message);
        this.stream.write(`Content-Length: ${Buffer.byteLength(json, 'utf8')}\r\n\r\n${json}`, 'utf8');
    }

    protected abstract createStream(address: string): NodeJS.ReadWriteStream;
}

// WebSocket close codes used by the Sniffer server. 1008 is the RFC 6455
// "policy violation" code, which the server sends for every auth rejection
// (missing/blank user param, player not online, not an op, prompt rejected,
// prompt timed out, superseded).
const WS_POLICY_VIOLATION = 1008;

function formatConnectionError(
    error: any,
    closeCode: number | undefined,
    closeReason: string | undefined,
): string {
    if (closeReason) {
        if (closeCode === WS_POLICY_VIOLATION) {
            return `Debug connection rejected by server — ${closeReason}.`;
        }
        return `Connection closed (${closeCode ?? 'unknown code'}): ${closeReason}`;
    }
    if (closeCode !== undefined) {
        return `Connection closed (code ${closeCode}) before the handshake completed.`;
    }
    return error ? (error.message || JSON.stringify(error)) : 'Unknown error';
}

class WebsocketDebugAdapter extends DebugAdapter {
    protected createStream(address: string): NodeJS.ReadWriteStream {
        console.log(`Trying to connect to WebSocket server at: ${address}`);

        try {
            // Formater l'adresse pour s'assurer qu'elle est valide
            let formattedAddress = address;
            if (!formattedAddress.startsWith('ws://') && !formattedAddress.startsWith('wss://')) {
                // Si l'adresse ne commence pas par ws:// ou wss://, ajouter ws://
                formattedAddress = `ws://${formattedAddress}`;
                console.log(`Reformatted address to: ${formattedAddress}`);
            }

            const options = {
                handshakeTimeout: SOCKET_TIMEOUT,
                perMessageDeflate: false  // Disable compression for better compatibility
            };

            console.log(`Connecting with options:`, options);
            const stream = WebSocketStream(formattedAddress, options);

            // Capture the WebSocket close frame (code + reason) so we can
            // surface a clear message when the server rejects the connection.
            // Without this, the user only sees "write after end" — the
            // downstream error from the adapter trying to write to a socket
            // the server closed with a 1008 VIOLATED_POLICY frame.
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
                vscode.window.showErrorMessage(`Cannot attach to debug server at ${formattedAddress}: ${message}`);
            });

            stream.on('connect', () => {
                console.log(`Successfully connected to WebSocket server at: ${formattedAddress}`);
                vscode.window.showInformationMessage(`Connected to debug server at ${formattedAddress}`);
            });

            return stream;
        } catch (error: any) {
            const errorMessage = error ? (error.message || JSON.stringify(error)) : 'Unknown error';
            console.error(`Failed to create WebSocket connection: ${errorMessage}`);
            vscode.window.showErrorMessage(`Failed to create WebSocket connection to ${address}: ${errorMessage}`);
            throw error;
        }
    }
}