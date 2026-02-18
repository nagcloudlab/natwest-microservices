-- =============================================================
-- Restaurant Service Seed Data
-- This service OWNS restaurant and menu_item data.
-- No other service has these tables.
-- =============================================================

-- Restaurants
INSERT INTO restaurant (id, name, address, phone, is_open) VALUES (1, 'Mumbai Masala', '123 MG Road, Mumbai', '9876543210', true);
INSERT INTO restaurant (id, name, address, phone, is_open) VALUES (2, 'Delhi Darbar', '456 Connaught Place, Delhi', '9876543211', true);
INSERT INTO restaurant (id, name, address, phone, is_open) VALUES (3, 'Chennai Spice', '789 Anna Salai, Chennai', '9876543212', false);

-- Menu Items — Mumbai Masala
INSERT INTO menu_item (id, restaurant_id, name, description, price) VALUES (1, 1, 'Butter Chicken', 'Creamy tomato-based curry with tender chicken', 350.00);
INSERT INTO menu_item (id, restaurant_id, name, description, price) VALUES (2, 1, 'Paneer Tikka', 'Grilled cottage cheese marinated in spices', 280.00);
INSERT INTO menu_item (id, restaurant_id, name, description, price) VALUES (3, 1, 'Chicken Biryani', 'Hyderabadi style aromatic rice with chicken', 320.00);
INSERT INTO menu_item (id, restaurant_id, name, description, price) VALUES (4, 1, 'Garlic Naan', 'Fresh tandoori naan with garlic butter', 60.00);

-- Menu Items — Delhi Darbar
INSERT INTO menu_item (id, restaurant_id, name, description, price) VALUES (5, 2, 'Chole Bhature', 'Spiced chickpeas with fried bread', 180.00);
INSERT INTO menu_item (id, restaurant_id, name, description, price) VALUES (6, 2, 'Dal Makhani', 'Slow-cooked black lentils in creamy gravy', 220.00);
INSERT INTO menu_item (id, restaurant_id, name, description, price) VALUES (7, 2, 'Tandoori Chicken', 'Clay oven roasted chicken with spices', 400.00);

-- Menu Items — Chennai Spice (closed restaurant — for demo)
INSERT INTO menu_item (id, restaurant_id, name, description, price) VALUES (8, 3, 'Masala Dosa', 'Crispy crepe with spiced potato filling', 150.00);
INSERT INTO menu_item (id, restaurant_id, name, description, price) VALUES (9, 3, 'Idli Sambar', 'Steamed rice cakes with lentil soup', 120.00);

-- Reset auto-increment sequences past the seed data IDs
ALTER TABLE restaurant ALTER COLUMN id RESTART WITH 100;
ALTER TABLE menu_item ALTER COLUMN id RESTART WITH 100;
