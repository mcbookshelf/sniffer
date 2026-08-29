import { COMMAND_NODE, type GraphEdge, type GraphNode, type SerializedGraph, type TraceStatus } from './traceWire';

/** The shape of the four events the mod pushes while it walks a traced execution. */
export interface TraceStarted { traceId: number; command: string }
export interface TraceCall {
    traceId: number;
    function: string;
    source?: { name?: string; path?: string; sourceReference?: number };
    callerLine?: number;
    executor: string;
}
export interface TraceReturn { traceId: number; line?: number }
export interface TraceEnded { traceId: number; reason: string }

/**
 * How a function is opened: by path, or by the reference the debug session answers with its content.
 * The two are exclusive, and a function the game could not locate has neither.
 */
export interface TraceSource {
    name?: string;
    path?: string;
    sourceReference?: number;
}

/** One invocation of a function, holding the calls it made in the order it made them. */
export class TraceNode {
    readonly children: TraceNode[] = [];
    returnLine?: number;

    constructor(
        readonly id: number,
        readonly call: TraceCall,
        readonly parent?: TraceNode,
    ) { }

    /**
     * The source this invocation can be opened through, `undefined` when the game could not locate it.
     *
     * A function loaded from a zipped pack has no path an editor can open, but it carries a reference the
     * debug session answers with its content, so it opens all the same.
     * The path is dropped in that case: the protocol says the reference wins, and leaving both invites a
     * client to try the path first.
     */
    get openableSource(): TraceSource | undefined {
        const source = this.call.source;
        if (source?.sourceReference) {
            return { name: source.name, sourceReference: source.sourceReference };
        }
        return source?.path ? { name: source.name, path: source.path } : undefined;
    }
}

/**
 * The call graph of the traced execution, built as its events arrive.
 *
 * Calls and returns pair up like brackets, so a single cursor is enough to know where the next call belongs:
 * it descends on a call and rises on a return.
 * A function called twice is two nodes, since what is drawn is the execution rather than the pack.
 */
export class TraceGraph {
    roots: TraceNode[] = [];
    command = '';
    status: TraceStatus = 'idle';

    private cursor?: TraceNode;
    private nextId = 0;
    /** How each function is reached, which is what a click on the drawn graph resolves against. */
    private readonly sources = new Map<string, TraceSource>();

    /** The source a function can be opened through, `undefined` when the game could not locate it. */
    sourceOf(functionId: string): TraceSource | undefined {
        return this.sources.get(functionId);
    }

    start(event: TraceStarted): void {
        this.roots = [];
        this.sources.clear();
        this.cursor = undefined;
        this.nextId = 0;
        this.command = event.command;
        this.status = 'running';
    }

    call(event: TraceCall): void {
        const node = new TraceNode(this.nextId++, event, this.cursor);
        const source = node.openableSource;
        if (source !== undefined) {
            this.sources.set(node.call.function, source);
        }
        (this.cursor ? this.cursor.children : this.roots).push(node);
        this.cursor = node;
    }

    return(event: TraceReturn): void {
        if (!this.cursor) {
            return;
        }
        this.cursor.returnLine = event.line;
        this.cursor = this.cursor.parent;
    }

    end(event: TraceEnded): void {
        this.status = event.reason === 'completed' ? 'completed' : 'cancelled';
        this.cursor = undefined;
    }

    /**
     * The graph as the webview draws it: one node per function, however many times it was called.
     *
     * A helper called five hundred times is one box with five hundred calls behind it rather than five hundred
     * boxes, and a function calling itself is an edge onto its own node.
     * What the merge gives up is the order the calls happened in, which is why the edges carry the lines
     * they were made from and how often each was taken.
     */
    serialize(): SerializedGraph {
        const nodes = new Map<string, GraphNode>();
        const edges = new Map<string, GraphEdge>();

        const visit = (invocation: TraceNode): void => {
            const id = invocation.call.function;
            const node = nodes.get(id) ?? blankNode(id, invocation);
            node.calls += 1;
            node.running ||= invocation.returnLine === undefined;
            node.openable ||= invocation.openableSource !== undefined;
            if (invocation.returnLine !== undefined) {
                node.lastReturnLine = invocation.returnLine;
            }
            if (!node.executors.includes(invocation.call.executor)) {
                node.executors.push(invocation.call.executor);
            }
            nodes.set(id, node);

            const from = invocation.parent?.call.function ?? COMMAND_NODE;
            const key = `${from} ${id}`;
            const edge = edges.get(key) ?? { from, to: id, lines: [], count: 0 };
            edge.count += 1;
            const line = invocation.call.callerLine;
            if (line !== undefined && line !== null && !edge.lines.includes(line)) {
                edge.lines.push(line);
            }
            edges.set(key, edge);

            invocation.children.forEach(visit);
        };
        this.roots.forEach(visit);

        for (const node of nodes.values()) {
            node.executors.sort();
        }

        return {
            command: this.command,
            status: this.status,
            nodes: [commandNode(this.command), ...nodes.values()],
            edges: [...edges.values()].map(edge => ({ ...edge, lines: edge.lines.sort((a, b) => a - b) })),
        };
    }
}

function blankNode(id: string, invocation: TraceNode): GraphNode {
    return {
        id,
        calls: 0,
        executors: [],
        running: false,
        lastReturnLine: invocation.returnLine ?? null,
        openable: false,
    };
}

function commandNode(command: string): GraphNode {
    return {
        id: COMMAND_NODE,
        calls: 0,
        executors: [command],
        running: false,
        lastReturnLine: null,
        openable: false,
    };
}
