# Lantern

## Facts
- Type: shop inventory app
- Client: Wen Qiu 文秋, 秋记小卖部 (people/wen-qiu.md)
- Started: 2026-09-15
- Stack: Python, SQLite, a barcode scanner in the shop

## What it is
An inventory app for Wen Qiu's shop: it counts stock as goods are scanned in and out, and prints a
morning count the shop compares with the shelves.

## Where it stands
- 2026-11-04: the stock-count bug after double-scanned returns is fixed; Wen Qiu tried it and said thanks.
- Invoice for the October work, $4,200, is overdue; Wen Qiu has not paid yet.
- Slept 4 hours before the fix, back pain from the chair all afternoon.
- Argued with co-founder Lin (林涛) about whether to keep taking shop clients.
- Next: the morning count timing, before or after deliveries.

## Key decisions
- One scanner id per return per day, so a double scan counts once.

## Open threads
- October invoice.
- Morning count timing.
