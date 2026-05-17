-- =============================================================================
-- SCRIPT DE EXPORT COMPLET - SCRIE UN SINGUR FISIER SQL PE DISK
-- Foloseste UTL_FILE pentru a depasi limita de buffer DBMS_OUTPUT
-- Ruleaza in contul STUDENT
-- Fisierul generat: C:\export_oracle\export_student.sql
-- =============================================================================

SET SERVEROUTPUT ON SIZE UNLIMITED

DECLARE
    -- Handle pentru fisier
    v_file      UTL_FILE.FILE_TYPE;
    v_schema    VARCHAR2(100) := SYS_CONTEXT('USERENV','CURRENT_USER');

    -- =========================================================================
    -- PROCEDURA: scrie o linie in fisier
    -- =========================================================================
    PROCEDURE w(p_line VARCHAR2) IS
    BEGIN
        UTL_FILE.PUT_LINE(v_file, p_line);
    END;

    -- Linie goala
    PROCEDURE wl IS
    BEGIN
        UTL_FILE.PUT_LINE(v_file, '');
    END;

    -- Separator
    PROCEDURE wsep(p_char VARCHAR2 DEFAULT '-', p_len NUMBER DEFAULT 79) IS
    BEGIN
        UTL_FILE.PUT_LINE(v_file, '-- ' || RPAD(p_char, p_len, p_char));
    END;

    -- =========================================================================
    -- SECTIUNEA 1: TYPES
    -- =========================================================================
    PROCEDURE export_types IS
        v_count     NUMBER;
        v_lines     NUMBER;
        v_body_lines NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM user_types;
        wl;
        wsep('=');
        w('-- SECTIUNEA 1: TYPES');
        w('-- Total tipuri definite in schema: ' || v_count);
        wsep('=');
        wl;

        FOR rec IN (
            SELECT t.type_name, t.typecode, t.final, t.instantiable, t.supertype_name
            FROM user_types t
            ORDER BY
                CASE t.typecode WHEN 'COLLECTION' THEN 1 ELSE 2 END,
                CASE WHEN t.supertype_name IS NULL THEN 1 ELSE 2 END,
                t.type_name
        ) LOOP
            SELECT COUNT(*) INTO v_lines FROM user_source
            WHERE name = rec.type_name AND type = 'TYPE';

            wsep;
            w('-- TYPE: ' || rec.type_name);
            w('-- Typecode: ' || rec.typecode);
            IF rec.supertype_name IS NOT NULL THEN
                w('-- Mostenire: UNDER ' || rec.supertype_name);
            END IF;
            w('-- Final: ' || rec.final || ' | Instantiable: ' || rec.instantiable);
            w('-- Linii sursa (spec): ' || v_lines);
            wsep;

            IF rec.typecode = 'COLLECTION' THEN
                DECLARE
                    v_coll_type   VARCHAR2(20);
                    v_elem_type   VARCHAR2(100);
                    v_elem_length NUMBER;
                    v_elem_prec   NUMBER;
                    v_elem_scale  NUMBER;
                    v_limit       NUMBER;
                    v_elem_str    VARCHAR2(200);
                BEGIN
                    SELECT coll_type, elem_type_name, length, precision, scale, upper_bound
                    INTO v_coll_type, v_elem_type, v_elem_length, v_elem_prec, v_elem_scale, v_limit
                    FROM user_coll_types WHERE type_name = rec.type_name;

                    IF v_elem_type IN ('VARCHAR2','NVARCHAR2','CHAR','NCHAR') THEN
                        v_elem_str := v_elem_type || '(' || v_elem_length || ')';
                    ELSIF v_elem_type = 'NUMBER' AND v_elem_prec IS NOT NULL THEN
                        v_elem_str := 'NUMBER(' || v_elem_prec || ',' || NVL(v_elem_scale,0) || ')';
                    ELSE
                        v_elem_str := v_elem_type;
                    END IF;

                    IF v_coll_type = 'TABLE' THEN
                        w('CREATE OR REPLACE TYPE ' || rec.type_name || ' AS TABLE OF ' || v_elem_str || ';');
                    ELSE
                        w('CREATE OR REPLACE TYPE ' || rec.type_name || ' IS VARRAY(' || v_limit || ') OF ' || v_elem_str || ';');
                    END IF;
                    w('/');
                END;
            ELSE
                FOR src IN (
                    SELECT text FROM user_source
                    WHERE name = rec.type_name AND type = 'TYPE'
                    ORDER BY line
                ) LOOP
                    UTL_FILE.PUT(v_file, src.text);
                END LOOP;
                UTL_FILE.NEW_LINE(v_file);
                w('/');
            END IF;
            wl;

            -- Type body
            SELECT COUNT(*) INTO v_body_lines FROM user_source
            WHERE name = rec.type_name AND type = 'TYPE BODY';

            IF v_body_lines > 0 THEN
                w('-- TYPE BODY: ' || rec.type_name || ' | Linii: ' || v_body_lines);
                FOR src IN (
                    SELECT text FROM user_source
                    WHERE name = rec.type_name AND type = 'TYPE BODY'
                    ORDER BY line
                ) LOOP
                    UTL_FILE.PUT(v_file, src.text);
                END LOOP;
                UTL_FILE.NEW_LINE(v_file);
                w('/');
                wl;
            END IF;
        END LOOP;
    END;

    -- =========================================================================
    -- SECTIUNEA 2: SEQUENCES
    -- =========================================================================
    PROCEDURE export_sequences IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM user_sequences;
        wl; wsep('=');
        w('-- SECTIUNEA 2: SEQUENCES | Total: ' || v_count);
        wsep('='); wl;

        FOR rec IN (
            SELECT sequence_name, min_value, max_value, increment_by,
                   cycle_flag, order_flag, cache_size, last_number
            FROM user_sequences ORDER BY sequence_name
        ) LOOP
            wsep;
            w('-- SEQUENCE: ' || rec.sequence_name);
            w('-- Valoare curenta: ' || rec.last_number || ' | Increment: ' || rec.increment_by);
            w('-- Min: ' || rec.min_value || ' | Cache: ' || rec.cache_size || ' | Cycle: ' || rec.cycle_flag);
            wsep;
            w('CREATE SEQUENCE ' || rec.sequence_name);
            w('    START WITH ' || rec.last_number);
            w('    INCREMENT BY ' || rec.increment_by);
            w('    MINVALUE ' || rec.min_value);
            IF rec.max_value >= 999999999999999999999999999 THEN
                w('    NOMAXVALUE');
            ELSE
                w('    MAXVALUE ' || rec.max_value);
            END IF;
            w(CASE WHEN rec.cycle_flag='Y' THEN '    CYCLE' ELSE '    NOCYCLE' END);
            w(CASE WHEN rec.cache_size=0 THEN '    NOCACHE' ELSE '    CACHE ' || rec.cache_size END);
            w(CASE WHEN rec.order_flag='Y' THEN '    ORDER;' ELSE '    NOORDER;' END);
            wl;
        END LOOP;
    END;

    -- =========================================================================
    -- SECTIUNEA 3: TABLES
    -- =========================================================================
    PROCEDURE export_tables IS
        v_tab_count   NUMBER;
        v_row_count   NUMBER;
        v_con_count   NUMBER;
        v_idx_count   NUMBER;
        v_col_type    VARCHAR2(300);
        v_nested_col  VARCHAR2(100);
        v_nested_tab  VARCHAR2(100);
        v_has_nested  BOOLEAN;
        v_has_clob    BOOLEAN;
        v_first_col   BOOLEAN;
        v_search_cond LONG;
        v_skip        BOOLEAN;
        CURSOR c_cond(p_con VARCHAR2, p_tab VARCHAR2) IS
            SELECT search_condition FROM user_constraints
            WHERE constraint_name = p_con AND table_name = p_tab;
    BEGIN
        SELECT COUNT(*) INTO v_tab_count FROM user_tables
        WHERE table_name NOT IN (SELECT table_name FROM user_nested_tables);

        wl; wsep('=');
        w('-- SECTIUNEA 3: TABLES (' || v_tab_count || ' tabele)');
        w('-- Tabelele de stocare nested table sunt excluse (create automat de Oracle)');
        wsep('='); wl;

        FOR tab IN (
            SELECT t.table_name,
                   (SELECT COUNT(*) FROM user_constraints c
                    WHERE c.table_name = t.table_name AND c.constraint_type = 'R') AS nr_fk
            FROM user_tables t
            WHERE t.table_name NOT IN (SELECT table_name FROM user_nested_tables)
            ORDER BY nr_fk, t.table_name
        ) LOOP
            BEGIN
                EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM "' || tab.table_name || '"' INTO v_row_count;
            EXCEPTION WHEN OTHERS THEN v_row_count := -1; END;

            SELECT COUNT(*) INTO v_con_count FROM user_constraints WHERE table_name = tab.table_name;
            SELECT COUNT(*) INTO v_idx_count FROM user_indexes WHERE table_name = tab.table_name;

            v_has_nested := FALSE;
            BEGIN
                SELECT n.table_name, n.parent_table_column INTO v_nested_tab, v_nested_col
                FROM user_nested_tables n WHERE n.parent_table_name = tab.table_name AND ROWNUM = 1;
                v_has_nested := TRUE;
            EXCEPTION WHEN NO_DATA_FOUND THEN v_has_nested := FALSE; END;

            v_has_clob := FALSE;
            BEGIN
                DECLARE v_dummy VARCHAR2(10); BEGIN
                    SELECT 'Y' INTO v_dummy FROM user_tab_columns
                    WHERE table_name = tab.table_name AND data_type IN ('CLOB','NCLOB','BLOB') AND ROWNUM = 1;
                    v_has_clob := TRUE;
                EXCEPTION WHEN NO_DATA_FOUND THEN v_has_clob := FALSE; END;
            END;

            wsep;
            w('-- TABEL: ' || tab.table_name);
            w('-- Nr. inregistrari: ' || CASE WHEN v_row_count >= 0 THEN TO_CHAR(v_row_count) ELSE 'N/A' END);
            w('-- Nr. constrangeri: ' || v_con_count || ' | Nr. indecsi: ' || v_idx_count);
            IF v_has_clob THEN w('-- Contine coloana CLOB/BLOB'); END IF;
            IF v_has_nested THEN
                w('-- Coloana nested table: ' || v_nested_col || ' | Storage: ' || v_nested_tab);
            END IF;

            -- Comentarii constrangeri
            FOR con IN (
                SELECT c.constraint_name, c.constraint_type, c.r_constraint_name,
                       c.delete_rule, c.status,
                       (SELECT LISTAGG(cc.column_name,', ') WITHIN GROUP (ORDER BY cc.position)
                        FROM user_cons_columns cc WHERE cc.constraint_name = c.constraint_name) AS cols,
                       (SELECT t2.table_name FROM user_constraints t2
                        WHERE t2.constraint_name = c.r_constraint_name) AS r_table
                FROM user_constraints c WHERE c.table_name = tab.table_name
                ORDER BY c.constraint_type
            ) LOOP
                DECLARE
                    v_tip VARCHAR2(20);
                    v_cond LONG;
                    CURSOR cc IS SELECT search_condition FROM user_constraints
                        WHERE constraint_name = con.constraint_name AND table_name = tab.table_name;
                BEGIN
                    v_tip := CASE con.constraint_type WHEN 'P' THEN 'PRIMARY KEY'
                        WHEN 'U' THEN 'UNIQUE' WHEN 'R' THEN 'FOREIGN KEY'
                        WHEN 'C' THEN 'CHECK' ELSE con.constraint_type END;
                    IF con.constraint_type = 'C' AND con.constraint_name NOT LIKE 'SYS_%' THEN
                        OPEN cc; FETCH cc INTO v_cond; CLOSE cc;
                    END IF;
                    w('--   [' || v_tip || '] ' || con.constraint_name
                        || ' | Coloane: ' || NVL(con.cols,'N/A')
                        || CASE WHEN con.constraint_type='R'
                                THEN ' -> ' || NVL(con.r_table,'?') || ' | Delete: ' || NVL(con.delete_rule,'NO ACTION')
                                ELSE '' END
                        || CASE WHEN v_cond IS NOT NULL
                                THEN ' | Cond: ' || REPLACE(SUBSTR(v_cond,1,200),CHR(10),' ')
                                ELSE '' END
                        || ' | ' || con.status);
                END;
            END LOOP;

            -- Comentarii indecsi
            FOR idx IN (
                SELECT i.index_name, i.index_type, i.uniqueness,
                       (SELECT LISTAGG(ic.column_name,', ') WITHIN GROUP (ORDER BY ic.column_position)
                        FROM user_ind_columns ic WHERE ic.index_name = i.index_name) AS cols
                FROM user_indexes i WHERE i.table_name = tab.table_name ORDER BY i.index_name
            ) LOOP
                w('--   [INDEX] ' || idx.index_name || ' | ' || idx.index_type
                    || ' | ' || idx.uniqueness || ' | ' || NVL(idx.cols,'N/A'));
            END LOOP;
            wsep;

            -- CREATE TABLE DDL
            w('CREATE TABLE ' || tab.table_name || ' (');
            v_first_col := TRUE;

            FOR col IN (
                SELECT column_name, data_type, data_length, data_precision,
                       data_scale, nullable, data_default, column_id
                FROM user_tab_columns WHERE table_name = tab.table_name ORDER BY column_id
            ) LOOP
                IF NOT v_first_col THEN UTL_FILE.PUT_LINE(v_file, ','); END IF;
                v_first_col := FALSE;

                IF col.data_type IN ('VARCHAR2','NVARCHAR2','CHAR','NCHAR') THEN
                    v_col_type := col.data_type || '(' || col.data_length || ')';
                ELSIF col.data_type = 'NUMBER' THEN
                    IF col.data_precision IS NOT NULL AND col.data_scale IS NOT NULL THEN
                        v_col_type := 'NUMBER(' || col.data_precision || ',' || col.data_scale || ')';
                    ELSIF col.data_precision IS NOT NULL THEN
                        v_col_type := 'NUMBER(' || col.data_precision || ')';
                    ELSE v_col_type := 'NUMBER'; END IF;
                ELSE v_col_type := col.data_type; END IF;

                UTL_FILE.PUT(v_file, '    ' || RPAD(col.column_name,25) || ' ' || v_col_type);
                IF col.data_default IS NOT NULL THEN
                    UTL_FILE.PUT(v_file, ' DEFAULT ' || TRIM(col.data_default));
                END IF;
                IF col.nullable = 'N' THEN UTL_FILE.PUT(v_file, ' NOT NULL'); END IF;
            END LOOP;

            -- Constrangeri inline (PK, UK, CHECK - nu FK)
            FOR con IN (
                SELECT c.constraint_name, c.constraint_type,
                       (SELECT LISTAGG(cc.column_name,', ') WITHIN GROUP (ORDER BY cc.position)
                        FROM user_cons_columns cc WHERE cc.constraint_name = c.constraint_name) AS cols
                FROM user_constraints c
                WHERE c.table_name = tab.table_name
                  AND c.constraint_type IN ('P','U','C')
                  AND NOT (c.constraint_name LIKE 'SYS_%' AND c.constraint_type IN ('U','C'))
                ORDER BY c.constraint_type
            ) LOOP
                DECLARE
                    v_cond2 LONG;
                    CURSOR cc2 IS SELECT search_condition FROM user_constraints
                        WHERE constraint_name = con.constraint_name AND table_name = tab.table_name;
                BEGIN
                    v_skip := FALSE;
                    IF con.constraint_type = 'C' THEN
                        OPEN cc2; FETCH cc2 INTO v_cond2; CLOSE cc2;
                        IF UPPER(SUBSTR(v_cond2,1,500)) LIKE '% IS NOT NULL'
                           OR UPPER(SUBSTR(v_cond2,1,500)) = 'IS NOT NULL' THEN
                            v_skip := TRUE;
                        END IF;
                    END IF;

                    IF NOT v_skip THEN
                        UTL_FILE.NEW_LINE(v_file);
                        UTL_FILE.PUT(v_file, ',    CONSTRAINT ' || con.constraint_name || ' ');
                        IF con.constraint_type = 'P' THEN
                            UTL_FILE.PUT(v_file, 'PRIMARY KEY (' || con.cols || ')');
                        ELSIF con.constraint_type = 'U' THEN
                            UTL_FILE.PUT(v_file, 'UNIQUE (' || con.cols || ')');
                        ELSIF con.constraint_type = 'C' THEN
                            UTL_FILE.PUT(v_file, 'CHECK (' || REPLACE(SUBSTR(v_cond2,1,4000),CHR(10),' ') || ')');
                        END IF;
                    END IF;
                END;
            END LOOP;

            UTL_FILE.NEW_LINE(v_file);
            IF v_has_nested THEN
                w(')');
                w('NESTED TABLE ' || v_nested_col || ' STORE AS ' || v_nested_tab || ';');
            ELSE
                w(');');
            END IF;
            wl;
        END LOOP;
    END;

    -- =========================================================================
    -- SECTIUNEA 4: FOREIGN KEYS
    -- =========================================================================
    PROCEDURE export_fks IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM user_constraints WHERE constraint_type = 'R';
        wl; wsep('=');
        w('-- SECTIUNEA 4: FOREIGN KEYS | Total: ' || v_count);
        wsep('='); wl;

        FOR fk IN (
            SELECT c.table_name, c.constraint_name, c.delete_rule, c.status,
                   (SELECT t2.table_name FROM user_constraints t2 WHERE t2.constraint_name = c.r_constraint_name) AS r_table,
                   (SELECT LISTAGG(cc.column_name,', ') WITHIN GROUP (ORDER BY cc.position)
                    FROM user_cons_columns cc WHERE cc.constraint_name = c.constraint_name) AS fk_cols,
                   (SELECT LISTAGG(cc.column_name,', ') WITHIN GROUP (ORDER BY cc.position)
                    FROM user_cons_columns cc WHERE cc.constraint_name = c.r_constraint_name) AS pk_cols
            FROM user_constraints c WHERE c.constraint_type = 'R'
            ORDER BY c.table_name, c.constraint_name
        ) LOOP
            w('-- FK: ' || fk.constraint_name || ' | ' || fk.table_name || '(' || fk.fk_cols || ')'
                || ' -> ' || NVL(fk.r_table,'?') || '(' || NVL(fk.pk_cols,'?') || ')'
                || ' | Delete: ' || NVL(fk.delete_rule,'NO ACTION'));
            w('ALTER TABLE ' || fk.table_name);
            w('    ADD CONSTRAINT ' || fk.constraint_name);
            w('    FOREIGN KEY (' || fk.fk_cols || ')');
            w('    REFERENCES ' || NVL(fk.r_table,'?') || ' (' || NVL(fk.pk_cols,'?') || ')');
            IF fk.delete_rule = 'CASCADE' THEN w('    ON DELETE CASCADE'); END IF;
            IF fk.delete_rule = 'SET NULL' THEN w('    ON DELETE SET NULL'); END IF;
            w(';'); wl;
        END LOOP;
    END;

    -- =========================================================================
    -- SECTIUNEA 5: INDEXES
    -- =========================================================================
    PROCEDURE export_indexes IS
        v_count NUMBER;
        v_cols  VARCHAR2(500);
    BEGIN
        SELECT COUNT(*) INTO v_count FROM user_indexes
        WHERE index_name NOT IN (SELECT constraint_name FROM user_constraints WHERE constraint_type IN ('P','U'))
          AND index_name NOT LIKE 'SYS_IL%' AND index_name NOT LIKE 'SYS_FK%'
          AND index_type != 'LOB'
          AND table_name NOT IN (SELECT table_name FROM user_nested_tables);

        wl; wsep('=');
        w('-- SECTIUNEA 5: INDEXES aditional (exclus PK/UK/LOB/NT) | Total: ' || v_count);
        wsep('='); wl;

        FOR idx IN (
            SELECT i.index_name, i.table_name, i.index_type, i.uniqueness, i.status
            FROM user_indexes i
            WHERE i.index_name NOT IN (SELECT constraint_name FROM user_constraints WHERE constraint_type IN ('P','U'))
              AND i.index_name NOT LIKE 'SYS_IL%' AND i.index_name NOT LIKE 'SYS_FK%'
              AND i.index_type != 'LOB'
              AND i.table_name NOT IN (SELECT table_name FROM user_nested_tables)
            ORDER BY i.table_name, i.index_name
        ) LOOP
            SELECT LISTAGG(column_name,', ') WITHIN GROUP (ORDER BY column_position)
            INTO v_cols FROM user_ind_columns WHERE index_name = idx.index_name;

            w('-- INDEX: ' || idx.index_name || ' | Tabel: ' || idx.table_name
                || ' | Tip: ' || idx.index_type || ' | ' || idx.uniqueness
                || ' | Coloane: ' || v_cols);
            IF idx.uniqueness = 'UNIQUE' THEN
                w('CREATE UNIQUE INDEX ' || idx.index_name);
            ELSE
                w('CREATE INDEX ' || idx.index_name);
            END IF;
            w('    ON ' || idx.table_name || ' (' || v_cols || ');');
            wl;
        END LOOP;
    END;

    -- =========================================================================
    -- SECTIUNEA 6: VIEWS
    -- =========================================================================
    PROCEDURE export_views IS
        v_count NUMBER; v_col_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM user_views;
        wl; wsep('=');
        w('-- SECTIUNEA 6: VIEWS | Total: ' || v_count);
        wsep('='); wl;

        FOR rec IN (SELECT view_name, text_length FROM user_views ORDER BY view_name) LOOP
            SELECT COUNT(*) INTO v_col_count FROM user_tab_columns WHERE table_name = rec.view_name;
            wsep;
            w('-- VIEW: ' || rec.view_name);
            w('-- Nr. coloane: ' || v_col_count || ' | Lungime text: ' || rec.text_length);
            wsep;
            w('CREATE OR REPLACE VIEW ' || rec.view_name || ' AS');
            FOR src IN (SELECT text FROM user_views WHERE view_name = rec.view_name) LOOP
                UTL_FILE.PUT(v_file, src.text);
            END LOOP;
            UTL_FILE.NEW_LINE(v_file);
            w(';'); wl;
        END LOOP;
    END;

    -- =========================================================================
    -- SECTIUNEA 7: FUNCTIONS
    -- =========================================================================
    PROCEDURE export_functions IS
        v_count NUMBER; v_lines NUMBER; v_is_determ VARCHAR2(5);
    BEGIN
        SELECT COUNT(*) INTO v_count FROM user_objects WHERE object_type = 'FUNCTION';
        wl; wsep('=');
        w('-- SECTIUNEA 7: FUNCTIONS | Total: ' || v_count);
        wsep('='); wl;

        FOR rec IN (SELECT object_name, status, last_ddl_time FROM user_objects
                    WHERE object_type = 'FUNCTION' ORDER BY object_name) LOOP
            SELECT COUNT(*) INTO v_lines FROM user_source
            WHERE name = rec.object_name AND type = 'FUNCTION';

            v_is_determ := 'NU';
            FOR src IN (SELECT text FROM user_source
                WHERE name = rec.object_name AND type = 'FUNCTION'
                AND UPPER(text) LIKE '%DETERMINISTIC%' AND ROWNUM = 1) LOOP
                v_is_determ := 'DA';
            END LOOP;

            wsep;
            w('-- FUNCTIE: ' || rec.object_name);
            w('-- Nr. linii cod: ' || v_lines);
            w('-- Deterministica: ' || v_is_determ);
            w('-- Status: ' || rec.status || ' | Ultima modificare: ' || TO_CHAR(rec.last_ddl_time,'DD/MM/YYYY HH24:MI'));
            FOR param IN (SELECT argument_name, data_type, in_out, position
                          FROM user_arguments WHERE object_name = rec.object_name
                          AND package_name IS NULL ORDER BY position) LOOP
                IF param.position = 0 THEN w('--   RETURN: ' || param.data_type);
                ELSE w('--   Param: ' || NVL(param.argument_name,'?') || ' | ' || param.data_type || ' | ' || param.in_out); END IF;
            END LOOP;
            wsep;
            w('CREATE OR REPLACE');
            FOR src IN (SELECT text FROM user_source
                WHERE name = rec.object_name AND type = 'FUNCTION' ORDER BY line) LOOP
                UTL_FILE.PUT(v_file, src.text);
            END LOOP;
            UTL_FILE.NEW_LINE(v_file);
            w('/'); wl;
        END LOOP;
    END;

    -- =========================================================================
    -- SECTIUNEA 8: PROCEDURES
    -- =========================================================================
    PROCEDURE export_procedures IS
        v_count NUMBER; v_lines NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM user_objects WHERE object_type = 'PROCEDURE';
        wl; wsep('=');
        w('-- SECTIUNEA 8: PROCEDURES | Total: ' || v_count);
        wsep('='); wl;

        FOR rec IN (SELECT object_name, status, last_ddl_time FROM user_objects
                    WHERE object_type = 'PROCEDURE' ORDER BY object_name) LOOP
            SELECT COUNT(*) INTO v_lines FROM user_source
            WHERE name = rec.object_name AND type = 'PROCEDURE';

            wsep;
            w('-- PROCEDURA: ' || rec.object_name);
            w('-- Nr. linii cod: ' || v_lines);
            w('-- Status: ' || rec.status || ' | Ultima modificare: ' || TO_CHAR(rec.last_ddl_time,'DD/MM/YYYY HH24:MI'));
            FOR param IN (SELECT argument_name, data_type, in_out, position
                          FROM user_arguments WHERE object_name = rec.object_name
                          AND package_name IS NULL ORDER BY position) LOOP
                w('--   Param: ' || NVL(param.argument_name,'?') || ' | ' || NVL(param.data_type,'?') || ' | ' || param.in_out);
            END LOOP;
            wsep;
            w('CREATE OR REPLACE');
            FOR src IN (SELECT text FROM user_source
                WHERE name = rec.object_name AND type = 'PROCEDURE' ORDER BY line) LOOP
                UTL_FILE.PUT(v_file, src.text);
            END LOOP;
            UTL_FILE.NEW_LINE(v_file);
            w('/'); wl;
        END LOOP;
    END;

    -- =========================================================================
    -- SECTIUNEA 9: PACKAGES
    -- =========================================================================
    PROCEDURE export_packages IS
        v_count NUMBER; v_spec_lines NUMBER; v_body_lines NUMBER; v_sub_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM user_objects WHERE object_type = 'PACKAGE';
        wl; wsep('=');
        w('-- SECTIUNEA 9: PACKAGES | Total: ' || v_count);
        wsep('='); wl;

        FOR rec IN (SELECT object_name, status, last_ddl_time FROM user_objects
                    WHERE object_type = 'PACKAGE' ORDER BY object_name) LOOP
            SELECT COUNT(*) INTO v_spec_lines FROM user_source WHERE name = rec.object_name AND type = 'PACKAGE';
            SELECT COUNT(*) INTO v_body_lines FROM user_source WHERE name = rec.object_name AND type = 'PACKAGE BODY';
            SELECT COUNT(*) INTO v_sub_count FROM user_procedures
            WHERE object_name = rec.object_name AND procedure_name IS NOT NULL;

            wsep;
            w('-- PACHET: ' || rec.object_name);
            w('-- Linii spec: ' || v_spec_lines || ' | Linii body: ' || v_body_lines);
            w('-- Subprograme: ' || v_sub_count);
            w('-- Status: ' || rec.status || ' | Ultima modificare: ' || TO_CHAR(rec.last_ddl_time,'DD/MM/YYYY HH24:MI'));
            FOR sub IN (SELECT procedure_name FROM user_procedures
                        WHERE object_name = rec.object_name AND procedure_name IS NOT NULL
                        ORDER BY procedure_name) LOOP
                DECLARE v_has_ret NUMBER; BEGIN
                    SELECT COUNT(*) INTO v_has_ret FROM user_arguments
                    WHERE package_name = rec.object_name AND object_name = sub.procedure_name AND position = 0;
                    w('--   ' || sub.procedure_name || CASE WHEN v_has_ret>0 THEN ' [FUNCTION]' ELSE ' [PROCEDURE]' END);
                END;
            END LOOP;
            wsep;

            w('-- ** PACKAGE SPEC **');
            w('CREATE OR REPLACE');
            FOR src IN (SELECT text FROM user_source WHERE name = rec.object_name AND type = 'PACKAGE' ORDER BY line) LOOP
                UTL_FILE.PUT(v_file, src.text);
            END LOOP;
            UTL_FILE.NEW_LINE(v_file);
            w('/'); wl;

            IF v_body_lines > 0 THEN
                w('-- ** PACKAGE BODY **');
                w('CREATE OR REPLACE');
                FOR src IN (SELECT text FROM user_source WHERE name = rec.object_name AND type = 'PACKAGE BODY' ORDER BY line) LOOP
                    UTL_FILE.PUT(v_file, src.text);
                END LOOP;
                UTL_FILE.NEW_LINE(v_file);
                w('/'); wl;
            END IF;
        END LOOP;
    END;

    -- =========================================================================
    -- SECTIUNEA 10: TRIGGERS
    -- =========================================================================
    PROCEDURE export_triggers IS
        v_count NUMBER; v_lines NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM user_triggers;
        wl; wsep('=');
        w('-- SECTIUNEA 10: TRIGGERS | Total: ' || v_count);
        wsep('='); wl;

        FOR rec IN (
            SELECT trigger_name, table_name, trigger_type, triggering_event, status, base_object_type
            FROM user_triggers
            ORDER BY CASE trigger_type WHEN 'BEFORE EACH ROW' THEN 1 WHEN 'AFTER EACH ROW' THEN 2
                WHEN 'BEFORE STATEMENT' THEN 3 WHEN 'AFTER STATEMENT' THEN 4 ELSE 5 END, trigger_name
        ) LOOP
            SELECT COUNT(*) INTO v_lines FROM user_source WHERE name = rec.trigger_name AND type = 'TRIGGER';
            wsep;
            w('-- TRIGGER: ' || rec.trigger_name);
            w('-- Tabel: ' || NVL(rec.table_name,'SCHEMA'));
            w('-- Tip: ' || rec.trigger_type || ' | Eveniment: ' || rec.triggering_event);
            w('-- Tip obiect: ' || rec.base_object_type || ' | Status: ' || rec.status);
            w('-- Nr. linii cod: ' || v_lines);
            wsep;
            w('CREATE OR REPLACE');
            FOR src IN (SELECT text FROM user_source WHERE name = rec.trigger_name AND type = 'TRIGGER' ORDER BY line) LOOP
                UTL_FILE.PUT(v_file, src.text);
            END LOOP;
            UTL_FILE.NEW_LINE(v_file);
            w('/');
            IF rec.status = 'DISABLED' THEN w('ALTER TRIGGER ' || rec.trigger_name || ' DISABLE;'); END IF;
            wl;
        END LOOP;
    END;

    -- =========================================================================
    -- SECTIUNEA 11: DATE (INSERT statements)
    -- =========================================================================
    PROCEDURE export_data IS
        v_row_count NUMBER;
        v_has_clob  BOOLEAN;
        v_col_list  VARCHAR2(4000);
        v_col_exprs VARCHAR2(32767);
        v_select    VARCHAR2(32767);
        v_first     BOOLEAN;
        TYPE t_cur IS REF CURSOR;
        c_cur t_cur;
        v_insert_line VARCHAR2(32767);
        v_insert_prefix VARCHAR2(500);
    BEGIN
        wl; wsep('=');
        w('-- SECTIUNEA 11: DATE (INSERT statements)');
        w('-- Triggerele sunt dezactivate temporar pentru import');
        wsep('='); wl;

        -- Dezactiveaza triggere
        FOR trg IN (SELECT trigger_name FROM user_triggers ORDER BY trigger_name) LOOP
            w('ALTER TRIGGER ' || trg.trigger_name || ' DISABLE;');
        END LOOP;
        wl;

        FOR tab IN (
            SELECT t.table_name,
                   (SELECT COUNT(*) FROM user_constraints c
                    WHERE c.table_name = t.table_name AND c.constraint_type = 'R') AS nr_fk
            FROM user_tables t
            WHERE t.table_name NOT IN (SELECT table_name FROM user_nested_tables)
            ORDER BY nr_fk, t.table_name
        ) LOOP
            v_has_clob := FALSE;
            BEGIN
                DECLARE v_d VARCHAR2(1); BEGIN
                    SELECT 'Y' INTO v_d FROM user_tab_columns
                    WHERE table_name = tab.table_name AND data_type IN ('CLOB','NCLOB','BLOB') AND ROWNUM = 1;
                    v_has_clob := TRUE;
                EXCEPTION WHEN NO_DATA_FOUND THEN NULL; END;
            END;

            EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM "' || tab.table_name || '"' INTO v_row_count;

            IF v_has_clob THEN
                w('-- TABEL: ' || tab.table_name || ' | ' || v_row_count || ' randuri | CLOB - inserati manual coloana IMPRESII_STUDENT');
            END IF;

            IF v_row_count = 0 THEN
                w('-- TABEL: ' || tab.table_name || ' | 0 randuri');
                wl;
                GOTO next_table;
            END IF;

            -- Construim lista coloane (fara CLOB/BLOB si fara coloane nested)
            v_col_list := '';
            v_col_exprs := '';
            v_first := TRUE;

            FOR col IN (
                SELECT column_name, data_type FROM user_tab_columns
                WHERE table_name = tab.table_name
                  AND data_type IN ('NUMBER','VARCHAR2','CHAR','NVARCHAR2','NCHAR',
                                    'DATE','TIMESTAMP','FLOAT','BINARY_FLOAT','BINARY_DOUBLE')
                ORDER BY column_id
            ) LOOP
                IF NOT v_first THEN
                    v_col_list := v_col_list || ', ';
                    v_col_exprs := v_col_exprs || ' || '','' || ';
                END IF;
                v_first := FALSE;
                v_col_list := v_col_list || col.column_name;

                IF col.data_type IN ('VARCHAR2','CHAR','NVARCHAR2','NCHAR') THEN
                    v_col_exprs := v_col_exprs
                        || 'NVL2(' || col.column_name
                        || ',CHR(39)||REPLACE(' || col.column_name || ',CHR(39),CHR(39)||CHR(39))||CHR(39)'
                        || ',''NULL'')';
                ELSIF col.data_type = 'DATE' THEN
                    v_col_exprs := v_col_exprs
                        || 'NVL2(' || col.column_name
                        || ',''TO_DATE(''||CHR(39)||TO_CHAR(' || col.column_name
                        || ',''DD/MM/YYYY HH24:MI:SS'')||CHR(39)||'','
                        || '''||CHR(39)||''DD/MM/YYYY HH24:MI:SS''||CHR(39)||'')'''
                        || ',''NULL'')';
                ELSIF col.data_type LIKE 'TIMESTAMP%' THEN
                    v_col_exprs := v_col_exprs
                        || 'NVL2(' || col.column_name
                        || ',''TO_TIMESTAMP(''||CHR(39)||TO_CHAR(' || col.column_name
                        || ',''DD/MM/YYYY HH24:MI:SS.FF'')||CHR(39)||'','
                        || '''||CHR(39)||''DD/MM/YYYY HH24:MI:SS.FF''||CHR(39)||'')'''
                        || ',''NULL'')';
                ELSE
                    v_col_exprs := v_col_exprs || 'NVL(TO_CHAR(' || col.column_name || '),''NULL'')';
                END IF;
            END LOOP;

            IF v_col_list IS NOT NULL THEN
                w('-- TABEL: ' || tab.table_name || ' | ' || v_row_count || ' randuri');
                v_insert_prefix := 'INSERT INTO ' || tab.table_name || ' (' || v_col_list || ') VALUES (';
                v_select := 'SELECT ''' || REPLACE(v_insert_prefix,'''','''''')
                    || ''' || ' || v_col_exprs || ' || '');'' FROM ' || tab.table_name;

                BEGIN
                    OPEN c_cur FOR v_select;
                    LOOP
                        FETCH c_cur INTO v_insert_line;
                        EXIT WHEN c_cur%NOTFOUND;
                        UTL_FILE.PUT_LINE(v_file, v_insert_line);
                    END LOOP;
                    CLOSE c_cur;
                EXCEPTION WHEN OTHERS THEN
                    IF c_cur%ISOPEN THEN CLOSE c_cur; END IF;
                    w('-- EROARE la generare INSERT pentru ' || tab.table_name || ': ' || SQLERRM);
                END;
                wl;
            END IF;

            <<next_table>>
            NULL;
        END LOOP;

        -- Repopulare LISTA_PRIETENI nested table
        wl;
        w('-- Repopulare LISTA_PRIETENI (nested table) dupa import STUDENTI si PRIETENI');
        w('DECLARE');
        w('    v_lista tip_lista_prieteni_nt;');
        w('    v_nr_prieteni NUMBER;');
        w('BEGIN');
        w('    FOR s IN (SELECT id FROM studenti) LOOP');
        w('        v_lista := tip_lista_prieteni_nt();');
        w('        FOR an_curent IN 1..3 LOOP');
        w('            SELECT COUNT(*) INTO v_nr_prieteni');
        w('            FROM prieteni p JOIN studenti st ON');
        w('                (p.id_student1 = s.id AND p.id_student2 = st.id) OR');
        w('                (p.id_student2 = s.id AND p.id_student1 = st.id)');
        w('            WHERE st.an = an_curent;');
        w('            v_lista.EXTEND;');
        w('            v_lista(v_lista.LAST) := tip_pereche_varray(an_curent, v_nr_prieteni);');
        w('        END LOOP;');
        w('        UPDATE studenti SET lista_prieteni = v_lista WHERE id = s.id;');
        w('    END LOOP;');
        w('    COMMIT;');
        w('END;');
        w('/');
        wl;

        -- Reactiveaza triggere
        FOR trg IN (SELECT trigger_name, status FROM user_triggers ORDER BY trigger_name) LOOP
            IF trg.status = 'ENABLED' THEN
                w('ALTER TRIGGER ' || trg.trigger_name || ' ENABLE;');
            END IF;
        END LOOP;
        wl;
        w('COMMIT;');
    END;

    -- =========================================================================
    -- SUMAR FINAL
    -- =========================================================================
    PROCEDURE export_summary IS
        v_t NUMBER; v_v NUMBER; v_s NUMBER; v_ty NUMBER;
        v_p NUMBER; v_f NUMBER; v_pk NUMBER; v_tr NUMBER; v_i NUMBER;
        v_total_rows NUMBER := 0; v_cnt NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_t FROM user_tables;
        SELECT COUNT(*) INTO v_v FROM user_views;
        SELECT COUNT(*) INTO v_s FROM user_sequences;
        SELECT COUNT(*) INTO v_ty FROM user_types;
        SELECT COUNT(*) INTO v_p FROM user_objects WHERE object_type = 'PROCEDURE';
        SELECT COUNT(*) INTO v_f FROM user_objects WHERE object_type = 'FUNCTION';
        SELECT COUNT(*) INTO v_pk FROM user_objects WHERE object_type = 'PACKAGE';
        SELECT COUNT(*) INTO v_tr FROM user_triggers;
        SELECT COUNT(*) INTO v_i FROM user_indexes
        WHERE index_name NOT IN (SELECT constraint_name FROM user_constraints WHERE constraint_type IN ('P','U'))
          AND index_name NOT LIKE 'SYS_IL%' AND index_name NOT LIKE 'SYS_FK%'
          AND index_type != 'LOB';

        FOR tab IN (SELECT table_name FROM user_tables) LOOP
            BEGIN
                EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM "' || tab.table_name || '"' INTO v_cnt;
                v_total_rows := v_total_rows + v_cnt;
            EXCEPTION WHEN OTHERS THEN NULL; END;
        END LOOP;

        wl; wsep('=');
        w('-- SUMAR EXPORT COMPLET');
        w('-- Schema: ' || v_schema);
        w('-- Data export: ' || TO_CHAR(SYSDATE,'DD/MM/YYYY HH24:MI:SS'));
        wsep('=');
        w('-- Tipuri (TYPE):           ' || LPAD(v_ty,5));
        w('-- Secvente (SEQUENCE):     ' || LPAD(v_s,5));
        w('-- Tabele (TABLE):           ' || LPAD(v_t,5) || '  (total ' || v_total_rows || ' randuri)');
        w('-- View-uri (VIEW):          ' || LPAD(v_v,5));
        w('-- Indecsi aditional:        ' || LPAD(v_i,5));
        w('-- Functii (FUNCTION):       ' || LPAD(v_f,5));
        w('-- Proceduri (PROCEDURE):    ' || LPAD(v_p,5));
        w('-- Pachete (PACKAGE):        ' || LPAD(v_pk,5));
        w('-- Trigger-e (TRIGGER):      ' || LPAD(v_tr,5));
        wsep('=');
        w('-- TOTAL OBIECTE: ' || (v_ty+v_s+v_t+v_v+v_i+v_f+v_p+v_pk+v_tr));
        wsep('=');
        wl;
        w('-- ============================================================');
        w('-- SFARSIT FISIER EXPORT');
        w('-- ============================================================');
    END;

