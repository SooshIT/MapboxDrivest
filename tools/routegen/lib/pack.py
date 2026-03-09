"""Output packing for route assets."""

from __future__ import annotations

from datetime import datetime
from typing import Dict, List


def build_routes_pack(centre: Dict, routes: List[Dict]) -> Dict:
    return {
        "metadata": {
            "generatedAt": datetime.utcnow().isoformat() + "Z",
            "centreId": centre["centre_id"],
            "centreName": centre["centre_name"],
        },
        "routes": routes,
    }
