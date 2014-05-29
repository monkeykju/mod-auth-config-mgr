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

		private LoginInfo(long timerID, String sessionID) {
			this.timerID = timerID;
			this.sessionID = sessionID;
		}
	}

	public void start() {
		super.start();
		this.address = getOptionalStringConfig("address",
				"vertx.auth-conf-mgr");
		this.table = getOptionalStringConfig("users_table", "users");
		this.configTable = getOptionalStringConfig("config_table", "config");
		this.persistorAddress = getOptionalStringConfig("persistor_address",
				"campudus.asyncdbs");
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

		String QUERY = "SELECT configuration FROM " + table + " INNER JOIN "
				+ configTable + " ON " + table + ".user_id = " + configTable
				+ ".user_id WHERE " + table + ".password='" + password
				+ "' AND " + table + ".username='" + username
				+ "' AND module_name='" + moduleName + "';";

		JsonObject findMsg = new JsonObject().putString("action", "raw")
				.putString("command", QUERY);
		eb.send(persistorAddress, findMsg, new Handler<Message<JsonObject>>() {
			public void handle(Message<JsonObject> reply) {

				if (reply.body().getString("status").equals("ok")) {
					if (reply.body().getArray("results") != null
							&& reply.body().getInteger("rows") == 1) {

						// Get configuration of the module
						JsonArray objConf = reply.body().getArray("results")
								.get(0);
						String config = objConf.get(0).toString();

						// Check if already logged in, if so logout of the old
						// session
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
						jsonReply.putObject("config", new JsonObject(config));
						sendOK(message, jsonReply);
					} else {
						// Not found
						sendStatus("denied", message);
					}
				} else {
					logger.error("Failed to execute login query: "
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
		String username = sessions.remove(sessionID);
		if (username != null) {
			LoginInfo info = logins.remove(username);
			vertx.cancelTimer(info.timerID);
			return true;
		} else {
			return false;
		}
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

		final String username = sessions.get(sessionID);

		// In this basic auth manager we don't do any resource specific
		// authorisation
		// The user is always authorised if they are logged in

		if (username != null) {
			String QUERY = "SELECT configuration FROM " + table
					+ " INNER JOIN " + configTable + " ON " + table
					+ ".user_id = " + configTable + ".user_id WHERE " + table
					+ ".username='" + username + "' AND module_name='"
					+ moduleName + "';";

			JsonObject findMsg = new JsonObject().putString("action", "raw")
					.putString("command", QUERY);

			eb.send(persistorAddress, findMsg,
					new Handler<Message<JsonObject>>() {

						@Override
						public void handle(Message<JsonObject> reply) {
							if (reply.body().getString("status").equals("ok")) {
								if (reply.body().getArray("results") != null && reply.body().getInteger("rows") == 1){
								// Get configuration of the module
								JsonArray objConf = reply.body()
										.getArray("results").get(0);
								String config = objConf.get(0).toString();
								JsonObject replyOk = new JsonObject()
										.putObject("config", new JsonObject(
												config));
								replyOk.putString("username", username);
								sendOK(message, replyOk);
								}else{
									sendStatus("denied", message);
								}
							} else {
								logger.error("Failed to execute login query: "
										+ reply.body().getString("message"));
								sendError(message, "Failed to excecute login");
							}
						}
					});

		} else {
			sendStatus("denied", message);
		}
	}

	public static void main(String[] args) {
		System.out.println("Home");
	}
}
