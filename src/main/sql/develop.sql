--connect 'jdbc:derby:./test;create=true';

create schema CARABI;
set schema = CARABI;

call SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY('derby.connection.requireAuthentication','true');
call SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY('derby.user.carabi','password');
call SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY('derby.user.CARABI','password');

create table DUAL(DUMMY integer);
insert into DUAL values(0);

/**
 * Базы данных Oracle, с которыми работает система.
 * URL, логин и пароль к каждой базе хранятся в настройках Java application server
 * и ищутся через JNDI-имя.
 */
create table CONNECTION_SCHEMA (
	SCHEMA_ID integer primary key generated by default as identity,
	JNDI varchar(256),-- unique not null, --JNDI-имя пула
	ADDRESS varchar(256),
	LOGIN varchar(256),
	PASSWORD varchar(256),
	NAME varchar(256) not null, --Название схемы БД
	SYSNAME varchar(256) unique not null, --Системное имя схемы -- параметр при подключении клиентов
	DESCRIPTION varchar(1024)
);

/*insert into CONNECTION_SCHEMA (JNDI, NAME, SYSNAME) values
	('jdbc/carabi', 'carabi all', 'carabi'),
	('jdbc/veneta', 'Венета', 'veneta'),
;*/

/**
 * Прикладной сервер (Glassfish + Eventer)
 */
create table APPSERVER (
	APPSERVER_ID integer primary key generated by default as identity,
	NAME varchar(256), --Краткое наименование сервера для сис. администратора
	SYSNAME varchar(256) unique not null, --Системное имя сервера, по которому они могут опознать друг друга и себя
	COMPUTER varchar(256) not null, -- адрес компьюрера (IP-адрес или домен)
	CONTEXTROOT varchar(256), --имя развёрнутого в Glassfish приложения Carabi Server
	GLASSFISH_PORT integer default 8080,
	EVENTER_PORT integer default 9234,
	DESCRIPTION varchar(32000), --Описание, если требуется
	IS_MASTER integer default 0
);
create index APPSERVER_IS_MASTER on APPSERVER(IS_MASTER);
/*
INSERT INTO APPSERVER (APPSERVER_ID, NAME, SYSNAME, COMPUTER, CONTEXTROOT) VALUES
	(1,'develop','office','127.0.0.1','carabiserver');
*/

/**
 * Пользовательский файл на текущем сервере
 */
create table FILE (
	FILE_ID bigint primary key generated by default as identity,
	NAME varchar(1024) not null,
	MIME_TYPE varchar(64),
	CONTENT_ADDRESS varchar(1024) unique,
	CONTENT_LENGTH bigint
);
create index FILE_NAME on FILE(NAME);

/**
 * Миниатюры картинок
 */
create table THUMBNAIL (
	ORIGINAL_ID bigint references FILE(FILE_ID) on delete cascade,
	WIDTH integer,
	HEIGHT integer,
	THUMBNAIL_ID bigint references FILE(FILE_ID) on delete cascade,
	primary key (ORIGINAL_ID, WIDTH, HEIGHT)
);

/**
 * Клиент компании Караби.
 * Может иметь доступ к различным базам данных, при входе в базу создаётся запись USER_LOGON
 */
create table CARABI_USER (
	USER_ID bigint primary key generated by default as identity,
	LOGIN varchar(256) not null unique, --логин
	PASSWORD varchar(64) not null default '==', --зашифрованный пароль
	FIRSTNAME varchar(1024), --имя
	MIDDLENAME varchar(1024), --отчество
	LASTNAME varchar(1024), --фамилия
	ROLE varchar(1024), --описание роли в компании/системе
	DEPARTMENT varchar(1024), --подразделение
	--Основная БД Oracle
	DEFAULT_SCHEMA_ID integer references CONNECTION_SCHEMA (SCHEMA_ID) on delete set null,
	--основной сервер с БД Derby для чата
	MAIN_SERVER_ID integer references APPSERVER (APPSERVER_ID) on delete set null,
	AVATAR bigint references FILE (FILE_ID), --файл с аватаром
	LASTACTIVE timestamp, --время последней активности
	SHOW_ONLINE integer default 1 --при нулевом значении не показывать онлайн (в чате и др.)

);

/**
 * Число входов пользователя на каждый сервер.
 * Если число входов на некоторый сервер больше, чем на 
 * указанный в CARABI_USER.MAIN_SERVER_ID -- возможно, его следует сделать основным
 * и перенести на него данные.
 */
create table USER_AT_SERVER_ENTER (
	USER_ID bigint references CARABI_USER(USER_ID) on delete cascade,
	SERVER_ID integer references APPSERVER (APPSERVER_ID) on delete cascade,
	NUMBER_OF_ENTERS bigint default 0,
	primary key (USER_ID, SERVER_ID)
);
/*insert into CARABI_USER (LOGIN, FIRSTNAME, MIDDLENAME, LASTNAME, DEFAULT_SCHEMA_ID) values
	('kop', 'Александр', 'Дмитриевич', 'Копилов', 1),
;*/

/**
 *Схемы, к которым клиент имеет право подключаться
 */
