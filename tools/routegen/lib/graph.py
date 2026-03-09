"""Graph helpers."""

from __future__ import annotations

from typing import Set

import networkx as nx


def dead_end_nodes(graph) -> Set[int]:
    undirected = graph.to_undirected()
    return {node for node, degree in undirected.degree if degree <= 1}


def edge_key(u, v, k) -> str:
    return f"{u}:{v}:{k}"


def path_edge_keys(graph, nodes) -> Set[str]:
    keys = set()
    for u, v in zip(nodes[:-1], nodes[1:]):
        for key in graph[u][v]:
            keys.add(edge_key(u, v, key))
            break
    return keys
