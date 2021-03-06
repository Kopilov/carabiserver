package ru.carabi.server.soap;

import java.util.Properties;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.xml.ws.Holder;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.MessageContext;
import ru.carabi.server.CarabiException;
import ru.carabi.server.RegisterException;
import ru.carabi.server.Settings;
import ru.carabi.server.UserLogon;
import ru.carabi.server.entities.CarabiAppServer;
import ru.carabi.server.entities.CarabiUser;
import ru.carabi.server.entities.ConnectionSchema;
import ru.carabi.server.kernel.ConnectionsGateBean;
import ru.carabi.server.kernel.GuestBean;
import ru.carabi.server.kernel.UsersControllerBean;
import ru.carabi.server.logging.CarabiLogging;

/**
 * Сервис для неавторизованных пользователей.
 * Содержит методы авторизации. Имеется два способа авторизации:
 * <ul>
 * <li>
 *		двухэтапный ({@linkplain #wellcomeNN}, {@linkplain #registerUser}) &ndash;
 *		для настольных приложений, создающих долгоживущую сессию и получающих информацию о клиенте;
 * </li>
 * <li>
 *		одноэтапный ({@linkplain #registerUserLight}) &ndash; для web-приложений,
 *		мобильных приложений, любых кратковременных подключений.
 * </li>
 * </ul>
 * 
 * @author sasha<kopilov.ad@gmail.com>
 */
@WebService(serviceName = "GuestService")
public class GuestService {
	private static final Logger logger = CarabiLogging.getLogger(GuestService.class);
	@EJB 
	private UsersControllerBean usersController;
	@EJB 
	private GuestBean guest;
	@EJB 
	private ConnectionsGateBean cg;

	@Inject
	GuestSesion guestSesion;
	
	@Resource
	private WebServiceContext context;
	private static final ResourceBundle messages = ResourceBundle.getBundle("ru.carabi.server.soap.Messages");
	
	/**
	 * Предварительная авторизация.
	 * Первый этап двухэтапной авторизации.
	 * Производит проверку версии, сохранение данных о клиенте в памяти, генерацию timestamp
	 * (шифровального дополнения к паролю), проверка доступности БД
	 * @param login Имя пользователя
	 * @param version Версия клиента
	 * @param vc Проверять версию клиента
	 * @param schemaID ID целевой схемы Oracle (-1 для выбора по названию)
	 * @param schemaName Название целевой схемы Oracle (пустое значение для выбора основной схемы)
	 * @param autoCloseableConnection Закрывать Oracle-сессию после каждого вызова -- рекомендуется при редком обращении, если не используются прокрутки
	 * @param notConnectToOracle Не подключаться к Oracle при авторизации (использование данной опции не позволит вернуть подробные данные о пользователе)
	 * @param timestamp Выходной параметр &mdash; выдача шифровального ключа для передачи пароля на втором этапе
	 * @return Код возврата:
	 *	<ul>
	 *		<li><strong>0</strong> при успешном выполнении</li>
	 *		<li>{@link Settings#VERSION_MISMATCH}, если vc != 0 и version != {@link Settings#projectVersion}</li>
	 *	</ul>
	 */
	@WebMethod(operationName = "wellcomeNN")
	public int wellcomeNN(
			@WebParam(name = "login") final String login,
			@WebParam(name = "version") String version,
			@WebParam(name = "vc") int vc,
			@WebParam(name = "schemaID") int schemaID,
			@WebParam(name = "schemaName") final String schemaName,
			@WebParam(name = "autoCloseableConnection") boolean autoCloseableConnection,
			@WebParam(name = "notConnectToOracle") boolean notConnectToOracle,
			@WebParam(name = "timestamp", mode = WebParam.Mode.OUT) Holder<String> timestamp
		) throws RegisterException {
		return guest.wellcomeNN(login, version, vc, schemaID, schemaName, autoCloseableConnection, notConnectToOracle, timestamp, guestSesion);
	}