create table ALLOWED_SCHEMAS (
	SCHEMA_ID integer references CONNECTION_SCHEMA (SCHEMA_ID) on delete cascade,
	USER_ID bigint references CARABI_USER (USER_ID) on delete cascade,
	primary key(SCHEMA_ID, USER_ID)
);
/*insert into ALLOWED_SCHEMAS (SCHEMA_ID, USER_ID) values
	(1, 1), (1, 2)
;*/

/**
 * Авторизация клиента компании Караби в определённой базе Oracle.
 * 
 */
create table USER_LOGON (
	TOKEN varchar(64) primary key, --сессионный ключ
	USER_ID bigint references CARABI_USER(USER_ID), --пользователь по общей базе (Derby)
	ORACLE_USER_ID bigint, --ID пользователя в Oracle, к которому подключились
	DISPLAY varchar(1024), --имя
	IP_ADDR_WHITE varchar(64), --IP клиента, определённый сервером (внешний) -- для журналирования.
	IP_ADDR_GREY varchar(64), --IP клиента, переданный клиентом (внутренний) -- для журналирования.
	SERVER_CONTEXT varchar(64), --сервер, в котором открыта сессия -- например, 83.243.75.4/carabiserver
	LASTACTIVE timestamp,
	REQUIRESESSION integer,
	SCHEMA_ID integer references CONNECTION_SCHEMA (SCHEMA_ID),
	APPSERVER_ID integer references APPSERVER (APPSERVER_ID),
	PERMANENT integer default null -- постоянные записи -- для программных клиентов
);
create index USER_LOGON_LASTACTIVE on USER_LOGON(LASTACTIVE);
create index USER_LOGON_PERMANENT on USER_LOGON(PERMANENT);

insert into USER_LOGON (TOKEN, LASTACTIVE, PERMANENT)
values ('durfvber74fvqi3447qiviq4vfi73vfdzjycyew673i7q3', '1970-01-01 00:00:00.0', 1);

--select * from SYSCS_DIAG.LOCK_TABLE

/**
 * Категории хранимых запросов
 */
create table QUERY_CATEGORY (
	CATEGORY_ID integer primary key generated by default as identity,
	NAME varchar(1024) not null unique, --название категории
	DESCRIPTION varchar(32000) --Описание
);

/**
 * SQL-запросы и PL-скрипты для Oracle.
 * Инициализируются пользователем при разработке, в служебную БД записываются с
 * заменой имён входных и выходных параметров на вопросы и сохренением имён,
 * порядка и типа параметров в таблице ORACLE_PARAMETER
 */
create table ORACLE_QUERY (
	QUERY_ID bigint primary key generated by default as identity,
	IS_EXECUTABLE integer not null, --0 -- SQL-запрос (select), 1 -- исполняемый скрипт
	NAME varchar(1024) not null unique, --Имя, используемое администратором системы
	SYSNAME varchar(1024) not null unique, --Имя, по которому запрос будет вызываться клиентом
	CATEGORY_ID integer references QUERY_CATEGORY (CATEGORY_ID),
	--CATEGORY varchar(256), --Название категории для пользователей-администраторов БД 
	SQL_QUERY varchar(32000) not null, --SQL текст запроса
	COUNT_QUERY varchar(32000), --Запрос, возвращающий объём выборки основного запроса
	SCHEMA_ID integer references CONNECTION_SCHEMA (SCHEMA_ID) --ID схемы БД, для которой предназначен запрос
);

create table ORACLE_PARAMETER (
	PARAMETER_ID bigint primary key generated by default as identity,
	NAME varchar(64) not null, --Название параметра
	TYPE_NAME varchar(64) not null, --Название его типа в БД (varchar,number,date,cursor...)
	IS_IN integer not null, --Является входным, если не 0
	IS_OUT integer not null, --Является выходным, если не 0 
	ORDERNUMBER integer not null, --Порядковый номер
	QUERY_ID bigint not null references ORACLE_QUERY(QUERY_ID) on delete cascade --ID запроса, для корого используется данный параметр
);

/**
 * Любая продукция компании Караби
 */
create table CARABI_PRODUCTION (
	PRODUCTION_ID integer primary key generated by default as identity,
	NAME varchar(1024) not null unique, --Название продукта
	SYSNAME varchar(1024) not null unique, --Системное наименование
	DESCRIPTION varchar(32000) --Описание
);

/**
 * Версии продуктов Караби
 */
create table CARABI_PRODUCT_VERSION (
	PRODUCT_VERSION_ID bigint primary key generated by default as identity,
	PRODUCT_ID integer not null references CARABI_PRODUCTION(PRODUCTION_ID) on delete cascade, --Версия какого продукта
	VERSION_NUMBER varchar(64) not null, --Номер версии вида '1.5.2.6'
	ISSUE_DATE date, --Дата выпуска
	SINGULARITY varchar(32000), --Особенности данной версии
	DOWNLOAD_URL varchar(1024), --Где скачать
	IS_SIGNIFICANT_UPDATE integer not null default 0--Является важным обновлением, если не 0
);
--commit;
