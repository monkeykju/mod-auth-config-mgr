package org.vertx.mods.java;

import static org.vertx.testtools.VertxAssert.testComplete;
import static org.vertx.testtools.VertxAssert.assertEquals;
//import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.testtools.TestVerticle;

public class TimeOutTest extends TestVerticle {

	private EventBus eb;
	String session = "";

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
							authConfig.putNumber("session_timeout", 200);
							container.deployModule(
									System.getProperty("vertx.modulename"),
									authConfig, 1,
									new AsyncResultHandler<String>() {
										@Override
										public void handle(
												AsyncResult<String> event) {
											if (event.succeeded()) {
												TimeOutTest.super.start();
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
	public void testSessionTimeout() throws Exception {
		deleteAll();
		storeEntries("tim","foo");
		//createTable();
		JsonObject json = new JsonObject();
		json.putString("username", "tim");
		json.putString("password", "foo");
		//storeEntries("tim","foo");
		eb.send("test.auth.login", json, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> reply) {
				assertEquals("ok", reply.body().getString("status"));
				System.out.println(reply.body().toString());
				assertTrue(reply.body().getString("sessionID") != null);
				session = reply.body().getString("sessionID");
				JsonObject authObj = new JsonObject();
				authObj.putString("sessionID", session);
				authObj.putString("password", "foo");
				eb.send("test.auth.authorise", authObj,
						new Handler<Message<JsonObject>>() {
							@Override
							public void handle(Message<JsonObject> event) {
								System.out.println(event.body().toString());
								assertEquals("ok",event.body().getString("status"));
								vertx.setTimer(1000, new Handler<Long>() {

									@Override
									public void handle(Long event) {
										JsonObject authObj = new JsonObject();
										authObj.putString("sessionID", session);

										authObj.putString("password", "foo");

										eb.send("test.auth.authorise",authObj,
												new Handler<Message<JsonObject>>() {
													@Override
													public void handle(Message<JsonObject> reply) {
														assertEquals("denied",reply.body().getString("status"));
													}
												});
										testComplete();
									}
								});
							}
						});
				// testComplete();
			}
		});
		//testComplete();
	}

}
