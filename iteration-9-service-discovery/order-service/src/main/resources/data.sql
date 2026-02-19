-- =============================================================
-- FTGO Seed Data (Iteration 5 — All modules extracted!)
--
-- Restaurant and menu_item data → Restaurant Service (port 8081)
-- Notification data → Notification Service (port 8082)
-- Payment data → Accounting Service (port 8083)
-- Kitchen ticket data → Kitchen Service (port 8084)
-- Delivery and courier data → Delivery Service (port 8085)
--
-- The monolith now contains ONLY Order data.
-- No seed data needed — orders are created at runtime.
-- =============================================================

-- No-op: Spring requires at least one statement in data.sql
SELECT 1;