	/**
	 * Полная авторизация пользователя.
	 * Выполняется после предварительной. Создаёт долгоживущую сессию Oracle.
	 * @param login логин
	 * @param passwordTokenClient ключ &mdash; md5 (md5(login+password)+ (Settings.projectName + "9999" + Settings.serverName timestamp из welcome)) *[login и md5 -- в верхнем регистре]
	 * @param userAgent название программы-клиента
	 * @param clientIp локальный IP-адрес клиентского компьютера (для журналирования)
	 * @param version Номер версии
	 * @param vc Проверять номер версии
	 * @param schemaID
	 * @param info
	 * @return код результата. 0(ноль) &mdash; успех, отрицательный &mdash; ошибка. 
	 */
	@WebMethod(operationName = "registerUser")
	public int registerUser(
			@WebParam(name = "login") String login,
			@WebParam(name = "password") String passwordTokenClient,
			@WebParam(name = "userAgent") String userAgent,
			@WebParam(name = "clientIp") String clientIp,
			@WebParam(name = "version") String version,
			@WebParam(name = "vc") int vc,
			@WebParam(name = "schemaID", mode = WebParam.Mode.OUT) Holder<Integer> schemaID, //Порядковый номер схемы БД
			@WebParam(name = "info", mode = WebParam.Mode.OUT) Holder<SoapUserInfo> info//Результат
		) throws CarabiException, RegisterException {
		try {
			CarabiUser user = guest.searchUser(login);
			user = guest.checkCurrentServer(user);
			return guest.registerUser(user, passwordTokenClient, userAgent, getConnectionProperties(clientIp), version, vc, schemaID, info, guestSesion);
		} catch (RegisterException e) {
			if (e.badLoginPassword()) {
				logger.log(Level.INFO, "", e);
				throw new RegisterException(RegisterException.MessageCode.ILLEGAL_LOGIN_OR_PASSWORD);
			} else {
				logger.log(Level.SEVERE, "", e);
				throw e;
			}
		}
	}
	
	/**
	 * Облегчённая авторизация.
	 * Одноэтапная авторизация &mdash; для использования PHP-скриптами.
	 * @param login Имя пользователя Carabi
	 * @param passwordCipherClient Зашифрованный пароль
	 * @param userAgent название программы-клиента
	 * @param requireSession Требуется долгоживущая сессия Oracle
	 * @param notConnectToOracle Не подключаться к Oracle при авторизации (на выходе будет -1 вместо ID)
	 * @param clientIp локальный IP-адрес клиента (при использовании для Web &mdash; адрес браузера) &mdash; для журналирования.
	 * @param schemaName Псевдоним базы Carabi, к которой нужно подключиться (если не задан &mdash; возвращается основная)
	 * @param token Выход: Ключ для авторизации при выполнении последующих действий
	 * @return ID Carabi-пользователя, если не указан параметр notConnectToOracle, иначе -1
	 */
	@WebMethod(operationName = "registerUserLight")
	public long registerUserLight(
			@WebParam(name = "login") String login,
			@WebParam(name = "password") String passwordCipherClient,
			@WebParam(name = "userAgent") String userAgent,
			@WebParam(name = "requireSession") boolean requireSession,
			@WebParam(name = "notConnectToOracle") boolean notConnectToOracle,
			@WebParam(name = "clientIp") String clientIp,
			@WebParam(name = "schemaName", mode = WebParam.Mode.INOUT) Holder<String> schemaName,
			@WebParam(name = "token", mode = WebParam.Mode.OUT) Holder<String> token
		) throws RegisterException, CarabiException {
		logger.log(Level.INFO, "{0} is logining", login);
		try {
			CarabiUser user = guest.searchUser(login);
			user = guest.checkCurrentServer(user);
			return guest.registerUserLight(user, passwordCipherClient, userAgent, requireSession, notConnectToOracle, getConnectionProperties(clientIp), schemaName, token);
		} catch (RegisterException e) {
			if (e.badLoginPassword()) {
				logger.log(Level.INFO, "", e);
				throw new RegisterException(RegisterException.MessageCode.ILLEGAL_LOGIN_OR_PASSWORD);
			} else {
				logger.log(Level.SEVERE, "", e);
				throw e;
			}
		}
	}
	
	/**
	 * Получение информации о текущем пользователе.
	 * проверка, кто авторизован с данным токеном, и получение основных данных:
	 * логин, ID Carabi-пользователя, схема
	 * @param token токен сессии
	 * @return {"login":"%s", "base":"%s" "carabiUserID":"%d"}
	 * @throws CarabiException если пользователь не найден
	 */
	@WebMethod(operationName = "getUserInfo")
	public String getUserInfo(@WebParam(name = "token") String token) throws CarabiException {
		try (UserLogon logon = usersController.tokenAuthorize(token)) {
			JsonObjectBuilder result = Json.createObjectBuilder();
			result.add("login", logon.getUser().getLogin());
			ConnectionSchema schema = logon.getSchema();
			if (schema != null) {
				result.add("schema", schema.getSysname());
			} else {
				result.addNull("schema");
			}
			result.add("carabiUserID", logon.getExternalId());
			return result.build().toString();
		}
	}
	/**
	 * Получение информации о текущем пользователе с веб-аккаунтом.
	 * проверка, кто авторизован с данным токеном, и получение основных данных:
	 * ID Carabi-пользователя, Web-пользователя, логин
	 * ID веб-пользователя по его ID Караби пользователя 
	 * @param token "Токен" (идентификатор) выполненной через сервер приложений регистрации в системе. 
	 * См. 
	 * {@link ru.carabi.server.soap.GuestService#registerUserLight(java.lang.String, java.lang.String, java.lang.String, boolean, javax.xml.ws.Holder)} и
	 * {@link ru.carabi.server.soap.GuestService#registerUser(java.lang.String, java.lang.String, java.lang.String, java.lang.String, int, javax.xml.ws.Holder, javax.xml.ws.Holder)}.
	 * @throws CarabiException если токен или веб-аккаунт не найден
	 * @return JSON-объект с полями login, idCarabiUser, idWebUser
	 */
	@WebMethod(operationName = "getWebUserInfo")
	public String getWebUserInfo(@WebParam(name = "token") String token) throws CarabiException {
		try (UserLogon logon = usersController.tokenAuthorize(token)) {
			return guest.getWebUserInfo(logon);
		}
	}
	
