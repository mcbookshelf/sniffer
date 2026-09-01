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

import * as vscode from 'vscode';
import { SocketDebugSession } from './SocketDebugSession';

/** Turns one launch configuration into the session that serves it. */
export class SocketDescriptorFactory implements vscode.DebugAdapterDescriptorFactory {

    public async createDebugAdapterDescriptor(session: vscode.DebugSession): Promise<vscode.DebugAdapterDescriptor> {
        const address = session.configuration.address;
        if (!address) {
            throw new Error('No server address provided. Please specify the "address" property in your launch configuration.');
        }

        const target = appendUserParam(address, session.configuration.user);
        console.log('Connecting to server at:', target);
        return new vscode.DebugAdapterInlineImplementation(
            new SocketDebugSession(target, session.configuration.pathMapping),
        );
    }
}

/** The declared Minecraft username travels as a query parameter, so the server can prompt that player in game. */
function appendUserParam(address: string, user: string | undefined): string {
    if (!user) {
        return address;
    }
    const separator = address.includes('?') ? '&' : '?';
    return `${address}${separator}user=${encodeURIComponent(user)}`;
}
