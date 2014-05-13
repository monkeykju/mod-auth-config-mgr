package org.vertx.mods.java;


//
import static  org.vertx.testtools.VertxAssert.assertEquals;
//import static org.junit.Assert.assertEquals;
import static org.vertx.testtools.VertxAssert.assertTrue;
import static org.vertx.testtools.VertxAssert.testComplete;

import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.testtools.TestVerticle;

public class ClientTest extends TestVerticle {

	private EventBus eb;
	public String session = "";
	public String new_session = "";

	@Override
	public void start() {
		eb = vertx.eventBus();

		JsonObject mysqlConfig = new JsonObject();
		mysqlConfig.putString("address", "test.mysql");
		mysqlConfig.putString("database", "auth_db");
		mysqlConfig.putString("username", "root");
		mysqlConfig.putString("password", "JHJD89373");
		mysqlConfig.putString("connection", "MySQL");
		container.deployModule("io.vertx~mod-mysql-postgresql~0.3.0-SNAPSHOT",
				mysqlConfig, new AsyncResultHandler<String>() {

					@Override
					public void handle(AsyncResult<String> event) {
						if (event.succeeded()) {
							JsonObject authConfig = new JsonObject();
							authConfig.putString("address", "test.auth");
							authConfig.putString("users_table", "users");
							authConfig.putString("persistor_address","test.mysql");
							authConfig.putNumber("session_timeout", 900000);
							container.deployModule(
									System.getProperty("vertx.modulename"),
									authConfig, 1,
									new AsyncResultHandler<String>() {
										@Override
										public void handle(
												AsyncResult<String> event) {
											if (event.succeeded()) {
												ClientTest.super.start();
											} else {
												event.cause().printStackTrace();
											}
										}
									});
						} else {
							event.cause().printStackTrace();
						}

					}
				});
	}

