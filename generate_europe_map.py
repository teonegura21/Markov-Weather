#!/usr/bin/env python3
"""
Generează EuropeMapData.java din date Natural Earth (public domain).
Folosește poligoane simplificate 1:110m pentru țările europene.
"""
import requests
import json
import math

URL = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_110m_admin_0_countries.geojson"

# Țări europene de inclus (ISO A2 / nume Natural Earth)
EUROPE_COUNTRIES = {
    'Albania', 'Andorra', 'Austria', 'Belarus', 'Belgium', 'Bosnia and Herzegovina',
    'Bulgaria', 'Croatia', 'Cyprus', 'Czechia', 'Denmark', 'Estonia', 'Finland',
    'France', 'Germany', 'Greece', 'Hungary', 'Iceland', 'Ireland', 'Italy',
    'Kosovo', 'Latvia', 'Liechtenstein', 'Lithuania', 'Luxembourg', 'Malta',
    'Moldova', 'Monaco', 'Montenegro', 'Netherlands', 'North Macedonia', 'Norway',
    'Poland', 'Portugal', 'Romania', 'Russia', 'San Marino', 'Serbia', 'Slovakia',
    'Slovenia', 'Spain', 'Sweden', 'Switzerland', 'Turkey', 'Ukraine',
    'United Kingdom', 'Vatican'
}

# Limite Europa (incl. UK, Islanda, Turcia europeană)
MIN_LON, MAX_LON = -10.0, 40.0
MIN_LAT, MAX_LAT = 36.0, 71.0


def simplify_poly(coords, tolerance_deg=0.8):
    """Simplificare Douglas-Peucker foarte light."""
    if len(coords) <= 3:
        return coords
    # Mai întâi eșantionare la distanță minimă
    filtered = [coords[0]]
    for pt in coords[1:]:
        last = filtered[-1]
        d = math.hypot(pt[0] - last[0], pt[1] - last[1])
        if d >= tolerance_deg:
            filtered.append(pt)
    # Asigură închidere
    if filtered and filtered[0] != filtered[-1]:
        filtered.append(filtered[0])
    return filtered


