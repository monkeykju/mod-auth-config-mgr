package org.vertx.mods;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.sql.*;

public class AuthManagerByMySQL extends BusModBase {
	private Handler<Message<JsonObject>> loginHandler;
	private Handler<Message<JsonObject>> logoutHandler;
	private Handler<Message<JsonObject>> authoriseHandler;

	protected final Map<String, String> sessions = new HashMap<>();
	protected final Map<String, LoginInfo> logins = new HashMap<>();

	private static final long DEFAULT_SESSION_TIMEOUT = 30 * 60 * 1000;

	static final String JDBC_DRIVER = "com.mysql.jdbc.Driver";
	static final String DB_URL = "jdbc:mysql://localhost/";

	private String address;
	private String database;
	private String table;
	private long sessionTimeout;
	private String usernameDB;
	private String passwordDB;
	private Connection conn;

	private static final class LoginInfo {
		final long timerID;
		final String sessionID;

		private LoginInfo(long timerID, String sessionID) {
			this.timerID = timerID;
			this.sessionID = sessionID;
		}
	}

	public void start() {
		super.start();
		this.address = getOptionalStringConfig("address",
				"vertx.basicauthmanager");
		this.table = getOptionalStringConfig("users_table", "users");
		this.database = getOptionalStringConfig("database", "auth_db");
		this.usernameDB = getOptionalStringConfig("username_db", "root");
		this.passwordDB = getOptionalStringConfig("password_db", "");
		

			Number timeout = config.getNumber("session_timeout");
			if (timeout != null) {
				if (timeout instanceof Long) {
					this.sessionTimeout = (Long) timeout;
				} else if (timeout instanceof Integer) {
					this.sessionTimeout = (Integer) timeout;
				}
			} else {
				this.sessionTimeout = DEFAULT_SESSION_TIMEOUT;
			}

			loginHandler = new Handler<Message<JsonObject>>() {
				public void handle(Message<JsonObject> message) {
					
				try {
					doLogin(message);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
					
				}
			};
			eb.registerHandler(address + ".login", loginHandler);
			logoutHandler = new Handler<Message<JsonObject>>() {
				public void handle(Message<JsonObject> message) {
					doLogout(message);
				}
			};
			eb.registerHandler(address + ".logout", logoutHandler);
			authoriseHandler = new Handler<Message<JsonObject>>() {
				public void handle(Message<JsonObject> message) {
					doAuthorise(message);
				}
			};
			eb.registerHandler(address + ".authorise", authoriseHandler);
	}

	protected void doLogin(final Message<JsonObject> message)
			throws Exception {
		Connection conn = null;
		Class.forName(JDBC_DRIVER);
		conn = DriverManager.getConnection(DB_URL+this.database, usernameDB, passwordDB);
		
		final String username = getMandatoryString("username", message);
		if (username == null) {
			return;
		}
		String password = getMandatoryString("password", message);
		if (password == null) {
			return;
		}
		Statement stmt = null;
		stmt = conn.createStatement();
		String QUERY = "SELECT * FROM " + this.table + " WHERE username='"
				+ username + "' AND password='" + password+"'";

		ResultSet results = stmt.executeQuery(QUERY);
		if (results != null) {
			if(results.next()){
				LoginInfo info = logins.get(username);
				if (info != null) {
					logout(info.sessionID);
				}

				// Found
				final String sessionID = UUID.randomUUID().toString();
				long timerID = vertx.setTimer(sessionTimeout,
						new Handler<Long>() {
							public void handle(Long timerID) {
								sessions.remove(sessionID);
								logins.remove(username);
							}
						});
				sessions.put(sessionID, username);
				logins.put(username, new LoginInfo(timerID, sessionID));
				JsonObject jsonReply = new JsonObject().putString(
						"sessionID", sessionID);
				sendOK(message, jsonReply);
			}else{
				sendStatus("denied", message);
			}
			
		} else {
			logger.error("Failed to execute login query");
			sendError(message, "Failed to excecute login");
		}
		stmt.close();
		conn.close();
	}

	protected void doLogout(final Message<JsonObject> message) {
		final String sessionID = getMandatoryString("sessionID", message);
		if (sessionID != null) {
			if (logout(sessionID)) {
				sendOK(message);
			} else {
				super.sendError(message, "Not logged in");
			}
		}
	}

	protected boolean logout(String sessionID) {
		String username = sessions.remove(sessionID);
		if (username != null) {
			LoginInfo info = logins.remove(username);
			vertx.cancelTimer(info.timerID);
			return true;
		} else {
			return false;
		}
	}

	protected void doAuthorise(Message<JsonObject> message) {
		String sessionID = getMandatoryString("sessionID", message);
		if (sessionID == null) {
			return;
		}
		String username = sessions.get(sessionID);

		// In this basic auth manager we don't do any resource specific
		// authorisation
		// The user is always authorised if they are logged in

		if (username != null) {
			JsonObject reply = new JsonObject().putString("username", username);
			sendOK(message, reply);
		} else {
			sendStatus("denied", message);
		}
	}
	public static void main(String[] args){
		System.out.println("Test");
	}
}
