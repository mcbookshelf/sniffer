import { strict as assert } from 'assert';
import { TraceGraph } from './traceGraph';
import { COMMAND_NODE, type SerializedGraph } from './traceWire';

const call = (fn: string, callerLine: number | undefined) =>
    ({ traceId: 1, function: fn, executor: 'Server', callerLine, source: { path: `/packs/${fn}.mcfunction` } });

/** The nesting is rebuilt from a flat stream, so what is checked is that calls and returns pair up. */
function nestingFollowsTheEvents(): void {
    const graph = new TraceGraph();
    graph.start({ traceId: 1, command: 'function pack:outer' });
    graph.call(call('pack:outer', undefined));
    graph.call(call('pack:inner', 2));
    graph.return({ traceId: 1, line: 2 });
    graph.call(call('pack:other', 3));
    graph.return({ traceId: 1, line: 1 });
    graph.return({ traceId: 1, line: 4 });
    graph.end({ traceId: 1, reason: 'completed' });

    assert.equal(graph.roots.length, 1, 'one root');
    const outer = graph.roots[0];
    assert.equal(outer.returnLine, 4);
    assert.deepEqual(outer.children.map(child => child.call.function), ['pack:inner', 'pack:other']);
    assert.equal(outer.children[0].children.length, 0, 'a returned call takes no further children');
    assert.equal(graph.status, 'completed');
}

/** A trace cut short leaves the calls it did make, which is the whole point of streaming them. */
function anUnfinishedTraceKeepsWhatItGot(): void {
    const graph = new TraceGraph();
    graph.start({ traceId: 2, command: 'function pack:outer' });
    graph.call(call('pack:outer', undefined));
    graph.call(call('pack:inner', 2));
    graph.end({ traceId: 2, reason: 'cancelled' });

    assert.equal(graph.status, 'cancelled');
    assert.equal(graph.roots[0].children[0].returnLine, undefined, 'the open call never returned');
    // The cursor is dropped with the trace, so a stray return cannot reopen it.
    graph.return({ traceId: 2, line: 9 });
    assert.equal(graph.roots[0].children[0].returnLine, undefined);
}

/** Starting again throws the previous graph away rather than drawing two traces at once. */
function anewTraceReplacesThePreviousOne(): void {
    const graph = new TraceGraph();
    graph.start({ traceId: 1, command: 'function pack:a' });
    graph.call(call('pack:a', undefined));
    graph.start({ traceId: 2, command: 'function pack:b' });
    graph.call(call('pack:b', undefined));

    assert.deepEqual(graph.roots.map(root => root.call.function), ['pack:b']);
    assert.equal(graph.command, 'function pack:b');
}

/**
 * A zipped function opens through its reference rather than its path, and the path has to go with it:
 * it points inside the archive, so a client trying it first would fail.
 */
function azippedFunctionOpensThroughItsReference(): void {
    const graph = new TraceGraph();
    graph.start({ traceId: 1, command: 'function pack:a' });
    graph.call({
        traceId: 1, function: 'pack:a', executor: 'Server', callerLine: undefined,
        source: { name: 'pack:a', path: '/packs/bundle.zip', sourceReference: 7 },
    });
    assert.deepEqual(graph.roots[0].openableSource, { name: 'pack:a', sourceReference: 7 });
    assert.equal(graph.serialize().nodes.find(node => node.id === 'pack:a')?.openable, true);
}

for (const check of [
    nestingFollowsTheEvents,
    anUnfinishedTraceKeepsWhatItGot,
    anewTraceReplacesThePreviousOne,
    azippedFunctionOpensThroughItsReference,
]) {
    check();
    console.log(`ok ${check.name}`);
}

const edgeOf = (graph: SerializedGraph, from: string, to: string) =>
    graph.edges.find(edge => edge.from === from && edge.to === to);

