package ru.carabi.server.kernel;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ejb.Schedule;
import javax.ejb.Stateless;
import javax.ejb.Timer;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.NonUniqueResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import org.apache.commons.lang3.StringUtils;
import ru.carabi.server.CarabiException;
import ru.carabi.server.EntityManagerTool;
import ru.carabi.server.Settings;
import ru.carabi.server.UserLogon;
import ru.carabi.server.entities.CarabiAppServer;
import ru.carabi.server.entities.CarabiUser;
import ru.carabi.server.entities.Permission;
import ru.carabi.server.logging.CarabiLogging;

/**
 * Управление учётными записями в БД
 * @author sasha<kopilov.ad@gmail.com>
 */
@Stateless
public class UsersPercistenceBean {
	private static final Logger logger = CarabiLogging.getLogger(UsersPercistenceBean.class);
	
	@PersistenceContext(unitName = "ru.carabi.server_carabiserver-kernel")
	private EntityManager em;
	
	/**
	 * Поиск сессии в базе по токену
	 * @param token
	 * @return Сессия с данным токеном, null, если сессии нет
	 */
	public UserLogon findUserLogon(String token) {
		if (StringUtils.isEmpty(token)) {
			return null;
		}
		UserLogon logon = em.find(UserLogon.class, token);
		if (logon != null) {
			//JPA может сохранить в кеше подключения к Oracle, что приводит к сбоям.
			try {
				logon.closeAllConnections();
			} catch (SQLException | NullPointerException e) {
				logger.log(Level.INFO, "error on closing lost connection: ", e);
			}
		}
		return logon;
	}
	
	/**
	 * Удаление авторизованного пользователя.
	 * @param token Токен удаляемого пользователя
	 */
	public void removeUserLogon(String token) {
		UserLogon logon = em.find(UserLogon.class, token);
		if (logon != null) {
			em.remove(logon);
			em.flush();
		}
	}
	
	/**
	 * Удаление из базы давно не заходивших пользователей.
	 * Раз в день из базы удаляются сессии, не активные более чем 
	 * {@link Settings.TOKEN_LIFETIME} дней.
	 * @param timer 
	 */
	@Schedule(hour="4")
	public synchronized void dispatcheUsersInBase(Timer timer) {
		CarabiAppServer currentServer = Settings.getCurrentServer();
		if (currentServer.isMaster()) {//чисткой базы должен заниматься единственный сервер
			Query query = em.createNamedQuery("deleteOldLogons");
			//Удаляем записи о давно не заходивших пользователях
			Calendar calendar = new GregorianCalendar();
			calendar.add(GregorianCalendar.DAY_OF_MONTH, -Settings.TOKEN_LIFETIME);
			query.setParameter("long_ago", calendar.getTime());
			logger.log(Level.INFO, "delete users older than: {0}", calendar.getTime().toString());
			query.executeUpdate();
			em.flush();
		}
	}
	
	public UserLogon addLogon(UserLogon logon) {
		logon.updateLastActive();
		UserLogon logonMerged = em.merge(logon);
		em.merge(logon.getUser());
		em.flush();
		logonMerged.copyTrancientFields(logon);
		return logonMerged;
	}
	
	public void updateLogon(UserLogon logon) {
		logon.updateLastActive();
		Query updateQuery = em.createNativeQuery("update USER_LOGON set LASTACTIVE = ? where TOKEN = ?");
		updateQuery.setParameter(1, logon.getLastActive());
		updateQuery.setParameter(2, logon.getToken());
		updateQuery.executeUpdate();
		em.flush();
	}
	
	/**
	 * Проверка, что существует пользователь с таким логином.
	 * @param login логин пользователя, которого ищем
	 * @return true, если найден пользователь с таким логином в ядровой БД
	 */
	public boolean userExists(String login) {
		Query findUser = em.createNamedQuery("findUser");
		findUser.setParameter("login", login);
		List resultList = findUser.getResultList();
		return !resultList.isEmpty();
	}
	
	/**
	 * Получение ID пользователя Carabi.
	 * @param login логин пользователя
	 * @return ID пользователя. -1, если нет пользователя с таким логином.
	 */
	public Long getUserID(String login) {
		final Query query = em.createNamedQuery("findUser");
		query.setParameter("login", login);
		final List resultList = query.getResultList();
		if (resultList.isEmpty()) {
			return -1L;
		} else {
			return (Long) resultList.get(0);
		}
	}

	/**
	 * Поиск пользователя по логину
	 * @param login логин
	 * @return найденный пользователь
	 * @throws CarabiException если пользователь не найден
	 */
	public CarabiUser findUser(String login) throws CarabiException {
		try {
			TypedQuery<CarabiUser> activeUser = em.createNamedQuery("getUserInfo", CarabiUser.class);
			activeUser.setParameter("login", login);
			CarabiUser user = activeUser.getSingleResult();
			return user;
		} catch (NoResultException ex) {
			final CarabiException e = new CarabiException("No user with login " 
					+ login);
			logger.log(Level.WARNING, "" , e);
			throw e;
		}
	}
	
