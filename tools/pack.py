#!/usr/bin/env python3
"""Make one family's copy of the app: write which agent to talk to into the APK.

The pairing goes into the APK Signing Block, whose ID-value pairs the APK
signature does not cover, so the APK is not re-signed and no signing key is
needed. The app reads the entry on first launch (app/.../Pairing.kt).

    python3 tools/pack.py whatcha.apk whatcha-family.apk --agent 0xAGENT --invite CODE

The invite code must also be one the agent accepts (its trust policy's
onboard.invite_code, e.g. CO_INVITE_CODE).
"""
import argparse
import json
import struct
from pathlib import Path

PAIRING_BLOCK_ID = 0x77686174  # ASCII "what"; must match PAIRING_BLOCK_ID in Pairing.kt
MAGIC = b"APK Sig Block 42"


def pack(apk: bytes, value: bytes) -> bytes:
    eocd = apk.rfind(b"PK\x05\x06")
    cd = struct.unpack_from("<I", apk, eocd + 16)[0]
    if apk[cd - 16:cd] != MAGIC:
        raise SystemExit("this APK has no v2 signing block; sign it with apksigner first")
    size = struct.unpack_from("<Q", apk, cd - 24)[0]
    start = cd - size - 8

    pairs = [p for p in read_pairs(apk[start + 8:cd - 24]) if p[0] != PAIRING_BLOCK_ID]
    pairs.append((PAIRING_BLOCK_ID, value))
    body = b"".join(struct.pack("<QI", len(v) + 4, i) + v for i, v in pairs)
    new_size = len(body) + 24
    block = struct.pack("<Q", new_size) + body + struct.pack("<Q", new_size) + MAGIC

    new_cd = start + len(block)
    return apk[:start] + block + apk[cd:eocd + 16] + struct.pack("<I", new_cd) + apk[eocd + 20:]


def read_pairs(data: bytes):
    pos = 0
    while pos < len(data):
        length, pair_id = struct.unpack_from("<QI", data, pos)
        yield pair_id, data[pos + 12:pos + 8 + length]
        pos += 8 + length


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("apk", type=Path)
    parser.add_argument("out", type=Path)
    parser.add_argument("--agent", required=True, help="the agent's 0x address")
    parser.add_argument("--invite", help="invite code the agent accepts")
    parser.add_argument("--relay", default="wss://oo.openonion.ai")
    args = parser.parse_args()

    pairing = {"agent": args.agent, "relay": args.relay}
    if args.invite:
        pairing["invite"] = args.invite
    value = json.dumps(pairing, separators=(",", ":")).encode()
    args.out.write_bytes(pack(args.apk.read_bytes(), value))
    print(f"wrote {args.out} for agent {args.agent}")


if __name__ == "__main__":
    main()
