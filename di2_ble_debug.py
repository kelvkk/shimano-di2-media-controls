#!/usr/bin/env python3
"""Di2 BLE Debug Tool — scan, connect, and log all NOTIFY characteristic data."""

import asyncio
import sys
from datetime import datetime

from bleak import BleakClient, BleakScanner

SHIMANO_SERVICE_UUID = "000018ff-5348-494d-414e-4f5f424c4500"

NOTIFY_CHAR_UUIDS = [
    "00002af9-5348-494d-414e-4f5f424c4500",
    "00002afb-5348-494d-414e-4f5f424c4500",
    "00002afd-5348-494d-414e-4f5f424c4500",
]


def format_bytes(data: bytes) -> str:
    hex_str = data.hex(" ")
    dec_str = " ".join(str(b) for b in data)
    return f"hex=[{hex_str}] dec=[{dec_str}]"


def short_uuid(uuid: str) -> str:
    return uuid.split("-")[0].lstrip("0") or "0"


def notification_handler(char_uuid: str):
    def handler(_sender, data: bytes):
        ts = datetime.now().strftime("%H:%M:%S.%f")[:-3]
        print(f"[{ts}] {short_uuid(char_uuid)}: {format_bytes(data)}")

    return handler


async def scan_for_di2() -> str | None:
    print("Scanning for Shimano Di2 devices...")
    devices = await BleakScanner.discover(timeout=10, return_adv=True)

    shimano_devices = []
    for device, adv in devices.values():
        name = adv.local_name or device.name or ""
        services = adv.service_uuids or []
        if "shimano" in name.lower() or SHIMANO_SERVICE_UUID in services:
            shimano_devices.append((device, adv))
            print(f"  Found: {name} [{device.address}] RSSI={adv.rssi}")

    if not shimano_devices:
        print("\nNo Shimano devices found. Make sure:")
        print("  1. Di2 EW-WU111 is powered on")
        print("  2. Bluetooth is enabled on this computer")
        print("  3. Device is in pairing mode (hold junction button)")
        return None

    if len(shimano_devices) == 1:
        return shimano_devices[0][0].address

    print("\nMultiple devices found. Enter the address to connect to:")
    return input("> ").strip()


async def connect_and_monitor(address: str):
    print(f"\nConnecting to {address}...")

    async with BleakClient(address) as client:
        print(f"Connected: {client.is_connected}")
        print("\nDiscovering services...")

        for service in client.services:
            print(f"\n  Service: {service.uuid}")
            for char in service.characteristics:
                props = ", ".join(char.properties)
                print(f"    Char: {short_uuid(char.uuid)} [{props}]")

        print("\nSubscribing to NOTIFY characteristics...")
        subscribed = []

        # Subscribe to known characteristics
        for uuid in NOTIFY_CHAR_UUIDS:
            try:
                await client.start_notify(uuid, notification_handler(uuid))
                subscribed.append(uuid)
                print(f"  Subscribed: {short_uuid(uuid)}")
            except Exception as e:
                print(f"  Failed {short_uuid(uuid)}: {e}")

        # Subscribe to ALL other notify/indicate characteristics across all services
        for service in client.services:
            for char in service.characteristics:
                if char.uuid in [u.lower() for u in NOTIFY_CHAR_UUIDS]:
                    continue
                if "notify" in char.properties or "indicate" in char.properties:
                    try:
                        await client.start_notify(
                            char.uuid, notification_handler(char.uuid)
                        )
                        subscribed.append(char.uuid)
                        print(f"  Subscribed (extra): {short_uuid(char.uuid)} [{', '.join(char.properties)}]")
                    except Exception as e:
                        print(f"  Failed {short_uuid(char.uuid)}: {e}")

        # Read all readable characteristics for initial state
        print("\nReading all readable characteristics...")
        for service in client.services:
            for char in service.characteristics:
                if "read" in char.properties:
                    try:
                        data = await client.read_gatt_char(char.uuid)
                        print(f"  {short_uuid(char.uuid)}: {format_bytes(data)}")
                    except Exception as e:
                        print(f"  {short_uuid(char.uuid)}: read failed: {e}")

        if not subscribed:
            print("No characteristics subscribed. Exiting.")
            return

        print(f"\nListening for notifications ({len(subscribed)} chars). Press Ctrl+C to stop.\n")
        print("-" * 70)

        try:
            while client.is_connected:
                await asyncio.sleep(1)
        except KeyboardInterrupt:
            pass

        print("\nDisconnecting...")


async def main():
    address = sys.argv[1] if len(sys.argv) > 1 else await scan_for_di2()
    if address:
        await connect_and_monitor(address)


if __name__ == "__main__":
    asyncio.run(main())
