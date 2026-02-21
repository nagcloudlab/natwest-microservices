-- =============================================================
-- Delivery Service Seed Data (Iteration 5)
--
-- Courier data moved here from the monolith.
-- The delivery-service now owns all courier and delivery data.
-- =============================================================

-- Couriers
INSERT INTO courier (id, name, phone, available) VALUES (1, 'Ravi Kumar', '9998887771', true);
INSERT INTO courier (id, name, phone, available) VALUES (2, 'Priya Sharma', '9998887772', true);
INSERT INTO courier (id, name, phone, available) VALUES (3, 'Amit Patel', '9998887773', false);
