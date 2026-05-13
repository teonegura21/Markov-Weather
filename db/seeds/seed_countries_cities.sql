-- Seed Romania-only data
-- 1 tara + 25 orase acoperind toate regiunile Romaniei

INSERT INTO countries (name, code) VALUES
('Romania', 'RO')
ON CONFLICT (name) DO NOTHING;

INSERT INTO cities (name, country_id, latitude, longitude, is_important) VALUES
-- Muntenia
('Bucuresti', (SELECT id FROM countries WHERE code = 'RO'), 44.4268, 26.1025, TRUE),
('Ploiesti', (SELECT id FROM countries WHERE code = 'RO'), 44.9367, 26.0129, TRUE),
('Pitesti', (SELECT id FROM countries WHERE code = 'RO'), 44.8565, 24.8692, TRUE),
('Targoviste', (SELECT id FROM countries WHERE code = 'RO'), 44.9244, 25.4565, FALSE),
('Giurgiu', (SELECT id FROM countries WHERE code = 'RO'), 43.9037, 25.9699, FALSE),

-- Transilvania
('Cluj-Napoca', (SELECT id FROM countries WHERE code = 'RO'), 46.7712, 23.6236, TRUE),
('Brasov', (SELECT id FROM countries WHERE code = 'RO'), 45.6427, 25.5887, TRUE),
('Sibiu', (SELECT id FROM countries WHERE code = 'RO'), 45.7936, 24.1213, TRUE),
('Targu Mures', (SELECT id FROM countries WHERE code = 'RO'), 46.5386, 24.5514, TRUE),
('Alba Iulia', (SELECT id FROM countries WHERE code = 'RO'), 46.0733, 23.5805, TRUE),
('Oradea', (SELECT id FROM countries WHERE code = 'RO'), 47.0465, 21.9189, TRUE),
('Satu Mare', (SELECT id FROM countries WHERE code = 'RO'), 47.7893, 22.8629, TRUE),
('Baia Mare', (SELECT id FROM countries WHERE code = 'RO'), 47.6567, 23.5850, TRUE),
('Bistrita', (SELECT id FROM countries WHERE code = 'RO'), 47.1332, 24.5001, TRUE),
('Sfantu Gheorghe', (SELECT id FROM countries WHERE code = 'RO'), 45.8681, 25.7996, FALSE),

-- Moldova
('Iasi', (SELECT id FROM countries WHERE code = 'RO'), 47.1585, 27.6014, TRUE),
('Bacau', (SELECT id FROM countries WHERE code = 'RO'), 46.5695, 26.9160, TRUE),
('Suceava', (SELECT id FROM countries WHERE code = 'RO'), 47.6635, 26.2732, TRUE),
('Galati', (SELECT id FROM countries WHERE code = 'RO'), 45.4353, 28.0080, TRUE),
('Botosani', (SELECT id FROM countries WHERE code = 'RO'), 47.7484, 26.6594, TRUE),

-- Dobrogea
('Constanta', (SELECT id FROM countries WHERE code = 'RO'), 44.1598, 28.6348, TRUE),
('Tulcea', (SELECT id FROM countries WHERE code = 'RO'), 45.1716, 28.7914, TRUE),

-- Oltenia
('Craiova', (SELECT id FROM countries WHERE code = 'RO'), 44.3193, 23.8006, TRUE),
('Drobeta-Turnu Severin', (SELECT id FROM countries WHERE code = 'RO'), 44.6319, 22.6562, TRUE),
('Targu Jiu', (SELECT id FROM countries WHERE code = 'RO'), 45.0382, 23.2749, TRUE),

-- Banat
('Timisoara', (SELECT id FROM countries WHERE code = 'RO'), 45.7489, 21.2087, TRUE),
('Resita', (SELECT id FROM countries WHERE code = 'RO'), 45.2970, 21.8857, TRUE),

-- Crisana
('Arad', (SELECT id FROM countries WHERE code = 'RO'), 46.1866, 21.3123, TRUE)
ON CONFLICT (name, country_id) DO NOTHING;
