create or replace function INSERT_INTO_USER_PERMISSION()
	returns integer as
$BODY$

declare
	ADMINISTRATING_ID$ INTEGER;

	ADMINISTRATING_PERMISSIONS_ID$ INTEGER;
	ADMINISTRATING_PERMISSIONS_VIEW_ID$ INTEGER;
	ADMINISTRATING_PERMISSIONS_EDIT_ID$ INTEGER;
	ADMINISTRATING_PERMISSIONS_ASSIGN_ID$ INTEGER;

	ADMINISTRATING_ROLES_ID$ INTEGER;
	ADMINISTRATING_ROLES_VIEW_ID$ INTEGER;
	ADMINISTRATING_ROLES_EDIT_ID$ INTEGER;
	ADMINISTRATING_ROLES_ASSIGN_ID$ INTEGER;

	ADMINISTRATOR_ID$ INTEGER;
begin
	set SEARCH_PATH to CARABI_KERNEL;
	ADMINISTRATOR_ID$ := 1;

	insert into USER_PERMISSION(PERMISSION_ID, NAME, SYSNAME, DESCRIPTION, PARENT_PERMISSION)
	values(DEFAULT, 'Управление правами', 'ADMINISTRATING-PERMISSIONS', 'Просмотр и редактирование прав доступа', ADMINISTRATING_ID$)
	returning PERMISSION_ID into ADMINISTRATING_PERMISSIONS_ID$;

	insert into USER_PERMISSION(PERMISSION_ID, NAME, SYSNAME, DESCRIPTION, PARENT_PERMISSION)
	values(DEFAULT, 'Просматривать права', 'ADMINISTRATING-PERMISSIONS-VIEW', 'Просматривать права доступа', ADMINISTRATING_PERMISSIONS_ID$)
	returning PERMISSION_ID into ADMINISTRATING_PERMISSIONS_VIEW_ID$;

	insert into USER_PERMISSION(PERMISSION_ID, NAME, SYSNAME, DESCRIPTION, PARENT_PERMISSION)
	values(DEFAULT, 'Редактировать права', 'ADMINISTRATING-PERMISSIONS-EDIT', 'Редактировать права доступа', ADMINISTRATING_PERMISSIONS_ID$)
	returning PERMISSION_ID into ADMINISTRATING_PERMISSIONS_EDIT_ID$;

	insert into USER_PERMISSION(PERMISSION_ID, NAME, SYSNAME, DESCRIPTION, PARENT_PERMISSION)
	values(DEFAULT, 'Выдавать права', 'ADMINISTRATING-PERMISSIONS-ASSIGN', 'Выдавать права пользователям и ролям', ADMINISTRATING_PERMISSIONS_ID$)
	returning PERMISSION_ID into ADMINISTRATING_PERMISSIONS_ASSIGN_ID$;


	insert into USER_PERMISSION(PERMISSION_ID, NAME, SYSNAME, DESCRIPTION, PARENT_PERMISSION)
	values(DEFAULT, 'Управление ролями', 'ADMINISTRATING-ROLES', 'Просмотр и редактирование пользовательских ролей', ADMINISTRATING_ID$)
	returning PERMISSION_ID into ADMINISTRATING_ROLES_ID$;

	insert into USER_PERMISSION(PERMISSION_ID, NAME, SYSNAME, DESCRIPTION, PARENT_PERMISSION)
	values(DEFAULT, 'Просматривать роли', 'ADMINISTRATING-ROLES-VIEW', 'Просматривать роли', ADMINISTRATING_ROLES_ID$)
	returning PERMISSION_ID into ADMINISTRATING_ROLES_VIEW_ID$;

	insert into USER_PERMISSION(PERMISSION_ID, NAME, SYSNAME, DESCRIPTION, PARENT_PERMISSION)
	values(DEFAULT, 'Редактировать роли', 'ADMINISTRATING-ROLES-EDIT', 'Редактировать роли', ADMINISTRATING_ROLES_ID$)
	returning PERMISSION_ID into ADMINISTRATING_ROLES_EDIT_ID$;

	insert into USER_PERMISSION(PERMISSION_ID, NAME, SYSNAME, DESCRIPTION, PARENT_PERMISSION)
	values(DEFAULT, 'Назначать роли', 'ADMINISTRATING-ROLES-ASSIGN', 'Назначать роли пользователям', ADMINISTRATING_ROLES_ID$)
	returning PERMISSION_ID into ADMINISTRATING_ROLES_ASSIGN_ID$;


	insert into ROLE_HAS_PERMISSION(role_id, permission_id) values(ADMINISTRATOR_ID$, ADMINISTRATING_PERMISSIONS_ID$);
	insert into ROLE_HAS_PERMISSION(role_id, permission_id) values(ADMINISTRATOR_ID$, ADMINISTRATING_PERMISSIONS_VIEW_ID$);
	insert into ROLE_HAS_PERMISSION(role_id, permission_id) values(ADMINISTRATOR_ID$, ADMINISTRATING_PERMISSIONS_EDIT_ID$);
	insert into ROLE_HAS_PERMISSION(role_id, permission_id) values(ADMINISTRATOR_ID$, ADMINISTRATING_PERMISSIONS_ASSIGN_ID$);

	insert into ROLE_HAS_PERMISSION(role_id, permission_id) values(ADMINISTRATOR_ID$, ADMINISTRATING_ROLES_ID$);
	insert into ROLE_HAS_PERMISSION(role_id, permission_id) values(ADMINISTRATOR_ID$, ADMINISTRATING_ROLES_VIEW_ID$);
	insert into ROLE_HAS_PERMISSION(role_id, permission_id) values(ADMINISTRATOR_ID$, ADMINISTRATING_ROLES_EDIT_ID$);
	insert into ROLE_HAS_PERMISSION(role_id, permission_id) values(ADMINISTRATOR_ID$, ADMINISTRATING_ROLES_ASSIGN_ID$);

	return 0;
end;

$BODY$
	LANGUAGE PLPGSQL VOLATILE;


SELECT INSERT_INTO_USER_PERMISSION();

--set SEARCH_PATH to DEFAULT;

--DROP FUNCTION INSERT_INTO_USER_PERMISSION();