/** A helper called from everywhere is one node, however many times it ran. */
function invocationsOfOneFunctionMergeIntoOneNode(): void {
    const graph = new TraceGraph();
    graph.start({ traceId: 1, command: 'function pack:outer' });
    graph.call(call('pack:outer', undefined));
    graph.call(call('pack:helper', 2));
    graph.return({ traceId: 1, line: 1 });
    graph.call(call('pack:helper', 3));
    graph.return({ traceId: 1, line: 1 });
    graph.return({ traceId: 1, line: 4 });

    const drawn = graph.serialize();
    assert.deepEqual(
        drawn.nodes.map(node => node.id).sort(),
        [COMMAND_NODE, 'pack:helper', 'pack:outer'],
    );
    assert.equal(drawn.nodes.find(node => node.id === 'pack:helper')?.calls, 2);

    // The order is gone, so the edge has to carry where the calls were made and how many there were.
    const edge = edgeOf(drawn, 'pack:outer', 'pack:helper');
    assert.equal(edge?.count, 2);
    assert.deepEqual(edge?.lines, [2, 3]);
}

/** Recursion is an edge onto the node itself rather than a chain as deep as the stack. */
function recursionBecomesAnEdgeOntoItself(): void {
    const graph = new TraceGraph();
    graph.start({ traceId: 1, command: 'function pack:loop' });
    graph.call(call('pack:loop', undefined));
    graph.call(call('pack:loop', 3));
    graph.call(call('pack:loop', 3));

    const drawn = graph.serialize();
    assert.deepEqual(drawn.nodes.map(node => node.id).sort(), [COMMAND_NODE, 'pack:loop']);
    assert.equal(edgeOf(drawn, 'pack:loop', 'pack:loop')?.count, 2);
    assert.equal(edgeOf(drawn, COMMAND_NODE, 'pack:loop')?.count, 1);
    assert.equal(drawn.nodes.find(node => node.id === 'pack:loop')?.running, true);
}

/** A function reached from two callers keeps both edges, which is what the merge is for. */
function afunctionCalledFromTwoPlacesKeepsBothEdges(): void {
    const graph = new TraceGraph();
    graph.start({ traceId: 1, command: 'function pack:a' });
    graph.call(call('pack:a', undefined));
    graph.call(call('pack:shared', 1));
    graph.return({ traceId: 1, line: 1 });
    graph.return({ traceId: 1, line: 2 });
    graph.call(call('pack:b', undefined));
    graph.call(call('pack:shared', 5));
    graph.return({ traceId: 1, line: 1 });
    graph.return({ traceId: 1, line: 6 });

    const drawn = graph.serialize();
    assert.equal(drawn.edges.filter(edge => edge.to === 'pack:shared').length, 2);
    assert.equal(drawn.nodes.find(node => node.id === 'pack:shared')?.calls, 2);
    assert.equal(drawn.nodes.find(node => node.id === 'pack:shared')?.running, false);
}

for (const check of [
    invocationsOfOneFunctionMergeIntoOneNode,
    recursionBecomesAnEdgeOntoItself,
    afunctionCalledFromTwoPlacesKeepsBothEdges,
]) {
    check();
    console.log(`ok ${check.name}`);
}

/** A click resolves a function to the way it is opened, which the game does not always know. */
function asourceIsKeptForEveryLocatedFunction(): void {
    const graph = new TraceGraph();
    graph.start({ traceId: 1, command: 'function pack:a' });
    graph.call(call('pack:a', undefined));
    graph.call({
        traceId: 1, function: 'pack:zipped', executor: 'Server', callerLine: 2,
        source: { name: 'pack:zipped', path: '/packs/bundle.zip', sourceReference: 7 },
    });
    // Named but never located, which is what an unresolved function looks like.
    graph.call({
        traceId: 1, function: 'pack:unknown', executor: 'Server', callerLine: 3,
        source: { name: 'pack:unknown' },
    });

    assert.deepEqual(graph.sourceOf('pack:a')?.path, '/packs/pack:a.mcfunction');
    assert.deepEqual(graph.sourceOf('pack:zipped')?.sourceReference, 7);
    assert.equal(graph.sourceOf('pack:unknown'), undefined);
    assert.equal(graph.sourceOf('pack:never-called'), undefined);
}

asourceIsKeptForEveryLocatedFunction();
console.log('ok asourceIsKeptForEveryLocatedFunction');
