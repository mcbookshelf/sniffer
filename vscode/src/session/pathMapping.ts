import { DebugProtocol } from '@vscode/debugprotocol';

const PATH_PREFIX = '"path":"';

/**
 * Rewrites the paths of a message between the game's spelling of them and the editor's,
 * as declared by the `pathMapping` of the launch configuration.
 *
 * The serialized message is substituted rather than the object walked, because a path sits in a different
 * place in every request, and every one of them is spelled `"path":`.
 *
 * ponytail: a mapped path containing a regex metacharacter would misfire.
 * Escape both sides if a pack is ever debugged from a directory named like a pattern.
 */
export function remapPaths(
    message: DebugProtocol.ProtocolMessage,
    mapping: Record<string, string> | undefined,
    direction: 'toGame' | 'toEditor',
): DebugProtocol.ProtocolMessage {
    if (!mapping) {
        return message;
    }
    let json = JSON.stringify(message);
    for (const [remote, local] of Object.entries(mapping)) {
        const [from, to] = direction === 'toGame' ? [local, remote] : [remote, local];
        json = json.replace(new RegExp(PATH_PREFIX + from, 'g'), PATH_PREFIX + to);
    }
    return JSON.parse(json);
}
