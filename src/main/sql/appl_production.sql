﻿DROP SCHEMA IF EXISTS appl_production CASCADE;
CREATE SCHEMA appl_production;

CREATE TYPE appl_production.production AS (production_id INTEGER, name CHARACTER VARYING, sysname CHARACTER VARYING, parent_production INTEGER, home_url CHARACTER VARYING);

/**
 * Возвращает ID продукта по его системному имени.
 */
CREATE OR REPLACE FUNCTION appl_production.get_production_by_sysname(sysname$ CHARACTER VARYING)
	RETURNS INTEGER AS
$BODY$
DECLARE
	production_id$ INTEGER;
BEGIN
	SELECT production_id INTO production_id$ FROM carabi_kernel.software_production WHERE sysname = sysname$;
	IF production_id$ IS NULL THEN
		RAISE EXCEPTION 'Unknown production: %', sysname$;
	END IF;
	RETURN production_id$;
END;
$BODY$
	LANGUAGE plpgsql VOLATILE;


/**
 * Проверка, доступен ли пользователю программный продукт.
 * Чтобы продукт был доступен, необходимо подключение к содержащей его схеме (если продукт зависит от схемы)
 * и право на использование (если такое право существует).
 * Доступность продукта на сервере пока не проверяется.
 */
CREATE OR REPLACE FUNCTION appl_production.production_is_available(token$ CHARACTER VARYING, production_sysname$ CHARACTER VARYING)
	RETURNS BOOLEAN AS
$BODY$
DECLARE
	user_id$ BIGINT;
	schema_id$ INTEGER;
BEGIN
	SELECT user_id, schema_id into user_id$, schema_id$ FROM carabi_kernel.user_logon WHERE token = token$;
	IF user_id$ IS NULL THEN
		RAISE EXCEPTION 'Unknown token: %', token$;
	END IF;
	RETURN appl_production.production_is_available(user_id$, schema_id$, appl_production.get_production_by_sysname(production_sysname$));
END;
$BODY$
	LANGUAGE plpgsql VOLATILE;


/**
 * Проверка, доступен ли пользователю программный продукт.
 * Чтобы продукт был доступен, необходимо подключение к содержащей его схеме (если продукт зависит от схемы)
 * и право на использование (если такое право существует).
 * Доступность продукта на сервере пока не проверяется.
 */
CREATE OR REPLACE FUNCTION appl_production.production_is_available(user_id$ BIGINT, schema_id$ INTEGER, production_id$ INTEGER)
	RETURNS BOOLEAN AS
$BODY$
DECLARE
	permission_to_use$ INTEGER;
	schema_independent$ BOOLEAN;
	appserver_independent$ BOOLEAN;
	count_records$ INTEGER;
	has_next$ BOOLEAN;
BEGIN
	--Получаем основные данные о продукте, проверяя его на существование
	SELECT TRUE AS has_next, permission_to_use, schema_independent, appserver_independent
	INTO has_next$, permission_to_use$, schema_independent$, appserver_independent$
	FROM carabi_kernel.software_production WHERE production_id = production_id$;
	IF has_next$ IS NULL THEN
		RAISE EXCEPTION 'Unknown production: %', production_id$;
	END IF;
	--если задано право использования -- пользователь должен его иметь
	IF permission_to_use$ IS NOT NULL THEN
		IF NOT appl_permissions.user_has_permission(user_id$, permission_to_use$) THEN
			RETURN FALSE;
		END IF;
	END IF;
	IF NOT schema_independent$ THEN --если продукт может не работать на текущей схеме -- смотрим, работает ли
		SELECT count(*) INTO count_records$ FROM carabi_kernel.product_on_schema
		WHERE product_on_schema.product_id = production_id$ AND product_on_schema.schema_id = schema_id$;
		IF count_records$ = 0 THEN --записи о работоспособности нет
			RETURN FALSE;
		END IF;
	END IF;
	/*Работоспособность на сервере временно игнорируется*/
	--Последнее условие: доступность родительского продукта
	RETURN appl_production.parent_production_is_available(user_id$, schema_id$, production_id$);
END;
$BODY$
	LANGUAGE plpgsql VOLATILE;


/**
 * Проверка, доступен ли пользователю вышележащий продукт
 * Для parent_production, если оно есть, вызывается функция production_is_available.
 * Если нет -- возвращается true.
 */
CREATE OR REPLACE FUNCTION appl_production.parent_production_is_available(user_id$ BIGINT, schema_id$ INTEGER, production_id$ INTEGER)
	RETURNS BOOLEAN AS
$BODY$
DECLARE
	parent_production$ INTEGER;
BEGIN
	SELECT parent_production INTO parent_production$ FROM carabi_kernel.software_production WHERE production_id = production_id$;
	IF parent_production$ IS NULL THEN
		RETURN TRUE;
	ELSE
		RETURN appl_production.production_is_available(user_id$, schema_id$, parent_production$);
	END IF;
END;
$BODY$
	LANGUAGE plpgsql VOLATILE;


/**
 * Возвращает все продукты ПО, доступные пользователю в данный момент
 */
CREATE OR REPLACE FUNCTION appl_production.get_available_production(user_id$ BIGINT, schema_id$ INTEGER)
	RETURNS SETOF appl_production.production AS
