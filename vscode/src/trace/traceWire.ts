/**
 * What crosses from the extension to the webview and back.
 *
 * Both sides compile separately, against different libraries, and only these shapes are common to them.
 * They live here so neither owns them and neither can drift: a webview redeclaring the graph it is sent
 * would go on compiling long after the extension stopped sending that.
 */

/** The node every root call hangs from, standing for the command the trace was asked for. */
export const COMMAND_NODE = '#command';

/** One function of the graph, however many times it was called. */
export interface GraphNode {
    id: string;
    /** How many times the function was entered over the whole trace. */
    calls: number;
    /** The sources it ran as, since merging invocations merges the ways they were executed. */
    executors: string[];
    /** Whether an invocation of it has not returned, which is where a held execution shows. */
    running: boolean;
    /** The line the latest returning invocation stopped on, `null` while none has returned. */
    lastReturnLine: number | null;
    /** Whether the editor can be asked to open it. */
    openable: boolean;
}

/** Every call made from one function to another, taken together. */
export interface GraphEdge {
    from: string;
    to: string;
    /** The lines the call was made from, which is what the merge would otherwise lose. */
    lines: number[];
    count: number;
}

export interface SerializedGraph {
    command: string;
    status: TraceStatus;
    nodes: GraphNode[];
    edges: GraphEdge[];
}

/** What the graph is doing, which is what the diagram states rather than leaving to be guessed. */
export type TraceStatus = 'idle' | 'running' | 'completed' | 'cancelled';

/** Sent by the panel. */
export type ToWebview =
    | { type: 'graph'; graph: SerializedGraph }
    | { type: 'theme' };

/**
 * Sent by the webview.
 * `ready` says its script is running, since anything posted before that is dropped.
 * `open` names the function to open and, when the click was on an edge, the call sites to choose from.
 */
export type FromWebview =
    | { type: 'ready' }
    | { type: 'open'; function: string; lines?: number[] };
