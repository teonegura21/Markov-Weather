-- Seed tari si orase europene
-- Include toate tarile europene majore cu cate 1-2 orase reprezentative

INSERT INTO countries (name, code) VALUES
('Romania', 'RO'),
('Bulgaria', 'BG'),
('Ungaria', 'HU'),
('Serbia', 'RS'),
('Ucraina', 'UA'),
('Republica Moldova', 'MD'),
('Polonia', 'PL'),
('Slovacia', 'SK'),
('Cehia', 'CZ'),
('Austria', 'AT'),
('Germania', 'DE'),
('Franta', 'FR'),
('Italia', 'IT'),
('Spania', 'ES'),
('Grecia', 'GR'),
('Regatul Unit', 'GB'),
('Portugalia', 'PT'),
('Irlanda', 'IE'),
('Olanda', 'NL'),
('Belgia', 'BE'),
('Elvetia', 'CH'),
('Danemarca', 'DK'),
('Suedia', 'SE'),
('Norvegia', 'NO'),
('Finlanda', 'FI'),
('Turcia', 'TR'),
('Croatia', 'HR'),
('Slovenia', 'SI'),
('Bosnia si Hertegovina', 'BA'),
('Albania', 'AL'),
('Macedonia de Nord', 'MK'),
('Muntenegru', 'ME'),
('Estonia', 'EE'),
('Letonia', 'LV'),
('Lituania', 'LT'),
('Belarus', 'BY'),
('Islanda', 'IS'),
('Malta', 'MT'),
('Cipru', 'CY'),
('Luxemburg', 'LU'),
('Andorra', 'AD'),
('Monaco', 'MC'),
('San Marino', 'SM'),
('Liechtenstein', 'LI'),
('Vatican', 'VA')
ON CONFLICT (name) DO NOTHING;

INSERT INTO cities (name, country_id, latitude, longitude, is_important) VALUES
-- Romania (Muntenia)
('Bucuresti', (SELECT id FROM countries WHERE code = 'RO'), 44.4268, 26.1025, TRUE),
('Ploiesti', (SELECT id FROM countries WHERE code = 'RO'), 44.9367, 26.0129, TRUE),
('Pitesti', (SELECT id FROM countries WHERE code = 'RO'), 44.8565, 24.8692, TRUE),
('Targoviste', (SELECT id FROM countries WHERE code = 'RO'), 44.9244, 25.4565, FALSE),
('Giurgiu', (SELECT id FROM countries WHERE code = 'RO'), 43.9037, 25.9699, FALSE),

-- Romania (Transilvania)
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

-- Romania (Moldova)
('Iasi', (SELECT id FROM countries WHERE code = 'RO'), 47.1585, 27.6014, TRUE),
('Bacau', (SELECT id FROM countries WHERE code = 'RO'), 46.5695, 26.9160, TRUE),
('Suceava', (SELECT id FROM countries WHERE code = 'RO'), 47.6635, 26.2732, TRUE),
('Galati', (SELECT id FROM countries WHERE code = 'RO'), 45.4353, 28.0080, TRUE),
('Botosani', (SELECT id FROM countries WHERE code = 'RO'), 47.7484, 26.6594, TRUE),

-- Romania (Dobrogea)
('Constanta', (SELECT id FROM countries WHERE code = 'RO'), 44.1598, 28.6348, TRUE),
('Tulcea', (SELECT id FROM countries WHERE code = 'RO'), 45.1716, 28.7914, TRUE),

-- Romania (Oltenia)
('Craiova', (SELECT id FROM countries WHERE code = 'RO'), 44.3193, 23.8006, TRUE),
('Drobeta-Turnu Severin', (SELECT id FROM countries WHERE code = 'RO'), 44.6319, 22.6562, TRUE),
('Targu Jiu', (SELECT id FROM countries WHERE code = 'RO'), 45.0382, 23.2749, TRUE),

-- Romania (Banat)
('Timisoara', (SELECT id FROM countries WHERE code = 'RO'), 45.7489, 21.2087, TRUE),
('Resita', (SELECT id FROM countries WHERE code = 'RO'), 45.2970, 21.8857, TRUE),

-- Romania (Crisana)
('Arad', (SELECT id FROM countries WHERE code = 'RO'), 46.1866, 21.3123, TRUE),

