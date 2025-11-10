#!/usr/bin/env python3

"""Simple MQTT sensor reading simulator.

Publishes synthetic temperature readings to the topics defined in the
site configuration file so the Temperature Control System can process them.
"""

import argparse
import json
import random
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, Iterable, List

try:
    import paho.mqtt.client as mqtt
except ImportError:  # pragma: no cover
    sys.stderr.write(
        "ERROR: The package 'paho-mqtt' is required. Install it with 'pip install paho-mqtt'\n"
    )
    sys.exit(1)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="MQTT sensor simulator")
    parser.add_argument(
        "--host", default="localhost", help="MQTT broker host (default: localhost)"
    )
    parser.add_argument(
        "--port", type=int, default=1883, help="MQTT broker port (default: 1883)"
    )
    parser.add_argument(
        "--config",
        type=Path,
        default=Path("config/site-config.json"),
        help="Path to site configuration JSON",
    )
    parser.add_argument(
        "--interval",
        type=float,
        default=5.0,
        help="Seconds between publishes per sensor (default: 5)",
    )
    parser.add_argument(
        "--count",
        type=int,
        default=0,
        help="Number of cycles to send (0 = infinite)",
    )
    parser.add_argument(
        "--jitter",
        type=float,
        default=0.5,
        help="Random +/- variation added to each temperature (default: 0.5°C)",
    )
    return parser.parse_args()


def load_rooms(config_path: Path) -> List[Dict[str, Any]]:
    if not config_path.exists():
        raise FileNotFoundError(f"Config file not found: {config_path}")

    with config_path.open("r", encoding="utf-8") as fh:
        data = json.load(fh)

    rooms = data.get("rooms", [])
    if not rooms:
        raise ValueError("Config file does not define any rooms")

    required_keys = {"sensorTopic", "desiredTemperature"}
    for room in rooms:
        if not required_keys.issubset(room.keys()):
            missing = required_keys - room.keys()
            raise ValueError(
                f"Room entry {room.get('id', '<unknown>')} is missing keys: {missing}"
            )

    return rooms


def iter_payloads(
    rooms: Iterable[Dict[str, Any]],
    jitter: float,
) -> Iterable[Dict[str, Any]]:
    for room in rooms:
        desired = float(room["desiredTemperature"])
        tolerance = float(room.get("temperatureTolerance", 1.0))
        base = random.uniform(desired - tolerance, desired + tolerance)
        value = base + random.uniform(-jitter, jitter)

        payload = {
            "sensor_id": room.get("sensorTopic", room.get("id")),
            "temperature": round(value, 2),
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }
        yield room["sensorTopic"], payload


def publish_cycle(
    client: mqtt.Client,
    rooms: List[Dict[str, Any]],
    jitter: float,
) -> None:
    for topic, payload in iter_payloads(rooms, jitter):
        message = json.dumps(payload)
        result = client.publish(topic, message, qos=1)
        if result.rc != mqtt.MQTT_ERR_SUCCESS:
            sys.stderr.write(
                f"Failed to publish to {topic}: {mqtt.error_string(result.rc)}\n"
            )
        else:
            print(f"[{datetime.now().isoformat()}] {topic} <- {message}")


def main() -> None:
    args = parse_args()
    rooms = load_rooms(args.config)

    client = mqtt.Client()
    try:
        client.connect(args.host, args.port, keepalive=60)
    except Exception as exc:  # pragma: no cover
        sys.stderr.write(f"ERROR: Could not connect to MQTT broker: {exc}\n")
        sys.exit(1)

    cycle = 0
    try:
        while True:
            cycle += 1
            publish_cycle(client, rooms, args.jitter)
            client.loop(timeout=1.0)

            if args.count and cycle >= args.count:
                break
            time.sleep(args.interval)
    except KeyboardInterrupt:
        print("Stopping simulator")
    finally:
        client.disconnect()


if __name__ == "__main__":
    main()