	@WebMethod(operationName = "getOracleUserID")
	public long getOracleUserID(@WebParam(name = "token") String token) throws CarabiException {
		try (UserLogon logon = usersController.tokenAuthorize(token)) {
			return logon.getExternalId();
		}
	}
	
	/**
	 * Деавторизация.
	 * Удаление объекта с долгоживущей сессией (при наличии),
	 * удаление записи из служебной БД -- опционально
	 * @param token Токен выходящего пользователя
	 * @param permanently Удалить запись из служебной БД
	 */
	@WebMethod(operationName = "unauthorize")
	public void unauthorize(
			@WebParam(name = "token") String token,
			@WebParam(name = "permanently") boolean permanently
		) 
	{
		usersController.removeUserLogon(token, permanently);
	}
	
	/**
	 * Получение данных о сервере.
	 * Не требует авторизации. Так же может
	 * использоваться для проверки работоспособности.
	 * @return 
	 */
	public String about() {
		return guest.about();
	}
	
	/**
	 * Создание объекта Properties для передачи в методы GuestBean.
	 * @param greyIP серый IP, переданный клиентом
	 * @return свойства подключения с полями:<ul>
	 * <li>ipAddrWhite &mdash; белый IP клиента, определённый сервером
	 * <li>ipAddrGrey &mdash; параметр greyIP
	 * <li>serverContext &mdash; адрес сервера, включающий IP с портом (белый &mdash; заданный
	 * в свойствах контейнера jndi/ServerWhiteAddress, при его отсутствии &mdash; серый)
	 * и имя JavaEE программы</ul>
	 */
	private Properties getConnectionProperties(String greyIP) {
		Properties connectionProperties = new Properties();
		if (greyIP == null) {
			greyIP = "null";
		}
		connectionProperties.setProperty("ipAddrGrey", greyIP);
		HttpServletRequest req = (HttpServletRequest)context.getMessageContext().get(MessageContext.SERVLET_REQUEST);
		connectionProperties.setProperty("ipAddrWhite", req.getRemoteAddr());
		try {
			Context initialContext = new InitialContext();
			String serverName = (String) initialContext.lookup("jndi/ServerName");
			logger.log(Level.INFO, "serverName: {0}", serverName);
		} catch (NamingException ex) {
			logger.log(Level.SEVERE, "serverName not found", ex);
		}
		CarabiAppServer currentServer = Settings.getCurrentServer();
		String serverIpPort = currentServer.getComputer()+ ":" + currentServer.getGlassfishPort();
		connectionProperties.setProperty("serverContext", serverIpPort + req.getContextPath());
		return connectionProperties;
	}
	
	/**
	 * Возвращает код для восстановления пароля пользователю с данным email.
	 * Ищет пользователя с логином, совпадающим со введённым email.
	 * Если таких нет -- с указанным полем email, равным указанному.
	 * Если таких нет или больше одного -- ошибка.
	 * @param email адрес пользователя, пароль которого надо восстановить.
	 * @throws ru.carabi.server.CarabiException Если не удалось отправить письмо (ненайденный пользователь игнорируется)
	 */
	@WebMethod(operationName = "sendPasswordRecoverCode")
	public void sendPasswordRecoverCode(@WebParam(name = "email") String email) throws CarabiException {
		guest.sendPasswordRecoverCode(email);
	}
	
	
	/**
	 * Изменение пароля по коду восстановления.
	 * Ищет пользователя с указанными email и кодом восстановления, если нашли -- ставит указанный пароль
	 * @param email
	 * @param code
	 * @param password
	 * @return удалось ли восстановить пароль
	 */
	@WebMethod(operationName = "recoverPassword")
	public boolean recoverPassword(
			@WebParam(name = "email") String email,
			@WebParam(name = "code") String code,
			@WebParam(name = "password") String password
		) {
		return guest.recoverPassword(email, code, password);
	}
}
