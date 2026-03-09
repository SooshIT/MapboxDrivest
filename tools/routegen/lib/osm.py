"""OSM graph loading."""

from __future__ import annotations

from pathlib import Path
from typing import Dict

import osmnx as ox

from .routing import edge_speed_kph, generation_edge_speed_kph, learner_edge_speed_kph, travel_time_seconds


def _add_travel_time(graph, cfg: Dict | None = None) -> None:
    cfg = cfg or {}
    junction_delay_s = float(cfg.get("learner_junction_delay_seconds", 1.75))
    major_junction_degree = int(cfg.get("learner_major_junction_degree", 4))
    for u, v, key, data in graph.edges(keys=True, data=True):
        length_m = data.get("length", 0.0) or 0.0
        routing_speed_kph = edge_speed_kph(data.get("highway"))
        generation_speed_kph = generation_edge_speed_kph(data.get("highway"), cfg)
        learner_speed_kph = learner_edge_speed_kph(data.get("highway"), cfg)
        delay_s = 0.0
        if graph.out_degree(u) >= major_junction_degree:
            delay_s += junction_delay_s
        if graph.out_degree(v) >= major_junction_degree:
            delay_s += junction_delay_s * 0.5
        data["routing_speed_kph"] = routing_speed_kph
        data["routing_time"] = travel_time_seconds(length_m, routing_speed_kph)
        data["generation_speed_kph"] = generation_speed_kph
        data["speed_kph"] = learner_speed_kph
        data["travel_time"] = travel_time_seconds(length_m, generation_speed_kph)
        data["estimated_travel_time"] = travel_time_seconds(length_m, learner_speed_kph) + delay_s


def load_graph(
    centre_lat: float,
    centre_lng: float,
    radius_meters: float,
    osm_dir: Path,
    use_overpass: bool,
    logger,
    cfg: Dict | None = None,
):
    delta = radius_meters / 111000.0
    north = centre_lat + delta
    south = centre_lat - delta
    east = centre_lng + delta
    west = centre_lng - delta

    graphml = osm_dir / "extract.graphml"
    osm_xml = osm_dir / "extract.osm"
    osm_pbf = osm_dir / "extract.osm.pbf"

    if graphml.exists():
        logger.info(f"Loading graph from {graphml}")
        graph = ox.load_graphml(graphml)
    elif osm_pbf.exists():
        try:
            from pyrosm import OSM  # type: ignore
        except ImportError as exc:
            if not osm_xml.exists():
                raise RuntimeError("pyrosm required to read .osm.pbf extracts") from exc
            logger.warn("pyrosm unavailable, falling back to extract.osm")
            logger.info(f"Loading graph from {osm_xml}")
            graph = ox.graph_from_xml(osm_xml, simplify=True, bidirectional=False, retain_all=True)
        else:
            logger.info(f"Loading graph from {osm_pbf} with bounding box crop")
            osm = OSM(str(osm_pbf), bounding_box=(west, south, east, north))
            net = osm.get_network(network_type="driving")
            graph = osm.to_graph(net, nodes=False, edges=False)
    elif osm_xml.exists():
        logger.info(f"Loading graph from {osm_xml}")
        graph = ox.graph_from_xml(osm_xml, simplify=True, bidirectional=False, retain_all=True)
    elif use_overpass:
        logger.info("Downloading graph via Overpass API")
        try:
            # osmnx >=2.0 signature
            graph = ox.graph_from_bbox(
                bbox=(north, south, east, west),
                network_type="drive",
                simplify=True,
                retain_all=True,
            )
        except TypeError:
            # osmnx <2.0 signature
            graph = ox.graph_from_bbox(
                north=north,
                south=south,
                east=east,
                west=west,
                network_type="drive",
                simplify=True,
                retain_all=True,
            )
    else:
        raise FileNotFoundError(
            "No local OSM extract found. Add extract.graphml, extract.osm, or extract.osm.pbf "
            "under tools/routegen/inputs/osm/, or use --use-overpass."
        )

    try:
        graph = ox.truncate.largest_component(graph, strongly=False)
    except AttributeError:
        graph = ox.utils_graph.get_largest_component(graph, strongly=False)
    _add_travel_time(graph, cfg)
    return graph
