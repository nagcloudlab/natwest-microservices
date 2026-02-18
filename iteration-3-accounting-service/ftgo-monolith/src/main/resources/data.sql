-- =============================================================
-- FTGO Seed Data (Iteration 3 — Restaurant, Notification, Accounting Extracted)
--
-- Restaurant and menu_item data → Restaurant Service (port 8081)
-- Notification data → Notification Service (port 8082)
-- Payment data → Accounting Service (port 8083)
-- Only Kitchen/Delivery/Order data remains here.
-- =============================================================

-- Couriers
INSERT INTO courier (id, name, phone, available) VALUES (1, 'Ravi Kumar', '9998887771', true);
INSERT INTO courier (id, name, phone, available) VALUES (2, 'Priya Sharma', '9998887772', true);
INSERT INTO courier (id, name, phone, available) VALUES (3, 'Amit Patel', '9998887773', false);
