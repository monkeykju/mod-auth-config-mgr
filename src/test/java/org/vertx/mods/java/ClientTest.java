package org.vertx.mods.java;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;


//import static  org.vertx.testtools.VertxAssert.assertEquals;
import static org.junit.Assert.assertEquals;
import static org.vertx.testtools.VertxAssert.assertTrue;
import static org.vertx.testtools.VertxAssert.testComplete;

import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.testtools.TestVerticle;

public class ClientTest extends TestVerticle {

	private EventBus eb;
	static final String JDBC_DRIVER = "com.mysql.jdbc.Driver";
	static final String DB_URL = "jdbc:mysql://localhost/auth_db";

	// Database credentials
	static final String USER = "root";
	static final String PASS = "JHJD89373";
	String session = "";

	@Override
	public void start() {
		eb = vertx.eventBus();
		JsonObject authConfig = new JsonObject();
		authConfig.putString("address", "test.auth");
		authConfig.putString("users_table", "users");
		authConfig.putString("database", "auth_db");
		authConfig.putString("username_db", "root");
		authConfig.putString("password_db", "JHJD89373");
		authConfig.putNumber("session_timeout", 900000);
		// System.out.println(System.getProperty("vertx.modulename"));
		container.deployModule(System.getProperty("vertx.modulename"),
				authConfig, 1, new AsyncResultHandler<String>() {
					@Override
					public void handle(AsyncResult<String> event) {
						if (event.succeeded()) {
							ClientTest.super.start();
						} else {
							event.cause().printStackTrace();
						}
					}
				});
	}

	public void deleteAll() throws Exception {
		Connection conn = null;
		Statement stmt = null;
		Class.forName("com.mysql.jdbc.Driver");
		conn = DriverManager.getConnection(DB_URL, USER, PASS);

		stmt = conn.createStatement();

		String query = "TRUNCATE users";
		int status = stmt.executeUpdate(query);
		assertEquals(status, 0);

		stmt.close();
		conn.close();

	}

	public void storeEntries(JsonObject json) throws Exception {
		Connection conn = null;
		Statement stmt = null;
		Class.forName("com.mysql.jdbc.Driver");

		conn = DriverManager.getConnection(DB_URL, USER, PASS);

		stmt = conn.createStatement();
		String username = json.getString("username");
		String password = json.getString("password");
		String query = "INSERT INTO users VALUES('" + username + "','"
				+ password + "')";
		int status = stmt.executeUpdate(query);
		assertEquals(status, 1);
		stmt.close();
		conn.close();
	}

	@Test
	public void testLoginDeniedEmptyDB() throws Exception {
		deleteAll();
		JsonObject json = new JsonObject();
		json.putString("username", "tim");
		json.putString("password", "foo");

		eb.send("test.auth.login", json, new Handler<Message<JsonObject>>() {

			@Override
			public void handle(Message<JsonObject> reply) {
				assertEquals("denied", reply.body().getString("status"));
				testComplete();
			}
		});

	}

	@Test
	public void testLoginDeniedNonMatchingOthers() throws Exception {
		deleteAll();
		JsonObject user1 = new JsonObject();
		user1.putString("username", "bob");
		user1.putString("password", "widdel");
		storeEntries(user1);

		JsonObject user2 = new JsonObject();
		user2.putString("username", "tom");
		user2.putString("password", "gotwtom");
		storeEntries(user2);

		JsonObject json = new JsonObject();
		json.putString("username", "tim");
		json.putString("password", "foo");

		eb.send("test.auth.login", json, new Handler<Message<JsonObject>>() {

			@Override
			public void handle(Message<JsonObject> reply) {
				assertEquals("denied", reply.body().getString("status"));
				testComplete();
			}
		});

	}

	@Test
	public void testLoginDeniedWrongPassword() throws Exception {
		deleteAll();
		JsonObject user1 = new JsonObject();
		user1.putString("username", "bob");
		user1.putString("password", "widdel");
		storeEntries(user1);

		JsonObject user2 = new JsonObject();
		user2.putString("username", "tim");
		user2.putString("password", "bar");
		storeEntries(user2);

		JsonObject json = new JsonObject();
		json.putString("username", "tim");
		json.putString("password", "foo");

		eb.send("test.auth.login", json, new Handler<Message<JsonObject>>() {

			@Override
			public void handle(Message<JsonObject> reply) {
				assertEquals("denied", reply.body().getString("status"));
				testComplete();
			}
		});
	}

