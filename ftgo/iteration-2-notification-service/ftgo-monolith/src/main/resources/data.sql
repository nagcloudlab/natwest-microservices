-- =============================================================
-- FTGO Seed Data (Iteration 1 — Restaurant Extracted)
--
-- Restaurant and menu_item data has been REMOVED from here.
-- That data now lives in the Restaurant Service (port 8081).
-- This is "Database per Service" in action!
-- =============================================================

-- Couriers
INSERT INTO courier (id, name, phone, available) VALUES (1, 'Ravi Kumar', '9998887771', true);
INSERT INTO courier (id, name, phone, available) VALUES (2, 'Priya Sharma', '9998887772', true);
INSERT INTO courier (id, name, phone, available) VALUES (3, 'Amit Patel', '9998887773', false);