def main():
    print("Downloading Natural Earth data...")
    r = requests.get(URL)
    r.raise_for_status()
    data = r.json()

    countries = {}
    for feat in data['features']:
        name = feat['properties'].get('NAME', '') or feat['properties'].get('ADMIN', '')
        if not name:
            continue
        # Fuzzy match
        matched = None
        for ec in EUROPE_COUNTRIES:
            if ec.lower() in name.lower() or name.lower() in ec.lower():
                matched = ec
                break
        if not matched:
            continue

        geom = feat['geometry']
        if geom['type'] == 'Polygon':
            polys = geom['coordinates']
        elif geom['type'] == 'MultiPolygon':
            # Alege cel mai mare poligon (suprafață aproximativă)
            best = max(geom['coordinates'], key=lambda p: len(p[0]))
            polys = best
        else:
            continue

        # Folosește primul inel (exterior)
        coords = polys[0]
        # Filtrare puncte în afara Europei (pentru Rusia, Turcia etc.)
        filtered = [(lon, lat) for lon, lat in coords
                    if MIN_LON - 5 <= lon <= MAX_LON + 5 and MIN_LAT - 5 <= lat <= MAX_LAT + 5]
        if len(filtered) < 3:
            continue
        simplified = simplify_poly(filtered, tolerance_deg=0.6)
        if len(simplified) < 3:
            continue
        countries[matched] = simplified

    # Generează Java
    lines = []
    lines.append('package com.sgbd.util;')
    lines.append('')
    lines.append('import javafx.geometry.Point2D;')
    lines.append('import java.util.LinkedHashMap;')
    lines.append('import java.util.Map;')
    lines.append('')
    lines.append('/**')
    lines.append(' * Date geografice simplificate pentru Europa.')
    lines.append(' * Sursa: Natural Earth 1:110m (public domain).')
    lines.append(' * Poligoane simplificate pentru performanta in JavaFX canvas.')
    lines.append(' */')
    lines.append('public final class EuropeMapData {')
    lines.append('')
    lines.append('    private EuropeMapData() {}')
    lines.append('')
    lines.append(f'    public static final double MIN_LAT = {MIN_LAT};')
    lines.append(f'    public static final double MAX_LAT = {MAX_LAT};')
    lines.append(f'    public static final double MIN_LON = {MIN_LON};')
    lines.append(f'    public static final double MAX_LON = {MAX_LON};')
    lines.append('')

    for name, pts in sorted(countries.items()):
        var_name = name.upper().replace(' ', '_').replace('-', '_').replace('.', '_')
        lines.append(f'    // {name} ({len(pts)} puncte)')
        lines.append(f'    private static final double[][] {var_name} = {{')
        pt_lines = []
        for lon, lat in pts:
            pt_lines.append(f'        {{{lon:.4f}, {lat:.4f}}}')
        lines.append(',\n'.join(pt_lines))
        lines.append('    };')
        lines.append('')

    lines.append('    /** Returnează poligoanele țărilor europene. */')
    lines.append('    public static Map<String, double[][]> getCountryPolygons() {')
    lines.append('        Map<String, double[][]> map = new LinkedHashMap<>();')
    for name in sorted(countries.keys()):
        var_name = name.upper().replace(' ', '_').replace('-', '_').replace('.', '_')
        lines.append(f'        map.put("{name}", {var_name});')
    lines.append('        return map;')
    lines.append('    }')
    lines.append('')

    # Orașe importante
    cities = [
        ('Lisbon', 'Portugal', 38.7223, -9.1393),
        ('Madrid', 'Spain', 40.4168, -3.7038),
        ('Barcelona', 'Spain', 41.3851, 2.1734),
        ('Paris', 'France', 48.8566, 2.3522),
        ('Lyon', 'France', 45.7640, 4.8357),
        ('Marseille', 'France', 43.2965, 5.3698),
        ('London', 'United Kingdom', 51.5074, -0.1278),
        ('Birmingham', 'United Kingdom', 52.4862, -1.8904),
        ('Manchester', 'United Kingdom', 53.4808, -2.2426),
        ('Dublin', 'Ireland', 53.3498, -6.2603),
        ('Brussels', 'Belgium', 50.8503, 4.3517),
        ('Amsterdam', 'Netherlands', 52.3676, 4.9041),
        ('Luxembourg', 'Luxembourg', 49.6116, 6.1319),
        ('Berlin', 'Germany', 52.5200, 13.4050),
        ('Munich', 'Germany', 48.1351, 11.5820),
        ('Hamburg', 'Germany', 53.5511, 9.9937),
        ('Frankfurt', 'Germany', 50.1109, 8.6821),
        ('Copenhagen', 'Denmark', 55.6761, 12.5683),
        ('Oslo', 'Norway', 59.9139, 10.7522),
        ('Stockholm', 'Sweden', 59.3293, 18.0686),
        ('Helsinki', 'Finland', 60.1699, 24.9384),
        ('Warsaw', 'Poland', 52.2297, 21.0122),
        ('Krakow', 'Poland', 50.0647, 19.9450),
        ('Prague', 'Czechia', 50.0755, 14.4378),
        ('Vienna', 'Austria', 48.2082, 16.3738),
        ('Budapest', 'Hungary', 47.4979, 19.0402),
        ('Bucharest', 'Romania', 44.4268, 26.1025),
        ('Cluj-Napoca', 'Romania', 46.7712, 23.6236),
        ('Timisoara', 'Romania', 45.7489, 21.2087),
        ('Iasi', 'Romania', 47.1585, 27.6014),
        ('Sofia', 'Bulgaria', 42.6977, 23.3219),
        ('Belgrade', 'Serbia', 44.7866, 20.4489),
        ('Zagreb', 'Croatia', 45.8150, 15.9819),
        ('Sarajevo', 'Bosnia and Herzegovina', 43.8563, 18.4131),
        ('Podgorica', 'Montenegro', 42.4304, 19.2594),
        ('Tirana', 'Albania', 41.3275, 19.8187),
        ('Athens', 'Greece', 37.9838, 23.7275),
        ('Thessaloniki', 'Greece', 40.6401, 22.9444),
        ('Rome', 'Italy', 41.9028, 12.4964),
        ('Milan', 'Italy', 45.4642, 9.1900),
        ('Naples', 'Italy', 40.8518, 14.2681),
        ('Turin', 'Italy', 45.0703, 7.6869),
        ('Bern', 'Switzerland', 46.9480, 7.4474),
        ('Zurich', 'Switzerland', 47.3769, 8.5417),
        ('Geneva', 'Switzerland', 46.2044, 6.1432),
        ('Ljubljana', 'Slovenia', 46.0569, 14.5058),
        ('Bratislava', 'Slovakia', 48.1486, 17.1077),
        ('Minsk', 'Belarus', 53.9045, 27.5615),
        ('Kyiv', 'Ukraine', 50.4501, 30.5234),
        ('Odessa', 'Ukraine', 46.4825, 30.7233),
        ('Chisinau', 'Moldova', 47.0105, 28.8638),
        ('Tallinn', 'Estonia', 59.4370, 24.7536),
        ('Riga', 'Latvia', 56.9496, 24.1052),
        ('Vilnius', 'Lithuania', 54.6872, 25.2797),
        ('Istanbul', 'Turkey', 41.0082, 28.9784),
        ('Ankara', 'Turkey', 39.9334, 32.8597),
        ('Izmir', 'Turkey', 38.4192, 27.1287),
        ('Moscow', 'Russia', 55.7558, 37.6173),
        ('Saint Petersburg', 'Russia', 59.9311, 30.3609),
        ('Reykjavik', 'Iceland', 64.1466, -21.9426),
        ('Valletta', 'Malta', 35.8989, 14.5146),
        ('Nicosia', 'Cyprus', 35.1856, 33.3823),
        ('Skopje', 'North Macedonia', 41.9981, 21.4254),
        ('Tbilisi', 'Georgia', 41.7151, 44.8271),
        ('Yerevan', 'Armenia', 40.1792, 44.4991),
        ('Baku', 'Azerbaijan', 40.4093, 49.8671),
    ]
    # Elimină duplicate dacă există
    seen = set()
    unique_cities = []
    for name, country, lat, lon in cities:
        key = (name, country)
        if key not in seen:
            seen.add(key)
            unique_cities.append((name, country, lat, lon))

    lines.append('    /** Orașe importante din Europa pentru hartă. */')
    lines.append('    public static final Object[][] IMPORTANT_CITIES = {')
    for name, country, lat, lon in unique_cities:
        lines.append(f'        {{"{name}", "{country}", {lat:.4f}, {lon:.4f}}},')
    lines.append('    };')
    lines.append('')

    lines.append('    /**')
    lines.append('     * Convertește coordonate geografice în puncte pe canvas (proiecție equirectangulară).')
    lines.append('     */')
    lines.append('    public static Point2D geoToCanvas(double lat, double lon, double w, double h, double pad) {')
    lines.append('        double usableW = w - 2 * pad;')
    lines.append('        double usableH = h - 2 * pad;')
    lines.append('        double x = pad + (lon - MIN_LON) / (MAX_LON - MIN_LON) * usableW;')
    lines.append('        double y = pad + (MAX_LAT - lat) / (MAX_LAT - MIN_LAT) * usableH;')
    lines.append('        return new Point2D(x, y);')
    lines.append('    }')
    lines.append('}')

    output = '\n'.join(lines)
    with open('src/main/java/com/sgbd/util/EuropeMapData.java', 'w') as f:
        f.write(output)

    print(f"Generated EuropeMapData.java with {len(countries)} countries and {len(unique_cities)} cities.")
    total_pts = sum(len(v) for v in countries.values())
    print(f"Total polygon points: {total_pts}")


if __name__ == '__main__':
    main()
