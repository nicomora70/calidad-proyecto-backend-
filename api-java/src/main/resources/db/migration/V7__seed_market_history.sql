-- Historial inicial del índice de referencia (QQQ) para que las proyecciones funcionen desde la
-- instalación: el proveedor solo guarda una variación por día y la proyección necesita al menos 5.
-- Serie sintética y determinista (Box-Muller sobre md5 de la fecha), calibrada a la volatilidad
-- diaria típica del QQQ: media 0,07 %, desviación 1,3 %, recortada a ±6 %.
-- fetched_at queda en el pasado para que nunca cuente como caché vigente (TTL) de la variación actual.
-- ON CONFLICT: no pisa datos reales ya guardados.
INSERT INTO market_variations (id, symbol, market_date, change_pct, source, fetched_at)
SELECT gen_random_uuid(),
       'QQQ',
       s.d,
       round(greatest(-6, least(6,
             0.07 + 1.3 * sqrt(-2 * ln(s.u1)) * cos(2 * pi() * s.u2)))::numeric, 4),
       'SEED',
       (s.d + time '21:00') AT TIME ZONE 'UTC'
FROM (
    SELECT g::date AS d,
           (('x' || substr(md5(g::date::text), 1, 8))::bit(32)::bigint + 1) / 4294967297.0 AS u1,
           (('x' || substr(md5(g::date::text), 9, 8))::bit(32)::bigint + 1) / 4294967297.0 AS u2
    FROM generate_series(date '2025-11-21', date '2026-11-20', interval '1 day') AS g
    WHERE extract(isodow FROM g) < 6
) AS s
ON CONFLICT (symbol, market_date) DO NOTHING;