	/**
	 * Поиск пользователя по email
	 * @param email email
	 * @return найденный пользователь
	 * @throws CarabiException если пользователь не найден или найдено несколько
	 */
	public CarabiUser getUserByEmail(String email) throws CarabiException {
		CarabiUser user;
		try { //Пробуем найти пользователя с логином, равным указанному email
			TypedQuery<CarabiUser> activeUser = em.createNamedQuery("getUserInfo", CarabiUser.class);
			activeUser.setParameter("login", email);
			user = activeUser.getSingleResult();
		} catch (NoResultException ex) {// если не нашли -- ищем по полю email
			try {
				TypedQuery<CarabiUser> activeUser = em.createNamedQuery("getUserInfoByEmail", CarabiUser.class);
				activeUser.setParameter("email", email);
				user = activeUser.getSingleResult();
			} catch (NoResultException e) {
				throw new CarabiException("User with email " + email + " not found");
			} catch (NonUniqueResultException e) {
				throw new CarabiException("More than one users with email " + email);
			}
		}
		return user;
	}
	
	/**
	 * Проверка, имеет ли текущий пользователь указанное право.
	 */
	public boolean userHavePermission(UserLogon logon, String permission) throws CarabiException {
		final CarabiUser user = logon.getUser();
		return userHavePermission(user, permission);
	}
	
	public boolean userHavePermission(String login, String permission) throws CarabiException {
		final CarabiUser user = findUser(login);
		return userHavePermission(user, permission);
	}
	
	/**
	 * Проверка, имеет ли данный пользователь указанное право.
	 * Принцип действия:
	 * <ul>
	 * <li>1 Если разрешение или запрет указано непосредственно для пользователя (таблица USER_HAS_PERMISSION) -- вернуть его.
	 * <li>2 Если нет, то искать по группам:
	 * <li>2.1 Если хотя бы в одной группе указано разрешение и ни в одной не указан запрет -- вернуть разрешение
	 * <li>2.2 Если хотя бы в одной группе указан запрет и ни в одной не указано разрешение -- вернуть запрет
	 * <li>3 Иначе (если нет противоречия) -- вернуть настройку для права по умолчанию.
	 * </ul>
	 * Если выставлен флаг PERMISSIONS_TRUST в настройках -- всегда возвращается true (например, на случай незаполненной базы прав)
	 * @param user пользователь
	 * @param permission кодовое название права
	 * @return имеет ли данный пользователь указанное право
	 * @throws ru.carabi.server.CarabiException если такого права нет в системе
	 */
	public boolean userHavePermission(CarabiUser user, String permission) throws CarabiException {
		if (Settings.PERMISSIONS_TRUST) {
			return true;
		}
		try {
			String sql = "select * from appl_permissions.user_has_permission(?, ?)";
			Query query = em.createNativeQuery(sql);
			query.setParameter(1, user.getId());
			query.setParameter(2, permission);
			Object result = query.getSingleResult();
			return (Boolean) result;
		} catch (Exception e) {
			throw new CarabiException(e);
		}
	}
	
	/**
	 * Возвращает права ({@link Permission}), которые текущий пользователь имеет в системе.
	 * Все (при parentPermissionSysname == null) или в некотором контексте
	 * (parentPermission и дочерние, либо пустой список, если пользователь не имеет parentPermission).
	 * @param user пользователь, права которого нужны
	 * @param parentPermissionSysname кодовое название родительского права, дочерние от которого интересуют
	 * @return список прав
	 * @throws ru.carabi.server.CarabiException если право parentPermission не найдено
	 */
	public Collection<Permission> getUserPermissions(CarabiUser user, String parentPermissionSysname) throws CarabiException {
		Permission parentPermission = null;
		if (StringUtils.isEmpty(parentPermissionSysname)) {
			return getUserPermissions(user, parentPermission);
		}
		if (!userHavePermission(user, parentPermissionSysname)) {
			return new ArrayList<>();
		}
		parentPermission = EntityManagerTool.findBySysname(em, Permission.class, parentPermissionSysname);
		if (parentPermission == null) {
			throw new CarabiException("Permission " + parentPermissionSysname + " not found");
		}
		Collection<Permission> userPermissions = getUserPermissions(user, parentPermission);
		userPermissions.add(parentPermission);
		return userPermissions;
	}
	
	/**
	 * Возвращает права указанного пользователя.
	 * Все (при parentPermission == null) или в некотором контексте
	 * (parentPermission и дочерние, либо пустой список, если пользователь не имеет parentPermission).
	 * @param user пользователь, права которого нужны
	 * @param parentPermission родительское право, дочерние от которого интересуют
	 * @return список прав пользователя user
	 */
	public Collection<Permission> getUserPermissions(CarabiUser user, Permission parentPermission) {
		Query query = null;
		if (parentPermission == null) {
			String sql = "select permission_id, name, sysname, parent_permission from appl_permissions.get_user_permissions(?)";
			query = em.createNativeQuery(sql);
			query.setParameter(1, user.getId());
		} else {
			String sql = "select permission_id, name, sysname, parent_permission from appl_permissions.get_user_permissions(?, ?)";
			query = em.createNativeQuery(sql);
			query.setParameter(1, user.getId());
			query.setParameter(2, parentPermission.getId());
		}
		List resultList = query.getResultList();
		Set<Permission> result = new HashSet<>();
		for (Object row: resultList) {
			Object[] data = (Object[])row;
			Permission permission = new Permission();
			permission.setId((Integer) data[0]);
			permission.setName((String) data[1]);
			permission.setSysname((String) data[2]);
			permission.setParentPermissionId((Integer) data[3]);
			result.add(permission);
		}
		return result;
	}
	
	/**
	 * Возвращает все права текущего пользователя
	 * @param logon сессия текущего пользователя
	 * @return список прав текущего пользователя
	 */
	public Collection<Permission> getUserPermissions(UserLogon logon) {
		return getUserPermissions(logon.getUser(), (Permission)null);
	}
}
