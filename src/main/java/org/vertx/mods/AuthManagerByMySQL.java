package org.vertx.mods;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

public class AuthManagerByMySQL extends BusModBase {
	private Handler<Message<JsonObject>> loginHandler;
	private Handler<Message<JsonObject>> logoutHandler;
	private Handler<Message<JsonObject>> authoriseHandler;

	protected final Map<String, String> sessions = new HashMap<>();
	protected final Map<String, LoginInfo> logins = new HashMap<>();

	private static final long DEFAULT_SESSION_TIMEOUT = 30 * 60 * 1000;

	private String address;
	private String table;
	private String configTable;
	private String persistorAddress;
	private long sessionTimeout;

	private static final class LoginInfo {
		final long timerID;
		final String sessionID;

		LoginInfo(long timerID, String sessionID) {
			this.timerID = timerID;
			this.sessionID = sessionID;
		}
	}

	@Override
	public void start() {
		super.start();
		this.address = getOptionalStringConfig("address","vertx.auth-conf-mgr");
		this.table = getOptionalStringConfig("users_table", "users");
		this.configTable = getOptionalStringConfig("config_table", "config");
		this.persistorAddress = getOptionalStringConfig("persistor_address","campudus.asyncdbs");
		Number timeout = this.config.getNumber("session_timeout");
		if (timeout != null) {
			if (timeout instanceof Long) {
				this.setSessionTimeout((Long) timeout);
			} else if (timeout instanceof Integer) {
				this.setSessionTimeout((Integer) timeout);
			}
		} else {
			this.setSessionTimeout(DEFAULT_SESSION_TIMEOUT);
		}

		this.loginHandler = new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> message) {

				try {
					doLogin(message);
				} catch (Exception e) {
					e.printStackTrace();
				}

			}
		};
		this.eb.registerHandler(this.address + ".login", this.loginHandler);
		
		this.logoutHandler = new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> message) {
				doLogout(message);
			}
		};		
		this.eb.registerHandler(this.address + ".logout", this.logoutHandler);
		
		this.authoriseHandler = new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> message) {
				doAuthorise(message);
			}
		};
		this.eb.registerHandler(this.address + ".authorise", this.authoriseHandler);
	}

	protected void doLogin(final Message<JsonObject> message) throws Exception {

		final String username = getMandatoryString("username", message);
		if (username == null) {
			return;
		}
		final String password = getMandatoryString("password", message);
		if (password == null) {
			return;
		}

		final String moduleName = getMandatoryString("module_name", message);
		if (moduleName == null) {
			return;
		}

		// String QUERY = "SELECT * FROM " + table +
		// " WHERE username='"+username+ "' AND password='"+password+"';";

		String QUERY = "SELECT configuration FROM " + this.table + " INNER JOIN "
				+ this.configTable + " ON " + this.table + ".user_id = " + this.configTable
				+ ".user_id WHERE " + this.table + ".password='" + password
				+ "' AND " + this.table + ".username='" + username
				+ "' AND module_name='" + moduleName + "';";

		JsonObject findMsg = new JsonObject().putString("action", "raw")
				.putString("command", QUERY);
		this.eb.send(this.persistorAddress, findMsg, new Handler<Message<JsonObject>>() {
			@SuppressWarnings("synthetic-access")
			@Override
			public void handle(Message<JsonObject> reply) {

				if (reply.body().getString("status").equals("ok")) {
					if (reply.body().getArray("results") != null
							&& reply.body().getInteger("rows") == 1) {

						// Get configuration of the module
						JsonArray objConf = reply.body().getArray("results").get(0);
						String configuration = objConf.get(0).toString();

						// Check if already logged in, if so logout of the old
						// session
						LoginInfo info = AuthManagerByMySQL.this.logins.get(username);
						if (info != null) {
							logout(info.sessionID);
						}

						// Found
						final String sessionID = UUID.randomUUID().toString();
						long timerID = AuthManagerByMySQL.this.getVertx().setTimer(AuthManagerByMySQL.this.getSessionTimeout(),
								new Handler<Long>() {
									@Override
									public void handle(Long timerID1) {
										AuthManagerByMySQL.this.sessions.remove(sessionID);
										AuthManagerByMySQL.this.logins.remove(username);
									}
								});
						AuthManagerByMySQL.this.sessions.put(sessionID, username);
						AuthManagerByMySQL.this.logins.put(username, new LoginInfo(timerID, sessionID));
						JsonObject jsonReply = new JsonObject().putString(
								"sessionID", sessionID);
						jsonReply.putObject("config", new JsonObject(configuration));
						sendOK(message, jsonReply);
					} else {
						// Not found
						sendStatus("denied", message);
					}
				} else {
					AuthManagerByMySQL.this.logger.error("Failed to execute login query: "
							+ reply.body().getString("message"));
					sendError(message, "Failed to excecute login");
				}
			}
		});

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
		String username = this.sessions.remove(sessionID);
		if (username != null) {
			LoginInfo info = this.logins.remove(username);
			this.vertx.cancelTimer(info.timerID);
			return true;
		}
		return false;
	}

	protected void doAuthorise(final Message<JsonObject> message) {
		String sessionID = getMandatoryString("sessionID", message);
		if (sessionID == null) {
			return;
		}

		final String moduleName = getMandatoryString("module_name", message);
		if (moduleName == null) {
			return;
		}

		final String username = this.sessions.get(sessionID);

		// In this basic auth manager we don't do any resource specific
		// authorisation
		// The user is always authorised if they are logged in

		if (username != null) {
			String QUERY = "SELECT configuration FROM " + this.table
					+ " INNER JOIN " + this.configTable + " ON " + this.table
					+ ".user_id = " + this.configTable + ".user_id WHERE " + this.table
					+ ".username='" + username + "' AND module_name='" + moduleName + "';";

			JsonObject findMsg = new JsonObject().putString("action", "raw")
					.putString("command", QUERY);

			this.eb.send(this.persistorAddress, findMsg,
					new Handler<Message<JsonObject>>() {

						@SuppressWarnings("synthetic-access")
						@Override
						public void handle(Message<JsonObject> reply) {
							if (reply.body().getString("status").equals("ok")) {
								if (reply.body().getArray("results") != null && reply.body().getInteger("rows") == 1){
								// Get configuration of the module
								JsonArray objConf = reply.body()
										.getArray("results").get(0);
								String configuration = objConf.get(0).toString();
								JsonObject replyOk = new JsonObject()
										.putObject("config", new JsonObject(
												configuration));
								replyOk.putString("username", username);
								sendOK(message, replyOk);
								}else{
									sendStatus("denied", message);
								}
							} else {
								AuthManagerByMySQL.this.logger.error("Failed to execute login query: "
										+ reply.body().getString("message"));
								sendError(message, "Failed to excecute login");
							}
						}
					});

		} else {
			sendStatus("denied", message);
		}
	}

	public long getSessionTimeout() {
		return this.sessionTimeout;
	}

	public void setSessionTimeout(long sessionTimeout) {
		this.sessionTimeout = sessionTimeout;
	}
}
