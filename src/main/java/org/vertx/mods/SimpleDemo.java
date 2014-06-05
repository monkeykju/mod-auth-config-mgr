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
		this.setEb(this.vertx.eventBus());
		JsonObject mysqlConfig = new JsonObject();
		mysqlConfig.putString("address", "test.mysql");
		mysqlConfig.putString("database", "auth_db");
		mysqlConfig.putString("username", "root");
		mysqlConfig.putString("password", "abc123");
		mysqlConfig.putString("connection", "MySQL");
		this.container.deployModule("io.vertx~mod-mysql-postgresql~0.3.0-SNAPSHOT",
				mysqlConfig, new AsyncResultHandler<String>() {
					@Override
					public void handle(AsyncResult<String> event) {
						if (event.succeeded()) {
							JsonObject authConfig = new JsonObject();
							authConfig.putString("address", "test.auth");
							authConfig.putString("users_table", "users");
							authConfig.putString("persistor_address","test.mysql");
							authConfig.putString("config_table", "config");
							authConfig.putNumber("session_timeout", 900000);
							SimpleDemo.this.getContainer().deployModule("dsvn~auth-config-mgr~0.1.0",authConfig, 1,
									new AsyncResultHandler<String>() {
										@Override
										public void handle(
												AsyncResult<String> event1) {
											if (event1.succeeded()) {
												System.out.println("------------------------\n");
												
												SimpleDemo.this.getEb().send("test.auth.login", SimpleDemo.this.findMsg,
														new Handler<Message<JsonObject>>() {
															@Override
															public void handle(Message<JsonObject> reply) {
																System.out.println("------------------------\n");
																System.out.println(reply.body().toString());
																SimpleDemo.this.session = reply.body().getString("sessionID");	
																JsonObject authObj = new JsonObject().putString("sessionID", SimpleDemo.this.session);
																authObj.putString("module_name", "II");
																getEb().send("test.auth.authorise", authObj,
																		new Handler<Message<JsonObject>>() {
																			@Override
																			public void handle(Message<JsonObject> reply1) {
																				System.out.println(reply1.body().toString());
																				JsonObject authObj1 = new JsonObject().putString("sessionID", SimpleDemo.this.session);
																				authObj1.putString("module_name", "EM");
																				getEb().send("test.auth.authorise", authObj1,
																						new Handler<Message<JsonObject>>() {
																							@Override
																							public void handle(Message<JsonObject> reply2) {
																								System.out.println(reply2.body().toString());
																								JsonObject authObj2 = new JsonObject().putString("sessionID", SimpleDemo.this.session);
																								getEb().send("test.auth.logout", authObj2,
																										new Handler<Message<JsonObject>>() {
																											@Override
																											public void handle(Message<JsonObject> reply3) {
																												System.out.println(reply3.body().toString());
																											}
																								});
																							}
																						});
																			}
																		});
															}
														});
												
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

	public EventBus getEb() {
		return this.eb;
	}

	public void setEb(EventBus eb) {
		this.eb = eb;
	}
}
