-- =============================================================
-- FTGO Seed Data
-- All tables live in ONE database — the monolith's shared DB
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

-- Couriers
INSERT INTO courier (id, name, phone, available) VALUES (1, 'Ravi Kumar', '9998887771', true);
INSERT INTO courier (id, name, phone, available) VALUES (2, 'Priya Sharma', '9998887772', true);
INSERT INTO courier (id, name, phone, available) VALUES (3, 'Amit Patel', '9998887773', false);
