import * as vscode from 'vscode';
import { TraceGraph, type TraceCall, type TraceEnded, type TraceStarted, type TraceReturn } from './traceGraph';
import type { FromWebview, ToWebview } from './traceWire';

/** How long the panel waits before redrawing, so a fast execution does not redraw once per call. */
const REDRAW_DELAY_MS = 80;

/**
 * The traced call graph, drawn as a diagram in an editor tab.
 *
 * A single panel is reused for every trace: the graph replaces the previous one, the way a debugger's call
 * stack does rather than piling tabs up.
 */
export class TracePanel {

    private static instance?: TracePanel;

    private readonly graph = new TraceGraph();
    private redraw?: NodeJS.Timeout;
    /** The webview drops anything posted before its script runs, and it is opened by the first event of a trace. */
    private ready = false;
    private readonly themeChanged: vscode.Disposable;

    private constructor(
        private readonly panel: vscode.WebviewPanel,
        private readonly extensionUri: vscode.Uri,
    ) {
        panel.webview.html = this.html();
        panel.webview.onDidReceiveMessage(message => this.onMessage(message));
        // Cytoscape draws on a canvas, so it cannot follow the theme on its own the way CSS would.
        this.themeChanged = vscode.window.onDidChangeActiveColorTheme(
            () => void this.post({ type: 'theme' }),
        );
        panel.onDidDispose(() => {
            TracePanel.instance = undefined;
            this.themeChanged.dispose();
            if (this.redraw) {
                clearTimeout(this.redraw);
            }
        });
    }

    /** Opens the panel, or brings back the one already open. */
    static reveal(extensionUri: vscode.Uri): TracePanel {
        if (TracePanel.instance) {
            TracePanel.instance.panel.reveal(undefined, true);
            return TracePanel.instance;
        }
        const panel = vscode.window.createWebviewPanel(
            'sniffer.trace',
            'Trace',
            { viewColumn: vscode.ViewColumn.Beside, preserveFocus: true },
            { enableScripts: true, retainContextWhenHidden: true, localResourceRoots: [extensionUri] },
        );
        TracePanel.instance = new TracePanel(panel, extensionUri);
        return TracePanel.instance;
    }

    /**
     * Feeds the panel, opening it when a trace begins.
     * A trace is started as readily by `/trace run` typed in game as by the command of this extension,
     * so waiting for the reader to open the panel first would drop the graph of every trace but ours.
     */
    static handleEvent(event: vscode.DebugSessionCustomEvent, extensionUri: vscode.Uri): void {
        if (event.event === 'snifferTraceStarted') {
            TracePanel.reveal(extensionUri);
        }
        TracePanel.instance?.consume(event);
    }

    private consume(event: vscode.DebugSessionCustomEvent): void {
        switch (event.event) {
            case 'snifferTraceStarted': this.graph.start(event.body as TraceStarted); break;
            case 'snifferTraceCall': this.graph.call(event.body as TraceCall); break;
            case 'snifferTraceReturn': this.graph.return(event.body as TraceReturn); break;
            case 'snifferTraceEnded': this.graph.end(event.body as TraceEnded); break;
            default: return;
        }
        this.schedule();
    }

    /**
     * Redraws once the events settle.
     * A trace of a busy function arrives as a burst, and drawing each of them would spend the frame budget
     * on graphs nobody sees.
     */
    private schedule(): void {
        if (this.redraw) {
            return;
        }
        this.redraw = setTimeout(() => {
            this.redraw = undefined;
            this.post({ type: 'graph', graph: this.graph.serialize() });
        }, REDRAW_DELAY_MS);
    }

    /** Nothing reaches a webview whose script has not run yet, so nothing is sent before it says so. */
    private post(message: ToWebview): void {
        if (this.ready) {
            void this.panel.webview.postMessage(message);
        }
    }

    private onMessage(message: FromWebview): void {
        if (message.type === 'ready') {
            this.ready = true;
            // Whatever arrived while the script was loading is in the graph already, so one post catches up.
            this.post({ type: 'graph', graph: this.graph.serialize() });
            return;
        }
        if (message.type === 'open') {
            void this.open(message.function, message.lines ?? []);
        }
    }