-- =========================================================================
-- BLOC PRINCIPAL
-- =========================================================================
BEGIN
    -- Deschide fisierul
    v_file := UTL_FILE.FOPEN('EXPORT_DIR', 'export_student.sql', 'W', 32767);

    -- Header
    w('-- ============================================================');
    w('-- FISIER EXPORT COMPLET BAZA DE DATE ORACLE');
    w('-- Schema sursa: ' || v_schema);
    w('-- Data export: ' || TO_CHAR(SYSDATE,'DD/MM/YYYY HH24:MI:SS'));
    w('-- Generat prin PL/SQL cu UTL_FILE (fara DBMS_METADATA)');
    w('-- Target import: StudentClona');
    w('-- ============================================================');
    wl;
    w('SET DEFINE OFF');
    w('SET FEEDBACK OFF');
    w('SET SERVEROUTPUT ON SIZE UNLIMITED');
    wl;

    -- Ruleaza toate sectiunile
    export_types;
    export_sequences;
    export_tables;
    export_fks;
    export_indexes;
    export_views;
    export_functions;
    export_procedures;
    export_packages;
    export_triggers;
    export_data;
    export_summary;

    -- Inchide fisierul
    UTL_FILE.FCLOSE(v_file);

    DBMS_OUTPUT.PUT_LINE('SUCCES! Fisierul a fost scris la: C:\export_oracle\export_student.sql');

EXCEPTION
    WHEN OTHERS THEN
        IF UTL_FILE.IS_OPEN(v_file) THEN UTL_FILE.FCLOSE(v_file); END IF;
        DBMS_OUTPUT.PUT_LINE('EROARE: ' || SQLERRM);
        RAISE;
END;
/
