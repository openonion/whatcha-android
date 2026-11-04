# Fix the stock count in Lantern

## Summary
Fixed the bug in Lantern where the stock count went wrong after a return was scanned twice. Wrote a test
that scans the same return twice and checks the count stays right, then changed the scanner to remember
returns it has already seen. Sent the fix to Wen Qiu; she tried it in the shop and said thanks, the
numbers match the shelves again.

## Facts
- Date: 2026-11-04
- Project: projects/lantern.md
- Client: people/wen-qiu.md
- Files: lantern/scanner.py, lantern/tests/test_returns.py
- Sessions today: 1

## Decisions
- A return scanned twice counts once; the scanner keeps the ids it has seen for the day.

## Follow-ups
- Ask Wen Qiu whether the morning count should run before or after deliveries.

## What you typed
### 09:12
> The stock count in Lantern is off again after returns. Find why and fix it with a test.
### 11:40
> Send Wen Qiu the fix and ask her to try it on the shop machine.

## Final reply
The double-scan bug is fixed and tested; Wen Qiu confirmed the count matches the shelves.