-- Bulgaria
('Sofia', (SELECT id FROM countries WHERE code = 'BG'), 42.6977, 23.3219, TRUE),
('Plovdiv', (SELECT id FROM countries WHERE code = 'BG'), 42.1354, 24.7453, TRUE),

-- Ungaria
('Budapesta', (SELECT id FROM countries WHERE code = 'HU'), 47.4979, 19.0402, TRUE),
('Debrecen', (SELECT id FROM countries WHERE code = 'HU'), 47.5316, 21.6273, TRUE),

-- Serbia
('Belgrad', (SELECT id FROM countries WHERE code = 'RS'), 44.7866, 20.4489, TRUE),
('Novi Sad', (SELECT id FROM countries WHERE code = 'RS'), 45.2671, 19.8335, TRUE),

-- Ucraina
('Kiev', (SELECT id FROM countries WHERE code = 'UA'), 50.4501, 30.5234, TRUE),
('Odesa', (SELECT id FROM countries WHERE code = 'UA'), 46.4825, 30.7233, TRUE),

-- Republica Moldova
('Chisinau', (SELECT id FROM countries WHERE code = 'MD'), 47.0105, 28.8638, TRUE),

-- Polonia
('Varsovia', (SELECT id FROM countries WHERE code = 'PL'), 52.2297, 21.0122, TRUE),
('Cracovia', (SELECT id FROM countries WHERE code = 'PL'), 50.0647, 19.9450, TRUE),

-- Slovacia
('Bratislava', (SELECT id FROM countries WHERE code = 'SK'), 48.1486, 17.1077, TRUE),

-- Cehia
('Praga', (SELECT id FROM countries WHERE code = 'CZ'), 50.0755, 14.4378, TRUE),
('Brno', (SELECT id FROM countries WHERE code = 'CZ'), 49.1951, 16.6068, TRUE),

-- Austria
('Viena', (SELECT id FROM countries WHERE code = 'AT'), 48.2082, 16.3738, TRUE),
('Salzburg', (SELECT id FROM countries WHERE code = 'AT'), 47.8095, 13.0550, TRUE),

-- Germania
('Berlin', (SELECT id FROM countries WHERE code = 'DE'), 52.5200, 13.4050, TRUE),
('Munchen', (SELECT id FROM countries WHERE code = 'DE'), 48.1351, 11.5820, TRUE),
('Hamburg', (SELECT id FROM countries WHERE code = 'DE'), 53.5511, 9.9937, TRUE),

-- Franta
('Paris', (SELECT id FROM countries WHERE code = 'FR'), 48.8566, 2.3522, TRUE),
('Lyon', (SELECT id FROM countries WHERE code = 'FR'), 45.7640, 4.8357, TRUE),
('Marsilia', (SELECT id FROM countries WHERE code = 'FR'), 43.2965, 5.3698, TRUE),

-- Italia
('Roma', (SELECT id FROM countries WHERE code = 'IT'), 41.9028, 12.4964, TRUE),
('Milano', (SELECT id FROM countries WHERE code = 'IT'), 45.4642, 9.1900, TRUE),
('Napoli', (SELECT id FROM countries WHERE code = 'IT'), 40.8518, 14.2681, TRUE),

-- Spania
('Madrid', (SELECT id FROM countries WHERE code = 'ES'), 40.4168, -3.7038, TRUE),
('Barcelona', (SELECT id FROM countries WHERE code = 'ES'), 41.3851, 2.1734, TRUE),

-- Grecia
('Atena', (SELECT id FROM countries WHERE code = 'GR'), 37.9838, 23.7275, TRUE),
('Salonic', (SELECT id FROM countries WHERE code = 'GR'), 40.6401, 22.9444, TRUE),

-- Regatul Unit
('Londra', (SELECT id FROM countries WHERE code = 'GB'), 51.5074, -0.1278, TRUE),
('Manchester', (SELECT id FROM countries WHERE code = 'GB'), 53.4808, -2.2426, TRUE),

-- Portugalia
('Lisabona', (SELECT id FROM countries WHERE code = 'PT'), 38.7223, -9.1393, TRUE),
('Porto', (SELECT id FROM countries WHERE code = 'PT'), 41.1579, -8.6291, TRUE),

-- Irlanda
('Dublin', (SELECT id FROM countries WHERE code = 'IE'), 53.3498, -6.2603, TRUE),

