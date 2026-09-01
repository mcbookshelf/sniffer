/**
 * The extension ships a declaration of globals rather than of a module, so its module shape is declared here.
 * It draws an HTML fragment over each node, which is what lets one node hold two text styles: the canvas
 * renderer has a single `color` and `font-size` per element.
 */
declare module 'cytoscape-node-html-label' {
    export interface NodeHtmlLabelOptions {
        query?: string;
        halign?: 'left' | 'center' | 'right';
        valign?: 'top' | 'center' | 'bottom';
        halignBox?: 'left' | 'center' | 'right';
        valignBox?: 'top' | 'center' | 'bottom';
        cssClass?: string;
        tpl?: (data: Record<string, unknown>) => string;
    }

    /** What the extension adds to a graph once it is registered. */
    export interface WithHtmlLabels {
        nodeHtmlLabel(options: NodeHtmlLabelOptions[]): void;
    }

    const register: (instance: unknown) => void;
    export default register;
}
