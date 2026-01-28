-- DROP FUNCTION dqes.fn_best_paths_from_object(numeric, varchar, int4);

CREATE OR REPLACE FUNCTION dqes.fn_best_paths_from_object(p_conn_id numeric, p_from_object_code character varying, p_max_depth integer DEFAULT 6)
 RETURNS TABLE(rel_code character varying, from_object_code character varying, to_object_code character varying, join_alias character varying, hop_count integer, total_weight integer, path_relation_codes jsonb)
 LANGUAGE sql
 STABLE
AS $function$
WITH RECURSIVE edges AS (
    select
      r.code AS rel_code,
      r.from_object_code, r.to_object_code,r.join_alias, 
      COALESCE(r.path_weight, 10)::int AS w
    FROM dqes.qrytb_relation_info r
    WHERE r.dbconn_id = p_conn_id
      AND r.is_navigable = true
      AND r.relation_type = 'MANY_TO_ONE'
),
paths AS (
    select
      e.rel_code,
      e.from_object_code,
      e.to_object_code,e.join_alias, 
      1 AS hop_count,
      e.w AS total_weight,
      jsonb_build_array(e.rel_code) AS path_relation_codes,
      ARRAY[e.from_object_code, e.to_object_code, e.join_alias]::varchar[] AS visited
    FROM edges e
    WHERE e.from_object_code = p_from_object_code

    UNION ALL

    select
      e.rel_code,
      p.from_object_code,
      e.to_object_code,e.join_alias,
      p.hop_count + 1,
      p.total_weight + e.w,
      p.path_relation_codes || jsonb_build_array(e.rel_code),
      p.visited || e.to_object_code
    FROM paths p
    JOIN edges e
      ON e.from_object_code = p.to_object_code
    WHERE p.hop_count < GREATEST(p_max_depth, 1)
      AND NOT (e.to_object_code = ANY(p.visited))
),
ranked AS (
    select rel_code,
      from_object_code, to_object_code,join_alias,
      hop_count, total_weight, path_relation_codes,
      ROW_NUMBER() OVER (
        PARTITION BY from_object_code, to_object_code, join_alias
        ORDER BY hop_count ASC, total_weight ASC
      ) AS rn
    FROM paths
)
select rel_code,
  from_object_code, to_object_code, join_alias, hop_count, total_weight, path_relation_codes
FROM ranked
WHERE rn = 1;
$function$
;


---
-- DROP FUNCTION dqes.fn_best_paths_from_object_v2(numeric, varchar);

CREATE OR REPLACE FUNCTION dqes.fn_best_paths_from_object_v2(p_conn_id numeric, p_from_object_code character varying)
 RETURNS TABLE(rel_code character varying, from_object_code character varying, to_object_code character varying, join_alias character varying, relation_type character varying, hop_count integer, total_weight integer, path_relation_codes jsonb)
 LANGUAGE sql
 STABLE
AS $function$
WITH RECURSIVE edges AS (
    select
      r.code AS rel_code,
      r.from_object_code, r.to_object_code,r.join_alias, 
      COALESCE(r.path_weight, 10)::int AS w,
      -- max depth theo relation_type
      CASE
        WHEN r.relation_type = 'MANY_TO_ONE' THEN 6
        WHEN r.relation_type = 'ONE_TO_MANY' THEN 1
        ELSE 1
      END AS max_depth,
      r.relation_type
    FROM dqes.qrytb_relation_info r
    WHERE r.dbconn_id = p_conn_id
      AND r.is_navigable = true
      AND r.relation_type IN ('MANY_TO_ONE', 'ONE_TO_MANY')
),
paths AS (
    select
      e.rel_code,
      e.from_object_code,
      e.to_object_code,e.join_alias,
      e.max_depth,
      e.relation_type,
      1 AS hop_count,
      e.w AS total_weight,
      jsonb_build_array(e.rel_code) AS path_relation_codes,
      ARRAY[e.from_object_code, e.to_object_code, e.join_alias]::varchar[] AS visited
    FROM edges e
    WHERE e.from_object_code = p_from_object_code

    UNION ALL

    select
      e.rel_code,
      p.from_object_code,
      e.to_object_code,e.join_alias,
      e.max_depth,
      e.relation_type,
      p.hop_count + 1,
      p.total_weight + e.w,
      p.path_relation_codes || jsonb_build_array(e.rel_code),
      p.visited || e.to_object_code
    FROM paths p
    JOIN edges e
      ON e.from_object_code = p.to_object_code and e.relation_type = p.relation_type 
    WHERE p.hop_count < GREATEST(e.max_depth, 1)
      AND NOT (e.to_object_code = ANY(p.visited))
),
ranked AS (
    select rel_code,
      from_object_code, to_object_code,join_alias,relation_type,
      hop_count, total_weight, path_relation_codes,
      ROW_NUMBER() OVER (
        PARTITION BY from_object_code, to_object_code, join_alias
        ORDER BY hop_count ASC, total_weight ASC
      ) AS rn
    FROM paths
)
select rel_code,
  from_object_code, to_object_code, join_alias, relation_type, hop_count, total_weight, path_relation_codes
FROM ranked
WHERE rn = 1;
$function$
;