-- Olanda
('Amsterdam', (SELECT id FROM countries WHERE code = 'NL'), 52.3676, 4.9041, TRUE),
('Rotterdam', (SELECT id FROM countries WHERE code = 'NL'), 51.9225, 4.4792, TRUE),

-- Belgia
('Bruxelles', (SELECT id FROM countries WHERE code = 'BE'), 50.8503, 4.3517, TRUE),
('Anvers', (SELECT id FROM countries WHERE code = 'BE'), 51.2194, 4.4025, TRUE),

-- Elvetia
('Zurich', (SELECT id FROM countries WHERE code = 'CH'), 47.3769, 8.5417, TRUE),
('Geneva', (SELECT id FROM countries WHERE code = 'CH'), 46.2044, 6.1432, TRUE),

-- Danemarca
('Copenhaga', (SELECT id FROM countries WHERE code = 'DK'), 55.6761, 12.5683, TRUE),

-- Suedia
('Stockholm', (SELECT id FROM countries WHERE code = 'SE'), 59.3293, 18.0686, TRUE),

-- Norvegia
('Oslo', (SELECT id FROM countries WHERE code = 'NO'), 59.9139, 10.7522, TRUE),

-- Finlanda
('Helsinki', (SELECT id FROM countries WHERE code = 'FI'), 60.1699, 24.9384, TRUE),

-- Turcia
('Istanbul', (SELECT id FROM countries WHERE code = 'TR'), 41.0082, 28.9784, TRUE),
('Ankara', (SELECT id FROM countries WHERE code = 'TR'), 39.9334, 32.8597, TRUE),

-- Croatia
('Zagreb', (SELECT id FROM countries WHERE code = 'HR'), 45.8150, 15.9819, TRUE),

-- Slovenia
('Ljubljana', (SELECT id FROM countries WHERE code = 'SI'), 46.0569, 14.5058, TRUE),

-- Bosnia si Hertegovina
('Sarajevo', (SELECT id FROM countries WHERE code = 'BA'), 43.8563, 18.4131, TRUE),

-- Albania
('Tirana', (SELECT id FROM countries WHERE code = 'AL'), 41.3275, 19.8187, TRUE),

-- Macedonia de Nord
('Skopje', (SELECT id FROM countries WHERE code = 'MK'), 41.9981, 21.4254, TRUE),

-- Muntenegru
('Podgorica', (SELECT id FROM countries WHERE code = 'ME'), 42.4304, 19.2594, TRUE),

-- Estonia
('Tallinn', (SELECT id FROM countries WHERE code = 'EE'), 59.4370, 24.7536, TRUE),

-- Letonia
('Riga', (SELECT id FROM countries WHERE code = 'LV'), 56.9496, 24.1052, TRUE),

-- Lituania
('Vilnius', (SELECT id FROM countries WHERE code = 'LT'), 54.6872, 25.2797, TRUE),

-- Belarus
('Minsk', (SELECT id FROM countries WHERE code = 'BY'), 53.9045, 27.5615, TRUE),

-- Islanda
('Reykjavik', (SELECT id FROM countries WHERE code = 'IS'), 64.1466, -21.9426, TRUE),

-- Malta
('Valletta', (SELECT id FROM countries WHERE code = 'MT'), 35.8989, 14.5146, TRUE),

-- Cipru
('Nicosia', (SELECT id FROM countries WHERE code = 'CY'), 35.1856, 33.3823, TRUE),

-- Luxemburg
('Luxemburg', (SELECT id FROM countries WHERE code = 'LU'), 49.6116, 6.1319, TRUE),

-- Andorra
('Andorra la Vella', (SELECT id FROM countries WHERE code = 'AD'), 42.5063, 1.5218, TRUE),

-- Monaco
('Monaco', (SELECT id FROM countries WHERE code = 'MC'), 43.7384, 7.4246, TRUE),

-- San Marino
('San Marino', (SELECT id FROM countries WHERE code = 'SM'), 43.9424, 12.4578, TRUE),

-- Liechtenstein
('Vaduz', (SELECT id FROM countries WHERE code = 'LI'), 47.1410, 9.5209, TRUE),

-- Vatican
('Vatican', (SELECT id FROM countries WHERE code = 'VA'), 41.9029, 12.4534, TRUE)
ON CONFLICT (name, country_id) DO NOTHING;
