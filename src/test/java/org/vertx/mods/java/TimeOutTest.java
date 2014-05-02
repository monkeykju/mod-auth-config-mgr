package org.vertx.mods.java;

import static org.junit.Assert.assertEquals;
import static org.vertx.testtools.VertxAssert.assertTrue;
import static org.vertx.testtools.VertxAssert.testComplete;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.testtools.TestVerticle;

public class TimeOutTest extends TestVerticle {

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
		authConfig.putNumber("session_timeout", 200);
		container.deployModule(System.getProperty("vertx.modulename"),
				authConfig, 1, new AsyncResultHandler<String>() {
					@Override
					public void handle(AsyncResult<String> event) {
						if (event.succeeded()) {
							TimeOutTest.super.start();
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
		String query = "INSERT INTO users VALUES('"+username+"','"+password+"')";		
		int status = stmt.executeUpdate(query);
		assertEquals(status, 1);
		stmt.close();
		conn.close();
	}

	@Test
	public void testSessionTimeout() throws Exception {
		deleteAll();
		final JsonObject json = new JsonObject();
		json.putString("username", "tim");
		json.putString("password", "foo");
		storeEntries(json);
		
		eb.send("test.auth.login",json, new Handler<Message<JsonObject>>() {
			
			@Override
			public void handle(Message<JsonObject> reply) {
				assertEquals("ok", reply.body().getString("status"));
				System.out.println(reply.body().toString());
				assertTrue(reply.body().getString("sessionID") != null);
				session = reply.body().getString("sessionID");
				JsonObject authObj = new JsonObject();
				authObj.putString("sessionID", session);
				authObj.putString("password", "foo");			
				eb.send("test.auth.authorise", authObj, new Handler<Message<JsonObject>>() {
					
					@Override
					public void handle(Message<JsonObject> event) {
						System.out.println(event.body().toString());
						assertEquals("ok",event.body().getString("status") );
						vertx.setTimer(1000, new Handler<Long>() {
							
							@Override
							public void handle(Long event) {
								JsonObject authObj = new JsonObject();
								authObj.putString("sessionID", session);
								
								authObj.putString("password", "foo");
								
								eb.send("test.auth.authorise", authObj, new Handler<Message<JsonObject>>() {									
									@Override
									public void handle(Message<JsonObject> reply) {
										assertEquals("denied",reply.body().getString("status") );
										
									}
								});
								testComplete();
							}
						});
					}
				} );	
				//testComplete();
			}			
		});		
		//testComplete();
	}

}
