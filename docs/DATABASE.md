# Database notes

Flyway migrations are in `backend/src/main/resources/db/migration`. V1 creates the normalized core schema and role/permission seed data; V2 adds revocable bearer sessions and customer branch metadata. UUID primary keys, `timestamptz`, foreign keys, unique document identifiers, and `NUMERIC` money columns are used.

Inventory is represented by `(location_id, product_id)` balances and signed movement ledger rows. Application workflows insert movement rows and do not expose movement-edit operations. Central warehouse and branch locations share a table; transfers use an in-transit location. Sale, receipt, dispatch, and receipt transitions update balances and movements transactionally. Product prices are separate per currency; finalized sale lines snapshot SKU, name, unit, price, discount, and total. Purchase order lines snapshot product name and unit cost. Exchange-rate direction is quote-currency units per base-currency unit.
