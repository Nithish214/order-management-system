-- Descriptions and specs for the 57 hand-curated products (the original 7 from
-- V1/V5, plus the 50 from V14) -- NOT the 300 bulk-generated ones from V16, which have
-- no real per-product identity to write genuine content about (see Product entity's own
-- comment on why that's a permanent, not temporary, gap). ids 1-57 only.
--
-- specs is a flat JSON object (parsed via SpecsConverter into a Map<String, String>) --
-- 3-5 attributes chosen per product for what's actually relevant to THAT item, not a
-- fixed schema forced across every row (a book's Author/Format and a blender's
-- Capacity/Power have nothing in common).

UPDATE product SET
  description = 'A reliable everyday laptop built for work, browsing, and light creative tasks. Fast boot times and a crisp display make it comfortable for long sessions, while the aluminum chassis keeps it light enough to carry all day.',
  specs = '{"Screen Size": "15.6 in", "Storage": "512GB SSD", "RAM": "16GB", "Battery Life": "Up to 10 hours", "Weight": "1.8 kg"}'
WHERE id = 1;

UPDATE product SET
  description = 'A comfortable wireless mouse with a smooth optical sensor and a shape designed for all-day use. Pairs instantly over a 2.4GHz USB receiver, so there''s no fiddling with Bluetooth settings.',
  specs = '{"Connectivity": "2.4GHz Wireless", "Battery Life": "Up to 12 months", "DPI": "1600", "Weight": "90g"}'
WHERE id = 2;

UPDATE product SET
  description = 'A full-size mechanical keyboard with tactile switches that give satisfying feedback on every keystroke. Backlit keys and a sturdy frame make it equally at home for typing marathons or late-night gaming.',
  specs = '{"Switch Type": "Tactile Mechanical", "Backlight": "RGB", "Layout": "Full-size", "Connectivity": "USB-C (wired)"}'
WHERE id = 3;

UPDATE product SET
  description = 'A spacious 27-inch display that gives you the room to run two windows side by side without squinting. The IPS panel keeps colors accurate whether you''re editing photos or just catching up on email.',
  specs = '{"Screen Size": "27 in", "Resolution": "2560x1440 (QHD)", "Panel Type": "IPS", "Refresh Rate": "75Hz"}'
WHERE id = 4;

UPDATE product SET
  description = 'Sharp, well-lit video for calls and streams, with a built-in microphone that picks up your voice clearly without a separate headset. Clips onto almost any monitor in seconds.',
  specs = '{"Resolution": "1080p", "Field of View": "90 degrees", "Microphone": "Built-in", "Connectivity": "USB-A"}'
WHERE id = 5;

UPDATE product SET
  description = 'Over-ear comfort meets clear call audio, with a noise-reducing boom mic that filters out background chatter. The 20-hour battery easily covers a full workday of back-to-back meetings.',
  specs = '{"Connectivity": "Bluetooth 5.0", "Battery Life": "Up to 20 hours", "Microphone": "Noise-cancelling boom mic", "Weight": "210g"}'
WHERE id = 6;

UPDATE product SET
  description = 'Turns one USB-C port into the four or five you actually need, adding HDMI output, extra USB-A ports, and pass-through charging in a compact aluminum body that fits easily in a laptop bag.',
  specs = '{"Ports": "3x USB-A, 1x HDMI, 1x USB-C PD", "Max Video Output": "4K@30Hz", "Material": "Aluminum"}'
WHERE id = 7;

UPDATE product SET
  description = 'A pocketable speaker that punches above its size, with clear mids and enough bass for a small room or backyard gathering. The rubberized shell shrugs off the occasional splash.',
  specs = '{"Connectivity": "Bluetooth 5.0", "Battery Life": "Up to 10 hours", "Water Resistance": "IPX5", "Weight": "380g"}'
WHERE id = 8;

UPDATE product SET
  description = 'Tracks steps, heart rate, and sleep without needing to be charged every night, and keeps notifications on your wrist so your phone can stay in your pocket. The display stays readable even in direct sunlight.',
  specs = '{"Display": "1.4 in AMOLED", "Battery Life": "Up to 7 days", "Water Resistance": "5 ATM", "Compatibility": "iOS & Android"}'
WHERE id = 9;

UPDATE product SET
  description = 'Enough capacity for two full phone charges or one tablet top-up, in a body small enough to forget it''s in your bag. Dual outputs mean you and a friend can charge at the same time.',
  specs = '{"Capacity": "10000mAh", "Output Ports": "2x USB-A", "Input": "USB-C", "Weight": "210g"}'
WHERE id = 10;

UPDATE product SET
  description = 'Active noise cancellation quiets down flights, open offices, and busy streets, while the compact charging case adds a couple of extra charges on the go. Touch controls handle calls and skips without reaching for your phone.',
  specs = '{"Connectivity": "Bluetooth 5.2", "Battery Life": "6h (24h with case)", "Noise Cancellation": "Active (ANC)", "Water Resistance": "IPX4"}'
WHERE id = 11;

UPDATE product SET
  description = 'Plugs straight into an HDMI port and turns any TV into a smart one, streaming in crisp 4K with support for every major app. The included remote also controls TV power and volume.',
  specs = '{"Output": "4K HDR", "Connectivity": "Wi-Fi 6", "Storage": "8GB", "Remote": "Voice-enabled"}'
WHERE id = 12;

UPDATE product SET
  description = 'An extended-size mat that gives a mouse plenty of room to roam, with a smooth woven surface for precise tracking and a stitched edge that won''t fray after months of use.',
  specs = '{"Size": "900x400mm", "Surface": "Woven Cloth", "Base": "Non-slip Rubber", "Thickness": "4mm"}'
WHERE id = 13;

UPDATE product SET
  description = 'Powerful enough to crush ice for smoothies but quiet enough not to wake the house up early. The stainless steel blades and shatter-resistant jar are built to handle daily use.',
  specs = '{"Capacity": "1.5L", "Power": "600W", "Speed Settings": "3 + Pulse", "Material": "Stainless Steel & BPA-free Plastic"}'
WHERE id = 14;

UPDATE product SET
  description = 'Three pans in the sizes you''ll actually reach for, coated for easy release and easy cleanup. Even heat distribution means fewer hot spots and less babysitting at the stove.',
  specs = '{"Set Size": "3 pans (20/24/28cm)", "Coating": "Non-Stick Ceramic", "Compatible Hobs": "Gas, Electric, Induction", "Oven-Safe": "Up to 180 degrees C"}'
WHERE id = 15;

UPDATE product SET
  description = 'Boils a full liter in under three minutes, with an auto shut-off that means you can walk away without worrying. The wide spout makes pouring into mugs or teapots genuinely easy.',
  specs = '{"Capacity": "1.7L", "Power": "2200W", "Material": "Stainless Steel", "Auto Shut-Off": "Yes"}'
WHERE id = 16;

UPDATE product SET
  description = 'Contours to your head and neck instead of fighting against them, easing the pressure points that keep you tossing and turning. The cover unzips for an easy wash.',
  specs = '{"Fill": "Memory Foam", "Size": "Standard (60x40cm)", "Cover": "Removable, Washable", "Firmness": "Medium"}'
WHERE id = 17;

UPDATE product SET
  description = 'A set of four mugs with a comfortable handle and a glaze that keeps drinks looking as good as they taste. Microwave and dishwasher safe, so they''re as practical as they are nice to look at.',
  specs = '{"Set Size": "4 mugs", "Capacity": "350ml each", "Material": "Ceramic", "Care": "Dishwasher & Microwave Safe"}'
WHERE id = 18;

UPDATE product SET
  description = 'Maps your floor plan and cleans room by room while you''re out, then finds its own way back to the dock to recharge. Handles carpet and hard floors without needing to be told which is which.',
  specs = '{"Suction Power": "2500Pa", "Battery Life": "Up to 120 min", "Navigation": "LiDAR Mapping", "Compatibility": "App & Voice Control"}'
WHERE id = 19;

UPDATE product SET
  description = 'Gets that crispy, fried texture using a fraction of the oil, and preheats fast enough to fit into a weeknight routine. The basket lifts out in one piece for easy cleaning.',
  specs = '{"Capacity": "5.5L", "Power": "1700W", "Temperature Range": "80-200 degrees C", "Presets": "8"}'
WHERE id = 20;

UPDATE product SET
  description = 'Enough cushioning to protect your knees during floor work, with a textured surface that stays grippy even through a sweaty session. Rolls up small enough to toss in a gym bag.',
  specs = '{"Thickness": "6mm", "Material": "TPE (eco-friendly)", "Length": "183cm", "Non-Slip Surface": "Yes"}'
WHERE id = 21;

UPDATE product SET
  description = 'Replaces a whole rack of dumbbells with one pair that dials up or down in seconds, ideal for a home setup with limited space. The weight plates lock securely with no risk of coming loose mid-rep.',
  specs = '{"Weight Range": "5-25kg per dumbbell", "Adjustment": "Dial System", "Material": "Cast Iron & Steel", "Set Includes": "2 dumbbells"}'
WHERE id = 22;

UPDATE product SET
  description = 'Keeps cold drinks cold for a full day and hot drinks hot for around twelve hours, thanks to double-wall vacuum insulation. The wide mouth makes it easy to add ice or clean out afterward.',
  specs = '{"Capacity": "750ml", "Insulation": "Double-Wall Vacuum", "Material": "Stainless Steel", "Keeps Cold": "Up to 24 hours"}'
WHERE id = 23;

UPDATE product SET
  description = 'Pitches in about five minutes even for a first-timer, with a rainfly that holds up through a genuinely wet weekend. Two people and their gear fit comfortably without feeling cramped.',
  specs = '{"Capacity": "2 Person", "Weight": "2.3kg", "Waterproof Rating": "3000mm", "Setup Time": "About 5 minutes"}'
WHERE id = 24;

UPDATE product SET
  description = 'A cushioned midsole absorbs impact over long distances, while the breathable mesh upper keeps feet cool on warmer runs. Grippy outsole tread holds up on both pavement and light trails.',
  specs = '{"Upper Material": "Breathable Mesh", "Midsole": "Cushioned EVA Foam", "Closure": "Lace-up", "Best For": "Road Running"}'
WHERE id = 25;

UPDATE product SET
  description = 'Five resistance levels cover everything from warm-ups to serious strength work, all packed into a set small enough to travel with. A door anchor and handles are included for a full range of exercises.',
  specs = '{"Set Size": "5 bands", "Resistance Levels": "10-50 lbs", "Includes": "Door Anchor, Handles, Ankle Straps", "Material": "Natural Latex"}'
WHERE id = 26;

UPDATE product SET
  description = 'A practical guide to writing code that other developers -- including future you -- will actually want to read. Covers naming, structure, and the small habits that separate maintainable codebases from tangled ones.',
  specs = '{"Author": "Christian Mayer", "Format": "Paperback", "Pages": "240", "Genre": "Software Engineering"}'
WHERE id = 27;

UPDATE product SET
  description = 'A clear, practical framework for building good habits and breaking bad ones, built on the idea that tiny, consistent changes compound into remarkable results over time. One of the most recommended habit-building books for a reason.',
  specs = '{"Author": "James Clear", "Format": "Paperback", "Pages": "320", "Genre": "Self-Help"}'
WHERE id = 28;

UPDATE product SET
  description = 'Stephen Hawking''s landmark explanation of the universe''s biggest questions -- black holes, the Big Bang, the nature of time itself -- written for readers with no physics background at all.',
  specs = '{"Author": "Stephen Hawking", "Format": "Paperback", "Pages": "256", "Genre": "Popular Science"}'
WHERE id = 29;

UPDATE product SET
  description = 'A career-spanning collection of practical advice for software developers, from debugging techniques to career habits, still referenced decades after its first edition. Dense with ideas you''ll come back to more than once.',
  specs = '{"Author": "David Thomas & Andrew Hunt", "Format": "Paperback", "Pages": "352", "Genre": "Software Engineering"}'
WHERE id = 30;

UPDATE product SET
  description = 'A sweeping look at how Homo sapiens went from an unremarkable ape to the dominant species on the planet, tracing the myths, money, and empires that shaped human history along the way.',
  specs = '{"Author": "Yuval Noah Harari", "Format": "Paperback", "Pages": "464", "Genre": "History"}'
WHERE id = 31;

UPDATE product SET
  description = 'F. Scott Fitzgerald''s classic portrait of wealth, longing, and the American Dream in the Jazz Age, told through the eyes of a narrator drawn into his mysterious neighbor''s world.',
  specs = '{"Author": "F. Scott Fitzgerald", "Format": "Paperback", "Pages": "180", "Genre": "Classic Fiction"}'
WHERE id = 32;

UPDATE product SET
  description = 'Makes the case that the ability to focus without distraction is becoming rare and valuable, and lays out concrete strategies for reclaiming that kind of focus in a noisy, notification-filled world.',
  specs = '{"Author": "Cal Newport", "Format": "Paperback", "Pages": "304", "Genre": "Productivity"}'
WHERE id = 33;

UPDATE product SET
  description = 'A wardrobe staple that layers easily over a t-shirt or hoodie, cut with a slim fit that doesn''t feel bulky. The denim softens nicely with wear, so it only gets more comfortable over time.',
  specs = '{"Material": "100% Cotton Denim", "Fit": "Slim", "Closure": "Button-front", "Care": "Machine Washable"}'
WHERE id = 34;

UPDATE product SET
  description = 'A tailored coat warm enough for genuine winter weather without feeling stiff or heavy. The wool blend holds its shape wear after wear, and the fit works equally well over a sweater or a blazer.',
  specs = '{"Material": "Wool Blend (70% Wool)", "Fit": "Tailored", "Lining": "Polyester", "Closure": "Button-front"}'
WHERE id = 35;

UPDATE product SET
  description = 'Compact enough for daily essentials but roomy enough for a phone, wallet, and more, with an adjustable strap that works cross-body or over the shoulder. Genuine leather that develops a nice patina over time.',
  specs = '{"Material": "Genuine Leather", "Strap": "Adjustable", "Closure": "Zip", "Dimensions": "22x16x7cm"}'
WHERE id = 36;

UPDATE product SET
  description = 'The timeless teardrop shape that''s never really gone out of style, with polarized lenses that cut glare on bright days. Lightweight metal frames sit comfortably for hours.',
  specs = '{"Lens Type": "Polarized", "UV Protection": "UV400", "Frame Material": "Metal", "Style": "Aviator"}'
WHERE id = 37;

UPDATE product SET
  description = 'A soft, breathable everyday tee that holds its shape wash after wash. The classic crew neck cut works equally well on its own or layered under something warmer.',
  specs = '{"Material": "100% Cotton", "Fit": "Regular", "Neckline": "Crew Neck", "Care": "Machine Washable"}'
WHERE id = 38;

UPDATE product SET
  description = 'A genuine leather belt with a classic buckle that goes with almost anything in the closet, from jeans to dress trousers. Built to last well beyond a season.',
  specs = '{"Material": "Genuine Leather", "Buckle": "Metal", "Width": "3.5cm", "Style": "Classic"}'
WHERE id = 39;

UPDATE product SET
  description = 'A snug, ribbed knit that keeps ears warm on cold mornings without looking bulky under a jacket hood. One size that stretches to fit comfortably.',
  specs = '{"Material": "Acrylic Knit", "Fit": "One Size", "Style": "Ribbed", "Care": "Hand Wash"}'
WHERE id = 40;

UPDATE product SET
  description = 'Five hundred compatible blocks in a mix of colors and shapes, enough to build anything from a simple tower to an elaborate imagined city. Open-ended play that holds a kid''s attention far longer than a single-build kit.',
  specs = '{"Pieces": "500", "Age Range": "6+", "Material": "ABS Plastic", "Compatible With": "Standard building brick systems"}'
WHERE id = 41;

UPDATE product SET
  description = 'Quick enough to actually be exciting, with rugged tires that handle grass and gravel as easily as pavement. The rechargeable battery means it''s ready to go again after a short charge, not a trip to buy more batteries.',
  specs = '{"Speed": "Up to 20 km/h", "Battery": "Rechargeable Li-ion", "Range": "50m", "Age Range": "8+"}'
WHERE id = 42;

UPDATE product SET
  description = 'A strategy board game that rewards planning ahead over luck, with enough depth to stay interesting after a dozen plays. Games typically run 45-60 minutes, making it a solid pick for a game night.',
  specs = '{"Players": "2-4", "Play Time": "45-60 min", "Age Range": "10+", "Category": "Strategy"}'
WHERE id = 43;

UPDATE product SET
  description = 'A satisfying weekend project with a finished image detailed enough to reward close attention. Thick, well-cut pieces fit snugly without being frustratingly tight.',
  specs = '{"Pieces": "1000", "Finished Size": "68x48cm", "Material": "Recycled Cardboard", "Age Range": "12+"}'
WHERE id = 44;

UPDATE product SET
  description = 'Soft, huggable, and sized just right for bedtime -- the kind of toy that ends up being a genuine favorite rather than one more thing in the toy box. Machine washable for when it inevitably needs a clean.',
  specs = '{"Height": "30cm", "Material": "Polyester Plush", "Filling": "Hypoallergenic", "Care": "Machine Washable"}'
WHERE id = 45;

UPDATE product SET
  description = 'A standard 52-card deck with a smooth, durable finish that holds up to shuffling far better than the cheap ones. Fits in a pocket for game nights, road trips, or a quick hand of solitaire.',
  specs = '{"Card Count": "52 + Jokers", "Material": "Plastic-Coated Paper", "Finish": "Linen", "Case": "Included"}'
WHERE id = 46;

UPDATE product SET
  description = 'A lightweight serum formulated to brighten dull skin and even out tone with daily use, absorbing quickly without leaving a sticky residue. A little goes a long way in a morning routine.',
  specs = '{"Volume": "30ml", "Key Ingredient": "15% Vitamin C", "Skin Type": "All Skin Types", "Usage": "Daily, AM"}'
WHERE id = 47;

UPDATE product SET
  description = 'A fragrance-free moisturizer that locks in hydration without feeling heavy or greasy, gentle enough for daily use on sensitive skin. Works well as a base under makeup or on its own.',
  specs = '{"Volume": "50ml", "Key Ingredient": "Hyaluronic Acid", "Skin Type": "All Skin Types", "Fragrance": "Free"}'
WHERE id = 48;

UPDATE product SET
  description = 'Cuts drying time noticeably compared to a basic dryer, with multiple heat and speed settings for different hair types. A cool-shot button locks in your style once you''re done.',
  specs = '{"Power": "2200W", "Settings": "3 Heat / 2 Speed", "Attachments": "Concentrator, Diffuser", "Cool Shot": "Yes"}'
WHERE id = 49;

UPDATE product SET
  description = 'A complete set covering foundation, powder, blending, and precision detail work, with soft synthetic bristles that don''t shed. Comes with a case that keeps everything organized in a bag or on a counter.',
  specs = '{"Set Size": "12 brushes", "Bristle Type": "Synthetic", "Case": "Included", "Cruelty-Free": "Yes"}'
WHERE id = 50;

UPDATE product SET
  description = 'Six versatile shades that go from everyday neutral to a bold statement color, in a quick-dry formula that holds up well without chipping after a day or two.',
  specs = '{"Set Size": "6 bottles", "Volume": "12ml each", "Finish": "Glossy", "Formula": "Quick-Dry"}'
WHERE id = 51;

UPDATE product SET
  description = 'Broad-spectrum protection that blends in without the heavy white cast some sunscreens leave behind, light enough to wear under makeup every day, not just at the beach.',
  specs = '{"SPF": "50", "Volume": "100ml", "Coverage": "Broad Spectrum (UVA/UVB)", "Water Resistance": "80 minutes"}'
WHERE id = 52;

UPDATE product SET
  description = 'Built for full workdays, with lumbar support and adjustable armrests that reduce the strain of sitting for hours. The mesh back stays breathable even during long stretches at the desk.',
  specs = '{"Material": "Mesh Back, Fabric Seat", "Adjustability": "Height, Armrests, Recline", "Weight Capacity": "120kg", "Warranty": "2 Years"}'
WHERE id = 53;

UPDATE product SET
  description = 'Switches between sitting and standing at the push of a button, with a sturdy frame that doesn''t wobble even at full height. A memory function saves your preferred positions so you''re not re-adjusting every time.',
  specs = '{"Height Range": "71-121cm", "Motor": "Dual Motor Electric", "Weight Capacity": "80kg", "Desktop Size": "140x70cm"}'
WHERE id = 54;

UPDATE product SET
  description = 'Keeps pens, notes, and small supplies out of a pile and within easy reach, with enough compartments to actually stay organized rather than becoming one more junk drawer. Fits neatly in a desk corner.',
  specs = '{"Material": "Bamboo & Mesh Metal", "Compartments": "6", "Dimensions": "25x15x12cm", "Assembly": "Required"}'
WHERE id = 55;

UPDATE product SET
  description = 'Advances slides from across the room without needing to hover near the laptop, with a built-in laser pointer for highlighting key points. The USB receiver stores inside the remote so it''s never misplaced.',
  specs = '{"Range": "Up to 30m", "Connectivity": "2.4GHz USB Receiver", "Battery": "AAA (included)", "Laser Pointer": "Yes"}'
WHERE id = 56;

UPDATE product SET
  description = 'Adjustable brightness and color temperature mean it works equally well for late-night reading or focused work sessions, without the flicker or harsh glare of a cheap lamp. The flexible arm points light exactly where it''s needed.',
  specs = '{"Brightness Levels": "5", "Color Temperature": "3000K-6500K", "Power": "USB-C", "Arm": "Adjustable/Foldable"}'
WHERE id = 57;
