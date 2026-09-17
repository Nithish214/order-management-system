-- One placeholder image per new product (V14) -- the actual product page/list expects
-- product.images to be non-empty everywhere it renders a thumbnail (see ProductsPage.jsx
-- and ProductDetailPage.jsx's placeholder-vs-real-image branches), so leaving these 50
-- with zero images would just show the generic placeholder box, not a genuine broken
-- state, but a real photo/URL is more honest seed data than an empty array.
--
-- These point at an external placeholder image service (placehold.co), not this
-- project's own S3 bucket -- generating 50 real product photos is out of scope for seed
-- data, and this project's actual image-upload pipeline (ProductImageUploadService,
-- presigned S3 URLs) is exactly how a real image would be added later; these rows exist
-- so the storefront doesn't look broken in the meantime, not to bypass that pipeline.
-- Colors match this project's own design tokens (--color-border/--color-text from
-- tokens.css) rather than the placeholder service's own default styling.
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 8, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Bluetooth%20Speaker');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 9, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Smartwatch');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 10, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Portable%20Charger%2010000mAh');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 11, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Noise-Cancelling%20Earbuds');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 12, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=4K%20Streaming%20Stick');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 13, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Gaming%20Mouse%20Pad');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 14, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Stainless%20Steel%20Blender');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 15, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Non-Stick%20Frying%20Pan%20Set');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 16, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Electric%20Kettle');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 17, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Memory%20Foam%20Pillow');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 18, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Ceramic%20Coffee%20Mug%20Set');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 19, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Robot%20Vacuum%20Cleaner');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 20, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Air%20Fryer');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 21, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Yoga%20Mat');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 22, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Adjustable%20Dumbbell%20Set');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 23, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Insulated%20Water%20Bottle');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 24, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Camping%20Tent%202-Person');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 25, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Running%20Shoes');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 26, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Resistance%20Bands%20Set');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 27, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=The%20Art%20of%20Clean%20Code');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 28, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Atomic%20Habits');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 29, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=A%20Brief%20History%20of%20Time');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 30, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=The%20Pragmatic%20Programmer');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 31, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Sapiens%3A%20A%20Brief%20History%20of%20Humankind');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 32, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=The%20Great%20Gatsby');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 33, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Deep%20Work');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 34, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Men%27s%20Slim%20Fit%20Denim%20Jacket');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 35, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Women%27s%20Wool%20Blend%20Coat');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 36, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Leather%20Crossbody%20Bag');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 37, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Classic%20Aviator%20Sunglasses');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 38, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Cotton%20Crew%20Neck%20T-Shirt');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 39, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Leather%20Belt');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 40, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Knit%20Beanie%20Hat');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 41, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Building%20Blocks%20Set%20500pc');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 42, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Remote%20Control%20Car');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 43, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Board%20Game%3A%20Strategy%20Quest');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 44, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Jigsaw%20Puzzle%201000-Piece');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 45, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Plush%20Teddy%20Bear');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 46, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Deck%20of%20Playing%20Cards');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 47, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Vitamin%20C%20Facial%20Serum');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 48, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Hydrating%20Face%20Moisturizer');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 49, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Electric%20Hair%20Dryer');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 50, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Makeup%20Brush%20Set');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 51, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Nail%20Polish%20Set%206-Pack');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 52, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Sunscreen%20SPF%2050');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 53, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Ergonomic%20Office%20Chair');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 54, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Adjustable%20Standing%20Desk');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 55, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Desk%20Organizer%20Set');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 56, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=Wireless%20Presenter%20Remote');
INSERT INTO product_image (id, product_id, image_url) VALUES (nextval('product_image_seq'), 57, 'https://placehold.co/400x400/e2e4ea/1a1d29?text=LED%20Desk%20Lamp');
