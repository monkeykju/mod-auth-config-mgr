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
		this.setEb(this.vertx.eventBus());

		JsonObject mysqlConfig = new JsonObject();
		mysqlConfig.putString("address", "test.mysql");
		mysqlConfig.putString("database", "auth_db");
		mysqlConfig.putString("username", "root");
		mysqlConfig.putString("password", "JHJD89373");
		mysqlConfig.putString("connection", "MySQL");
		this.container.deployModule("io.vertx~mod-mysql-postgresql~0.3.0-SNAPSHOT",
				mysqlConfig, new AsyncResultHandler<String>() {

					@Override
					public void handle(AsyncResult<String> event) {
						if (event.succeeded()) {
							JsonObject authConfig = new JsonObject();
							authConfig.putString("address", "test.auth");
							authConfig.putString("users_table", "users");
							authConfig.putString("config_table", "config");
							authConfig.putString("persistor_address","test.mysql");
							authConfig.putNumber("session_timeout", 200);
							TimeOutTest.this.getContainer().deployModule(
									System.getProperty("vertx.modulename"),
									authConfig, 1,
									new AsyncResultHandler<String>() {
										@SuppressWarnings("synthetic-access")
										@Override
										public void handle(
												AsyncResult<String> event1) {
											if (event1.succeeded()) {							
												TimeOutTest.super.start();
											} else {
												event1.cause().printStackTrace();
											}
										}
									});
						} else {
							event.cause().printStackTrace();
						}

					}
				});
	}

	@Test
	public void testSession() throws Exception {

		JsonObject json = new JsonObject();
		
		String command2 = "truncate config;";
		
		json.putString("action", "raw");
		json.putString("command", command2);
		this.getEb().send("test.mysql", json, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> reply) {				
				assertEquals("ok", reply.body().getString("status"));
				//testComplete();
				String command = "DELETE FROM users";
				JsonObject json1 = new JsonObject();
				json1.putString("action", "raw");
				json1.putString("command", command);
				TimeOutTest.this.getEb().send("test.mysql", json1, new Handler<Message<JsonObject>>() {
					@Override
					public void handle(Message<JsonObject> reply1) {		
						System.out.println(reply1.body().toString());
						assertEquals("ok", reply1.body().getString("status"));
						try {
							storeEntries("tim", "foo");
						} catch (Exception e) {
							e.printStackTrace();
						}
						//testComplete();
					}
				});
				
			}
		});
		
		//String command = "truncate users;";
		

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
		getEb().send("test.mysql", json2, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> reply) {
				System.out.println(reply.body().toString());
				assertEquals("ok", reply.body().getString("status"));
				storeMoudle("tim", "II", "{\"access\":\"ok\"}");
				//testComplete();
			}
		});
	}
	
	public void storeMoudle(String username, String moduleName, String config){
		//insert into config(user_id,module_name,configuration) values ((select user_id from users where username="tim"),"II","{\"test\":\"ok\"}");
		String command = "INSERT INTO config(user_id,module_name,configuration) values (( SELECT user_id FROM users WHERE username='"
				+username+"'),'"+moduleName+"','"+config+"');";
		JsonObject json2 = new JsonObject();
		json2.putString("action", "raw");
		json2.putString("command", command);
		getEb().send("test.mysql", json2, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> reply) {
				System.out.println(reply.body().toString());
				assertEquals("ok", reply.body().getString("status"));
				try {
					testSessionTimeout();
				} catch (Exception e) {
					e.printStackTrace();
				}
				//testComplete();
			}
		});
		
	}

	public void testSessionTimeout() throws Exception {
		JsonObject json = new JsonObject();
		json.putString("username", "tim");
		json.putString("password", "foo");
		json.putString("module_name", "II");
		getEb().send("test.auth.login", json, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> reply) {
				assertEquals("ok", reply.body().getString("status"));
				System.out.println(reply.body().toString());
				assertTrue(reply.body().getString("sessionID") != null);
				TimeOutTest.this.session = reply.body().getString("sessionID");
				JsonObject authObj = new JsonObject();
				authObj.putString("sessionID", TimeOutTest.this.session);
				authObj.putString("module_name", "II");
				getEb().send("test.auth.authorise", authObj,
						new Handler<Message<JsonObject>>() {
							@Override
							public void handle(Message<JsonObject> event) {
								System.out.println(event.body().toString());
								assertEquals("ok",event.body().getString("status"));
								TimeOutTest.this.getVertx().setTimer(1000, new Handler<Long>() {

									@Override
									public void handle(Long event1) {
										JsonObject authObj1 = new JsonObject();
										authObj1.putString("sessionID", TimeOutTest.this.session);

										authObj1.putString("module_name", "II");

										getEb().send("test.auth.authorise",authObj1,
												new Handler<Message<JsonObject>>() {
													@Override
													public void handle(Message<JsonObject> reply1) {
														assertEquals("denied",reply1.body().getString("status"));
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

	public EventBus getEb() {
		return this.eb;
	}

	public void setEb(EventBus eb) {
		this.eb = eb;
	}

}