	public void deleteAll() throws Exception {

		String command = "truncate users;";
		JsonObject json = new JsonObject();
		json.putString("action", "raw");
		json.putString("command", command);
		eb.send("test.mysql", json, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> reply) {				
				assertEquals("ok", reply.body().getString("status"));
				//testComplete();
			}
		});

	}

	public void storeEntries(String username, String password) throws Exception {
		JsonObject json2 = new JsonObject();
		json2.putString("action", "insert");
		json2.putString("table", "users");
		JsonArray fields = new JsonArray().add("username").add("password");
		JsonArray values = new JsonArray().add(new JsonArray().add(username).add(password));
		json2.putArray("fields", fields);
		json2.putArray("values", values);
		//String QUERY = "INSERT INTO users(username,password) VALUES('"+username+"','"+password+"');";
		eb.send("test.mysql", json2, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> reply) {
				assertEquals("ok", reply.body().getString("status"));
				//testComplete();
			}
		});
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
				System.out.println(reply.body().toString());
				assertEquals("denied", reply.body().getString("status"));
				testComplete();
			}
		});

	}

	@Test
	public void testLoginDeniedNonMatchingOthers() throws Exception {
		deleteAll();
		storeEntries("bob","widdel");
		storeEntries("tom","gotwtom");

		JsonObject json = new JsonObject();
		json.putString("username", "tim");
		json.putString("password", "foo");

		eb.send("test.auth.login", json, new Handler<Message<JsonObject>>() {

			@Override
			public void handle(Message<JsonObject> reply) {
				System.out.println(reply.body().toString());
				assertEquals("denied", reply.body().getString("status"));
				testComplete();
			}
		});

	}

	@Test
	public void testLoginDeniedWrongPassword() throws Exception {
		deleteAll();
		storeEntries("bob","widdel");
		storeEntries("tim","bar");

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
		storeEntries("bob","foo");
		storeEntries("tim","bar");

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
		storeEntries("tim","foo");

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
		
		storeEntries("bob","foo");
		storeEntries("tom","bar");
		storeEntries("tim","foo");
		
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
	public void testValidateDeniedNotLoggedIn() throws Exception{
		deleteAll();
		storeEntries("tim","foo");
		JsonObject json = new JsonObject();
		json.putString("sessionID", "uhhihihih");
		json.putString("password", "foo");
				
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
		storeEntries("tim","foo");
		JsonObject json = new JsonObject();
		json.putString("sessionID", "uhhihihih");
		json.putString("password", "foo");
		
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
		storeEntries("tim","foo");
		JsonObject json = new JsonObject();
		json.putString("username", "tim");
		json.putString("password", "foo");

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
		storeEntries("tim","foo");
		JsonObject json = new JsonObject();
		json.putString("username", "tim");
		json.putString("password", "foo");

		
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
		storeEntries("tim","foo");
		JsonObject json = new JsonObject();
		json.putString("username", "tim");
		json.putString("password", "foo");
		
		eb.send("test.auth.login", json, new Handler<Message<JsonObject>>() {
			
			@Override
			public void handle(Message<JsonObject> reply) {
				assertEquals("ok", reply.body().getString("status"));
				assertTrue(reply.body().getString("sessionID") != null);
				session = reply.body().getString("sessionID");
				
				JsonObject auth = new JsonObject();
				auth.putString("sessionID", session);
				auth.putString("password", "foo");
				
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
		storeEntries("tim","foo");
		JsonObject json = new JsonObject();
		json.putString("username", "tim");
		json.putString("password", "foo");
		
		eb.send("test.auth.login", json, new Handler<Message<JsonObject>>() {
			
			@Override
			public void handle(Message<JsonObject> reply) {
				assertEquals("ok", reply.body().getString("status"));
				session = reply.body().getString("sessionID");
				JsonObject auth = new JsonObject();
				auth.putString("username", "tim");
				auth.putString("password", "foo");
				eb.send("test.auth.login", auth, new Handler<Message<JsonObject>>() {
					
					@Override
					public void handle(Message<JsonObject> reply) {
						assertEquals("ok", reply.body().getString("status"));
						new_session = reply.body().getString("sessionID");
						assertTrue(new_session != session);
						JsonObject user1 = new JsonObject();
						user1.putString("sessionID", session);
						eb.send("test.auth.logout", user1, new Handler<Message<JsonObject>>() {							
							@Override
							public void handle(Message<JsonObject> reply) {
								assertEquals("error", reply.body().getString("status"));
								assertEquals("Not logged in", reply.body().getString("message"));
								JsonObject user1 = new JsonObject();
								user1.putString("sessionID", session);
								eb.send("test.auth.authorise",user1,new Handler<Message<JsonObject>>() {									
									@Override
									public void handle(Message<JsonObject> reply) {									
										assertEquals("denied", reply.body().getString("status"));
										JsonObject user2 = new JsonObject();
										user2.putString("sessionID", new_session);
										eb.send("test.auth.authorise",user2, new Handler<Message<JsonObject>>() {											
											@Override
											public void handle(Message<JsonObject> reply) {
												assertEquals("ok", reply.body().getString("status"));
												JsonObject user2 = new JsonObject();
												user2.putString("sessionID", new_session);
												eb.send("test.auth.logout",user2, new Handler<Message<JsonObject>>() {							
													@Override
													public void handle(Message<JsonObject> reply) {
														assertEquals("ok", reply.body().getString("status"));	
														JsonObject user2 = new JsonObject();
														user2.putString("sessionID", new_session);
														eb.send("test.auth.authorise", user2, new Handler<Message<JsonObject>>() {														
															@Override
															public void handle(Message<JsonObject> reply) {
																assertEquals("denied", reply.body().getString("status"));
																testComplete();
															}
														});
													}
												});
											}
										});
									}
								});
							}
						});
					}
				});
			}
		} );
	}
	
	@Test
	public void testLoginMoreThanOnceThenLogout() throws Exception{
		deleteAll();
		storeEntries("tim","foo");
		JsonObject json = new JsonObject();
		json.putString("username", "tim");
		json.putString("password", "foo");
		
		eb.send("test.auth.login", json, new Handler<Message<JsonObject>>() {
			
			@Override
			public void handle(Message<JsonObject> reply) {
				System.out.println(reply.body().toString());
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