	@Test
	public void testLoginDeniedOtherUserWithSamePassword() throws Exception {
		deleteAll();
		JsonObject user1 = new JsonObject();
		user1.putString("username", "bob");
		user1.putString("password", "foo");
		storeEntries(user1);

		JsonObject user2 = new JsonObject();
		user2.putString("username", "tim");
		user2.putString("password", "bar");
		storeEntries(user2);

		JsonObject json = new JsonObject();
		json.putString("username", "tim");
		json.putString("password", "foo");

		eb.send("test.auth.login", json, new Handler<Message<JsonObject>>() {

			@Override
			public void handle(Message<JsonObject> reply) {
				assertEquals("denied", reply.body().getString("status"));
				testComplete();
			}
		});
	}

	@Test
	public void testLoginOKOneEntryInDB() throws Exception {
		deleteAll();
		JsonObject user1 = new JsonObject();
		user1.putString("username", "tim");
		user1.putString("password", "foo");
		storeEntries(user1);

		JsonObject json = new JsonObject();
		json.putString("username", "tim");
		json.putString("password", "foo");

		eb.send("test.auth.login", json, new Handler<Message<JsonObject>>() {

			@Override
			public void handle(Message<JsonObject> reply) {
				assertEquals("ok", reply.body().getString("status"));
				assertTrue(reply.body().getString("sessionID") != null);
				testComplete();
			}
		});
	}

	@Test
	public void testLoginOKMultipleEntryInDB() throws Exception {
		deleteAll();
		final JsonObject user1 = new JsonObject();
		user1.putString("username", "bob");
		user1.putString("password", "foo");
		storeEntries(user1);

		JsonObject user2 = new JsonObject();
		user2.putString("username", "tom");
		user2.putString("password", "bar");
		storeEntries(user2);

		JsonObject json = new JsonObject();
		json.putString("username", "tim");
		json.putString("password", "foo");
		storeEntries(json);

		eb.send("test.auth.login", json, new Handler<Message<JsonObject>>() {

			@Override
			public void handle(Message<JsonObject> reply) {
				assertEquals("ok", reply.body().getString("status"));
				assertTrue(reply.body().getString("sessionID") != null);
				testComplete();
			}
		});
	}

	@Test
	public void testValidateDeniedNotLoggedIn() throws Exception{
		deleteAll();
		JsonObject json = new JsonObject();
		json.putString("sessionID", "uhhihihih");
		json.putString("password", "foo");
		storeEntries(json);
		
		eb.send("test.auth.authorise", json, new Handler<Message<JsonObject>>() {

			@Override
			public void handle(Message<JsonObject> reply) {
				assertEquals("denied", reply.body().getString("status"));
				testComplete();
			}
		});
	}
	
	@Test
	public void testValidateDeniedInvalidSessionID() throws Exception{
		deleteAll();
		JsonObject json = new JsonObject();
		json.putString("sessionID", "uhhihihih");
		json.putString("password", "foo");
		storeEntries(json);
		
		eb.send("test.auth.authorise", json, new Handler<Message<JsonObject>>() {

			@Override
			public void handle(Message<JsonObject> reply) {
				assertEquals("denied", reply.body().getString("status"));
				testComplete();
			}
		});
	}
	
	@Test
	public void testValidateDeniedLoggedInWrongSessionID() throws Exception{
		deleteAll();
		JsonObject json = new JsonObject();
		json.putString("username", "tim");
		json.putString("password", "foo");
		storeEntries(json);

		eb.send("test.auth.login", json, new Handler<Message<JsonObject>>() {

			@Override
			public void handle(Message<JsonObject> reply) {
				assertEquals("ok", reply.body().getString("status"));
				assertTrue(reply.body().getString("sessionID") != null);
				JsonObject session = new JsonObject();
				session.putString("sessionID", "uhhihihih");
				session.putString("password", "foo");
				eb.send("test.auth.authorise", session, new Handler<Message<JsonObject>>() {
					@Override
					public void handle(Message<JsonObject> reply) {
						assertEquals("denied", reply.body().getString("status"));
						
					}
				});
				testComplete();
			}			
		});
		
	}
	
