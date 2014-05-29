package org.vertx.mods;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Verticle;

public class SimpleDemo extends Verticle {

	private EventBus eb;
	public String session = "";
	public String body = "";

	public JsonObject findMsg = new JsonObject().putString("username", "tim")
			.putString("password", "foo").putString("module_name", "II");
	
	@Override
	public void start() {
		eb = vertx.eventBus();
		JsonObject mysqlConfig = new JsonObject();
		mysqlConfig.putString("address", "test.mysql");
		mysqlConfig.putString("database", "auth_db");
		mysqlConfig.putString("username", "root");
		mysqlConfig.putString("password", "abc123");
		mysqlConfig.putString("connection", "MySQL");
		container.deployModule("io.vertx~mod-mysql-postgresql~0.3.0-SNAPSHOT",
				mysqlConfig, new AsyncResultHandler<String>() {

					@Override
					public void handle(AsyncResult<String> event) {
						if (event.succeeded()) {
							JsonObject authConfig = new JsonObject();
							authConfig.putString("address", "test.auth");
							authConfig.putString("users_table", "users");
							authConfig.putString("persistor_address",
									"test.mysql");
							authConfig.putString("config_table", "config");
							authConfig.putNumber("session_timeout", 900000);
							container.deployModule(
									"dsvn~auth-config-mgr~0.1.0",
									authConfig, 1,
									new AsyncResultHandler<String>() {
										@Override
										public void handle(
												AsyncResult<String> event) {
											if (event.succeeded()) {
												// TimeOutTest.super.start();
												System.out.println("------------------------\n");
												
												eb.send("test.auth.login", findMsg,
														new Handler<Message<JsonObject>>() {
															@Override
															public void handle(Message<JsonObject> reply) {
																System.out.println("------------------------\n");
																System.out.println(reply.body().toString());
																session = reply.body().getString("sessionID");	
																JsonObject authObj = new JsonObject().putString("sessionID", session);
																authObj.putString("module_name", "II");
																eb.send("test.auth.authorise", authObj,
																		new Handler<Message<JsonObject>>() {
																			@Override
																			public void handle(Message<JsonObject> reply) {
																				System.out.println(reply.body().toString());
																				JsonObject authObj = new JsonObject().putString("sessionID", session);
																				authObj.putString("module_name", "EM");
																				eb.send("test.auth.authorise", authObj,
																						new Handler<Message<JsonObject>>() {
																							@Override
																							public void handle(Message<JsonObject> reply) {
																								System.out.println(reply.body().toString());
																								JsonObject authObj = new JsonObject().putString("sessionID", session);
																								eb.send("test.auth.logout", authObj,
																										new Handler<Message<JsonObject>>() {
																											@Override
																											public void handle(Message<JsonObject> reply) {
																												System.out.println(reply.body().toString());
																											}
																								});
																							}
																						});
																			}
																		});
															}
														});
												
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
}
