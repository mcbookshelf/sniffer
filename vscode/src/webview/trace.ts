import cytoscape from 'cytoscape';
import dagre, { type DagreLayoutOptions } from 'cytoscape-dagre';
import nodeHtmlLabel, { type WithHtmlLabels } from 'cytoscape-node-html-label';
import {
    COMMAND_NODE,
    type FromWebview,
    type GraphEdge,
    type GraphNode,
    type SerializedGraph,
    type ToWebview,
} from '../traceWire';

cytoscape.use(dagre);
nodeHtmlLabel(cytoscape);

const FIT_PADDING = 30;

declare function acquireVsCodeApi(): { postMessage(message: FromWebview): void };

const vscode = acquireVsCodeApi();

const container = document.getElementById('graph');

const cy = cytoscape({
    container,
    style: stylesheet(),
    minZoom: 0.1,
    maxZoom: 3,
    // A tap jumps to the code, it does not pick anything: nothing may stay highlighted once it is over.
    autounselectify: true,
    // The layout places the nodes; dragging one would only break the layers it was placed in.
    autoungrabify: true,
});

/**
 * What a click would act on, told by the pointer before the click rather than after it.
 * The graph is a canvas, so there is no element to hang a `:hover` rule or a cursor on: the class is put on
 * by hand, and the cursor is set on the canvas itself.
 */
const ACTIONABLE = 'node.openable, edge.actionable';

// The node text is HTML rather than canvas, so its two lines can carry two styles, and it follows the
// editor theme through the CSS variables the way the rest of the panel does.
(cy as unknown as WithHtmlLabels).nodeHtmlLabel([{
    query: 'node',
    halign: 'center',
    valign: 'center',
    tpl: data => '<div class="node-label">'
        + `<span class="node-name">${escapeHtml(String(data.name))}</span>`
        + `<span class="node-detail">${escapeHtml(String(data.detail))}</span>`
        + '</div>',
}]);

cy.on('mouseover', ACTIONABLE, event => {
    event.target.addClass('hovered');
    setCursor('pointer');
});

cy.on('mouseout', ACTIONABLE, event => {
    event.target.removeClass('hovered');
    setCursor('default');
});

cy.on('tap', 'node.openable', event => {
    vscode.postMessage({ type: 'open', function: event.target.id() });
});

// An edge is a call site, which lives in the function the call was made from.
cy.on('tap', 'edge.actionable', event => {
    const edge = event.target;
    vscode.postMessage({
        type: 'open',
        function: edge.source().id(),
        lines: edge.data('lines') as number[],
    });
});

/** Set once the first graph is fitted, so a running trace does not fight the reader for the viewport. */
let framed = false;

window.addEventListener('message', event => {
    const message = event.data as ToWebview;
    if (message.type === 'graph') {
        render(message.graph);
    } else if (message.type === 'theme') {
        // The stylesheet holds resolved colours, not variables, since the graph is drawn on a canvas.
        cy.style(stylesheet());
    }
});

document.getElementById('fit')?.addEventListener('click', () => cy.fit(undefined, FIT_PADDING));

// The panel is opened by the first event of a trace, so it has a graph to send before this script exists.
vscode.postMessage({ type: 'ready' });

function render(graph: SerializedGraph): void {
    setStatus(graph);

    cy.elements().remove();
    // Whatever was under the pointer is gone with them, so the cursor it set has to go too.
    setCursor('default');
    // A trace holding nothing but the command node is not worth drawing.
    if (graph.nodes.length <= 1) {
        return;
    }

    cy.add(elementsOf(graph));
    cy.layout({
        name: 'dagre',
        rankDir: 'TB',
        nodeSep: 45,
        rankSep: 70,
        fit: !framed,
        padding: FIT_PADDING,
    } as DagreLayoutOptions).run();
    framed = true;
}

function elementsOf(graph: SerializedGraph): cytoscape.ElementDefinition[] {
    const nodes = graph.nodes.map(node => ({
        data: { id: node.id, name: nameOf(node), detail: detailOf(node), tooltip: describeNode(node) },
        classes: classesOf(node),
    }));
    const edges = graph.edges.map(edge => ({
        data: {
            id: `${edge.from} ${edge.to}`,
            source: edge.from,
            target: edge.to,
            label: describeEdge(edge),
            lines: edge.lines,
        },
        // The call sites of an edge live in the function it leaves, which the traced command is not.
        classes: edge.from !== COMMAND_NODE && edge.lines.length > 0 ? 'actionable' : '',
    }));
    return [...nodes, ...edges];
}

function classesOf(node: GraphNode): string {
    const classes: string[] = [];
    if (node.id === COMMAND_NODE) {
        classes.push('command');
    }
    if (node.running) {
        classes.push('running');
    }
    if (node.openable) {
        classes.push('openable');
    }
    return classes.join(' ');
}