	@Test
	public void testValidateDeniedLoggedOut() throws Exception{
		deleteAll();
		JsonObject json = new JsonObject();
		json.putString("username", "tim");
		json.putString("password", "foo");
		storeEntries(json);
		
		eb.send("test.auth.login", json, new Handler<Message<JsonObject>>() {

			@Override
			public void handle(Message<JsonObject> reply) {
				assertEquals("ok", reply.body().getString("status"));
				assertTrue(reply.body().getString("sessionID") != null);
				session = reply.body().getString("sessionID");
				
				JsonObject logout = new JsonObject();
				logout.putString("sessionID", session);
				
				eb.send("test.auth.logout",logout, new Handler<Message<JsonObject>>() {

					@Override
					public void handle(Message<JsonObject> reply) {
						assertEquals("ok", reply.body().getString("status"));
						JsonObject auth = new JsonObject();
						auth.putString("sessionID", session);
						
						eb.send("test.auth.authorise", auth, new Handler<Message<JsonObject>>() {

							@Override
							public void handle(Message<JsonObject> reply) {
								assertEquals("denied", reply.body().getString("status"));
								testComplete();
							}
						} );
						
					}
				} );
				
				//testComplete();
			}
		});
	}
	
	@Test
	public void testValidateOK() throws Exception{
		deleteAll();
		JsonObject json = new JsonObject();
		json.putString("username", "tim");
		json.putString("password", "foo");
		storeEntries(json);
		
		eb.send("test.auth.login", json, new Handler<Message<JsonObject>>() {
			
			@Override
			public void handle(Message<JsonObject> reply) {
				assertEquals("ok", reply.body().getString("status"));
				assertTrue(reply.body().getString("sessionID") != null);
				session = reply.body().getString("sessionID");
				
				JsonObject auth = new JsonObject();
				auth.putString("sessionID", session);
				
				eb.send("test.auth.authorise", auth, new Handler<Message<JsonObject>>() {
					
					@Override
					public void handle(Message<JsonObject> reply) {
						assertEquals("ok", reply.body().getString("status"));
						testComplete();
					}
				});			
			}
		});		
	}
	
	@Test
	public void testLoginMoreThanOnce() throws Exception{
		deleteAll();
		JsonObject json = new JsonObject();
		json.putString("username", "tim");
		json.putString("password", "foo");
		storeEntries(json);
		
		eb.send("test.auth.login", json, new Handler<Message<JsonObject>>() {
			
			@Override
			public void handle(Message<JsonObject> reply) {
				assertEquals("ok", reply.body().getString("status"));
				testComplete();
			}
		} );
	}
	
	@Test
	public void testLoginMoreThanOnceThenLogout() throws Exception{
		deleteAll();
		JsonObject json = new JsonObject();
		json.putString("username", "tim");
		json.putString("password", "foo");
		storeEntries(json);
		
		eb.send("test.auth.login", json, new Handler<Message<JsonObject>>() {
			
			@Override
			public void handle(Message<JsonObject> reply) {
				assertEquals("ok", reply.body().getString("status"));
				session = reply.body().getString("sessionID");
				JsonObject json = new JsonObject();
				json.putString("username", "tim");
				json.putString("password", "foo");				
				eb.send("test.auth.login", json, new Handler<Message<JsonObject>>() {					
					@Override
					public void handle(Message<JsonObject> reply) {
						assertEquals("ok", reply.body().getString("status"));
						JsonObject logout = new JsonObject();
						logout.putString("sessionID", reply.body().getString("sessionID"));
						eb.send("test.auth.logout",logout, new Handler<Message<JsonObject>>() {							
							@Override
							public void handle(Message<JsonObject> reply) {
								assertEquals("ok", reply.body().getString("status"));	
								testComplete();
							}
						});
					}
				} );
			}
		} );
	}
	
}