    /**
     * Opens a function, at one of [lines] when the click was on an edge rather than on a node.
     * An edge stands for every call between the two functions, so a call written in several places asks
     * which one was meant rather than picking one.
     */
    private async open(functionId: string, lines: number[]): Promise<void> {
        const uri = this.uriOf(functionId);
        if (!uri) {
            return;
        }

        const line = lines.length > 1 ? await pickLine(functionId, lines) : lines[0];
        if (lines.length > 1 && line === undefined) {
            return;
        }

        const document = await vscode.window.showTextDocument(
            uri,
            { preview: true, viewColumn: vscode.ViewColumn.One },
        );
        if (line !== undefined) {
            // The mod counts lines the way an editor shows them, one further along than the API wants.
            const position = new vscode.Position(Math.max(0, line - 1), 0);
            document.selection = new vscode.Selection(position, position);
            document.revealRange(new vscode.Range(position, position), vscode.TextEditorRevealType.InCenter);
        }
    }

    /**
     * Where a function is opened from, `undefined` when it cannot be, having said why.
     *
     * A function loaded from a zipped pack has no file to open, but the session that traced it can answer with
     * its content, which is what a `debug:` uri asks it for. That needs the session to still be running, so a
     * graph outliving its session keeps its zipped functions unopenable.
     */
    private uriOf(functionId: string): vscode.Uri | undefined {
        const source = this.graph.sourceOf(functionId);
        if (!source) {
            void vscode.window.showWarningMessage(`Minecraft did not say where ${functionId} was loaded from.`);
            return undefined;
        }
        const session = vscode.debug.activeDebugSession;
        if (source.sourceReference && !session) {
            void vscode.window.showWarningMessage(
                `${functionId} lives inside a zipped datapack, and the debug session that can read it is over.`,
            );
            return undefined;
        }
        try {
            return vscode.debug.asDebugSourceUri(source as vscode.DebugProtocolSource, session);
        } catch (error: any) {
            void vscode.window.showWarningMessage(`Could not open ${functionId}: ${error?.message ?? error}`);
            return undefined;
        }
    }

    private html(): string {
        const script = this.panel.webview.asWebviewUri(
            vscode.Uri.joinPath(this.extensionUri, 'dist', 'trace.js'),
        );
        // A nonce is what lets the bundled script run under a policy that refuses every other one.
        const nonce = Array.from({ length: 32 }, () => Math.floor(Math.random() * 36).toString(36)).join('');

        return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta http-equiv="Content-Security-Policy"
      content="default-src 'none'; style-src ${this.panel.webview.cspSource} 'unsafe-inline'; script-src 'nonce-${nonce}';">
<style>
  html, body { height: 100%; margin: 0; font-family: var(--vscode-font-family); color: var(--vscode-foreground); }
  body { display: flex; flex-direction: column; background: var(--vscode-editor-background); }
  header {
    display: flex; align-items: center; gap: 12px; padding: 6px 10px;
    border-bottom: 1px solid var(--vscode-panel-border); font-size: 12px;
  }
  #status { flex: 1; opacity: 0.9; }
  #status.running::before { content: '● '; color: var(--vscode-charts-blue); }
  #status.completed::before { content: '● '; color: var(--vscode-charts-green); }
  #status.cancelled::before { content: '● '; color: var(--vscode-charts-orange); }
  button {
    background: var(--vscode-button-secondaryBackground); color: var(--vscode-button-secondaryForeground);
    border: none; padding: 3px 10px; border-radius: 2px; cursor: pointer; font-size: 12px;
  }
  button:hover { background: var(--vscode-button-secondaryHoverBackground); }
  /* The boxes and the arrows are drawn on a canvas by cytoscape, which resolves its colours from these
     variables. The node text is HTML laid over it, so it uses them directly. */
  #graph { flex: 1; width: 100%; }
  .node-label {
    display: flex; flex-direction: column; align-items: center; gap: 2px;
    width: 200px; text-align: center; pointer-events: none;
  }
  .node-label span { max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .node-name { font-size: 12px; color: var(--vscode-foreground); }
  .node-detail { font-size: 10px; color: var(--vscode-descriptionForeground); }
</style>
</head>
<body>
<header>
  <span id="status">No trace yet.</span>
  <button id="fit">Fit</button>
</header>
<div id="graph"></div>
<script nonce="${nonce}" src="${script}"></script>
</body>
</html>`;
    }
}

/** Which of the call sites of one edge to jump to. */
async function pickLine(functionId: string, lines: number[]): Promise<number | undefined> {
    const picked = await vscode.window.showQuickPick(
        lines.map(line => ({ label: `Line ${line}`, line })),
        { title: `Calls in ${functionId}`, placeHolder: 'Which call site to open' },
    );
    return picked?.line;
}