/**
 * The colours of the editor, resolved rather than referenced.
 * The graph is drawn on a canvas, so a CSS variable in the stylesheet would mean nothing: the values are read
 * once here and read again whenever the extension says the theme changed.
 */
/* eslint-disable @typescript-eslint/naming-convention -- cytoscape style keys are kebab-case by contract */
function stylesheet(): cytoscape.StylesheetJson {
    const css = getComputedStyle(document.body);
    const colour = (name: string, fallback: string) => css.getPropertyValue(name).trim() || fallback;

    const foreground = colour('--vscode-foreground', '#ccc');
    const muted = colour('--vscode-descriptionForeground', '#999');
    const border = colour('--vscode-panel-border', '#555');
    const surface = colour('--vscode-editorWidget-background', '#252526');
    const accent = colour('--vscode-charts-blue', '#3794ff');
    const pending = colour('--vscode-charts-orange', '#d18616');
    const loop = colour('--vscode-charts-purple', '#b180d7');
    const focus = colour('--vscode-focusBorder', '#007fd4');

    return [
        {
            selector: 'node',
            style: {
                'shape': 'round-rectangle',
                'width': 220,
                'height': 46,
                'background-color': surface,
                'border-color': border,
                'border-width': 1,
            },
        },
        { selector: 'node.command', style: { 'border-color': accent, 'border-width': 2 } },
        { selector: 'node.running', style: { 'border-color': pending, 'border-style': 'dashed' } },
        // Cytoscape greys an element while it is pressed and puts a disc where a pan starts.
        // Neither means anything here: a press is a jump to the code, and a pan is just a pan.
        { selector: 'node, edge', style: { 'overlay-opacity': 0 } },
        {
            selector: 'core',
            // The typings demand the whole core shape here, the library takes what it is given.
            style: { 'active-bg-opacity': 0, 'active-bg-size': 0 } as unknown as cytoscape.Css.Core,
        },
        {
            selector: 'edge',
            style: {
                'width': 1.5,
                'line-color': border,
                'target-arrow-color': border,
                'target-arrow-shape': 'triangle',
                'arrow-scale': 0.9,
                'curve-style': 'bezier',
                'label': 'data(label)',
                'font-size': 10,
                'color': muted,
                'text-background-color': surface,
                'text-background-opacity': 1,
                'text-background-padding': '2',
            },
        },
        {
            selector: 'edge[source = target]',
            style: { 'line-color': loop, 'target-arrow-color': loop, 'loop-direction': '0deg', 'loop-sweep': '-40deg' },
        },
        { selector: 'node.hovered', style: { 'border-color': focus, 'border-width': 2 } },
        {
            selector: 'edge.hovered',
            style: { 'line-color': focus, 'target-arrow-color': focus, 'color': foreground },
        },
    ];
}

/* eslint-enable @typescript-eslint/naming-convention */

/** What the function is, on the first line. */
function nameOf(node: GraphNode): string {
    return node.id === COMMAND_NODE ? node.executors[0] : node.id;
}

/** Where it got to, on the second, which is the quieter of the two. */
function detailOf(node: GraphNode): string {
    if (node.id === COMMAND_NODE) {
        return 'traced command';
    }
    const times = node.calls === 1 ? '' : ` x${node.calls}`;
    const state = node.running
        ? 'running'
        : node.lastReturnLine === null ? 'returned' : `returned at ${node.lastReturnLine}`;
    return `${state}${times}`;
}

/** The labels are HTML now, and a function id is data. */
function escapeHtml(text: string): string {
    return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function describeNode(node: GraphNode): string {
    if (node.id === COMMAND_NODE) {
        return node.executors[0];
    }
    const lines = [node.id, `entered ${node.calls} time${node.calls === 1 ? '' : 's'}`];
    lines.push(node.executors.length === 1
        ? `runs as ${node.executors[0]}`
        : `runs as ${node.executors.length} different sources`);
    lines.push(node.running
        ? 'still running'
        : node.lastReturnLine === null ? 'ran no line' : `last returned at line ${node.lastReturnLine}`);
    return lines.join('\n');
}

/** What the merge would otherwise lose: where the call was written, and how often it was taken. */
function describeEdge(edge: GraphEdge): string {
    const lines = edge.lines.length === 0
        ? ''
        : `line${edge.lines.length === 1 ? '' : 's'} ${edge.lines.join(', ')}`;
    const count = edge.count === 1 ? '' : ` x${edge.count}`;
    return `${lines}${count}`.trim();
}

function setCursor(cursor: string): void {
    if (container) {
        container.style.cursor = cursor;
    }
}

function setStatus(graph: SerializedGraph): void {
    const status = document.getElementById('status');
    if (!status) {
        return;
    }
    status.className = graph.status;
    status.textContent = graph.nodes.length <= 1
        ? 'No trace yet. Run "Sniffer: Trace a command" while attached to Minecraft.'
        : `${graph.command} - ${graph.status}`;
}
