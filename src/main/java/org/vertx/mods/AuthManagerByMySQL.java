package org.vertx.mods;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
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
		this.address = getOptionalStringConfig("address","vertx.basicauthmanager");
		this.table = getOptionalStringConfig("users_table", "users");
		this.persistorAddress = getOptionalStringConfig("persistor_address", "campudus.asyncdbs");		
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

	protected void doLogin(final Message<JsonObject> message)
			throws Exception {
		
		final String username = getMandatoryString("username", message);
		if (username == null) {
			return;
		}
		String password = getMandatoryString("password", message);
		if (password == null) {
			return;
		}
		
		String QUERY = "SELECT * FROM " + table + " WHERE username='"+username+ "' AND password='"+password+"';";
		
		JsonObject findMsg = new JsonObject().putString("action", "raw")
				.putString("command", QUERY);
		eb.send(persistorAddress, findMsg, new Handler<Message<JsonObject>>() {
		      public void handle(Message<JsonObject> reply) {

		        if (reply.body().getString("status").equals("ok")) {
		          if (reply.body().getArray("results") != null && reply.body().getInteger("rows") == 1) {
		        	  
		            // Check if already logged in, if so logout of the old session
		            LoginInfo info = logins.get(username);
		            if (info != null) {
		              logout(info.sessionID);
		            }

		            // Found
		            final String sessionID = UUID.randomUUID().toString();
		            long timerID = vertx.setTimer(sessionTimeout, new Handler<Long>() {
		              public void handle(Long timerID) {
		                sessions.remove(sessionID);
		                logins.remove(username);
		              }
		            });
		            sessions.put(sessionID, username);
		            logins.put(username, new LoginInfo(timerID, sessionID));
		            JsonObject jsonReply = new JsonObject().putString("sessionID", sessionID);
		            sendOK(message, jsonReply);
		          } else {
		            // Not found
		            sendStatus("denied", message);
		          }
		        } else {
		          logger.error("Failed to execute login query: " + reply.body().getString("message"));
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
		System.out.println("Home");
	}
}