$BODY$
DECLARE
	root_productions REFCURSOR;
	has_next$ BOOLEAN;
	production_id$ INTEGER;
	name$ CHARACTER VARYING;
	sysname$ CHARACTER VARYING;
	home_url$ CHARACTER VARYING;
	parent_production$ INTEGER;
BEGIN
	OPEN root_productions FOR --Выбираем видимые родительские продукты
		SELECT true as has_next, production_id, name, sysname, home_url$, parent_production
		FROM carabi_kernel.software_production WHERE parent_production IS NULL AND visible;
	FETCH root_productions INTO has_next$, production_id$, name$, sysname$, home_url$, parent_production$;
	WHILE has_next$ LOOP
		IF appl_production.production_is_available(user_id$, schema_id$, production_id$) THEN
			RETURN NEXT (production_id$, name$, sysname$, parent_production$, home_url$);
			RETURN QUERY SELECT * FROM appl_production.get_available_production(user_id$, schema_id$, production_id$);
		END IF;
		FETCH root_productions INTO has_next$, production_id$, name$, sysname$, parent_production$;
	END LOOP;
END;
$BODY$
	LANGUAGE plpgsql VOLATILE;


/**
 * Возвращает все дочерние продукты ПО под данным родительским
 */
CREATE OR REPLACE FUNCTION appl_production.get_available_production(user_id$ BIGINT, schema_id$ INTEGER, parent_production_id$ INTEGER)
	RETURNS SETOF appl_production.production AS
$BODY$
DECLARE
	child_productions REFCURSOR;
	has_next$ BOOLEAN;
	production_id$ INTEGER;
	name$ CHARACTER VARYING;
	sysname$ CHARACTER VARYING;
	home_url$ CHARACTER VARYING;
	parent_production$ INTEGER;
BEGIN
	OPEN child_productions FOR --Выбираем видимые дочерние продукты
		SELECT true as has_next, production_id, name, sysname, home_url, parent_production
		FROM carabi_kernel.software_production WHERE parent_production = parent_production_id$ AND visible;
	FETCH child_productions INTO has_next$, production_id$, name$, sysname$, home_url$, parent_production$;
	WHILE has_next$ LOOP
		IF appl_production.production_is_available(user_id$, schema_id$, production_id$) THEN
			RETURN NEXT (production_id$, name$, sysname$, parent_production$, home_url$);
			RETURN QUERY SELECT * FROM appl_production.get_available_production(user_id$, schema_id$, production_id$);
		END IF;
		FETCH child_productions INTO has_next$, production_id$, name$, sysname$, home_url$, parent_production$;
	END LOOP;
END;
$BODY$
	LANGUAGE plpgsql VOLATILE;


/**
 * Возвращает все продукты ПО, доступные пользователю в данный момент
 */
CREATE OR REPLACE FUNCTION appl_production.get_available_production(token$ CHARACTER VARYING)
	RETURNS SETOF appl_production.production AS
$BODY$
DECLARE
	user_id$ BIGINT;
	schema_id$ INTEGER;
BEGIN
	--Из сессии берём пользователя и текущую схему
	SELECT user_id, schema_id into user_id$, schema_id$ FROM carabi_kernel.user_logon WHERE token = token$;
	IF user_id$ IS NULL THEN
		RAISE EXCEPTION 'Unknown token: %', token$;
	END IF;
	RETURN QUERY SELECT * FROM appl_production.get_available_production(user_id$, schema_id$);
END;
$BODY$
	LANGUAGE plpgsql VOLATILE;


/**
 * Возвращает все дочерние продукты ПО под данным родительским, доступные пользователю в данный момент
 */
CREATE OR REPLACE FUNCTION appl_production.get_available_production(token$ CHARACTER VARYING, production_sysname$ CHARACTER VARYING)
	RETURNS SETOF appl_production.production AS
$BODY$
DECLARE
	user_id$ BIGINT;
	schema_id$ INTEGER;
BEGIN
	--Из сессии берём пользователя и текущую схему
	SELECT user_id, schema_id into user_id$, schema_id$ FROM carabi_kernel.user_logon WHERE token = token$;
	IF user_id$ IS NULL THEN
		RAISE EXCEPTION 'Unknown token: %', token$;
	END IF;
	RETURN QUERY SELECT * FROM appl_production.get_available_production(user_id$, schema_id$, appl_production.get_production_by_sysname(production_sysname$));
END;
$BODY$
	LANGUAGE plpgsql VOLATILE;


/**
 * Возвращает все дочерние продукты ПО под данным родительским, доступные пользователю в данный момент
 */
CREATE OR REPLACE FUNCTION appl_production.get_available_production(token$ CHARACTER VARYING, production_id$ INTEGER)
	RETURNS SETOF appl_production.production AS
$BODY$
DECLARE
	user_id$ BIGINT;
	schema_id$ INTEGER;
BEGIN
	--Из сессии берём пользователя и текущую схему
	SELECT user_id, schema_id into user_id$, schema_id$ FROM carabi_kernel.user_logon WHERE token = token$;
	IF user_id$ IS NULL THEN
		RAISE EXCEPTION 'Unknown token: %', token$;
	END IF;
	RETURN QUERY SELECT * FROM appl_production.get_available_production(user_id$, schema_id$, production_id$);
END;
$BODY$
	LANGUAGE plpgsql VOLATILE;