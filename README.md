# Authentication/Authorisation and Configuaration Manager

This is a basic auth manager that verifies usernames and passwords in a MySQL database and generates time-limited session ids. These session ids can be passed around the event bus.

The auth manager can also authorise a session id. This allows session ids to be passed around the event bus and validated if particular busmods want to find out if the user is authorised.

Sessions time out after a certain amount of time. After that time, they will not verify as ok.

This busmod, is used in the web application tutorial to handle simple user/password authorisation for the application.

## Dependencies

This busmod requires mod-mysql-postgresql to be running to allow searching for usernames and passwords, mapping config with module-name.
We need deploy mod-mysql-postgresql with config:
```
{
  "address" : <event-bus-addres-to-listen-on>,
  "connection" : <MySQL|PostgreSQL>,
  "host" : <your-host>,
  "port" : <your-port>,
  "maxPoolSize" : <maximum-number-of-open-connections>,
  "username" : <your-username>,
  "password" : <your-password>,
  "database" : <name-of-your-database>
}
```
Simple example:
```
{
  "address" : "test.mysql"
  "database" : "auth_db"
  "username" : "root"
  "password" : "abc123"
  "connection" : "MySQL"
}
```

## Name

The module name is `auth-conf-mgr`.

## Users table
Structure of users table in MySQL:

Users table:
```
users(
  user_id int auto_increment primary key,
  username varchar(50) unique,
  password varchar(50)
  );
```
Config table:
```
config(
  user_id int,
  module_name varchar(50),
  configuration text,
  primary key(user_id,module_name),
  foreign key(user_id) references users(user_id)
  );
```

## Configuration

This busmod takes the following configuration:

    {
    "address": <address>,
    "users_table": <user_table_in_mysql>,
    "config_table": <config_table_in_mysql>,
    "persistor_address": <mysql_persistor_address>,
    "session_timeout": <session_timeout>
    }

For example:

    {
    "address": "test.auth-conf",
    "users_table": "users",
    "config_table": "config",
    "persistor_address": "test.mysql",
    "session_timeout": 900000
    }

Let's take a look at each field in turn:

* `address` The main address for the busmod. Optional field. Default value is `vertx.auth-conf-mgr`
* `users_table` The MySQL table in which to search for usernames and passwords. Optional field. Default value is `users`.
* `config_table` The MySQL table in which to search for module users can access. Optional field. Default value is `config`.
* `session_timeout` Timeout of a session, in milliseconds. This field is optional. Default value is `1800000` (30 minutes).
* `persistor_address` Busmod address of mysql. Default is `campudus.asyncdbs`.

## Operations

### Login

Login with a username and password and obtain a session id if successful.

To login, send a JSON message to the address given by the main address of the busmod + `.login`. For example if the main address is `test.authManager`, you send the message to `test.authManager.login`.

The JSON message should have the following structure:

    {
        "username": <username>,
        "password": <password>,
        "module_name": <access_module_name>
    }

If login is successful a reply will be returned:

    {
        "status": "ok",
        "sessionID": <sesson_id>,
        "config": <json_config_object>
    }

Where `session_id` is a unique session id.

If login is unsuccessful the following reply will be returned:

    {
        "status": "denied"
    }

### Logout

Logout and close a session. Any subsequent attempts to validate the session id will fail.

To login, send a JSON message to the address given by the main address of the busmod + `.logout`. For example if the main address is `test.authManager`, you send the message to `test.authManager.logout`.

The JSON message should have the following structure:

    {
        "sessionID": <session_id>
    }

Where `session_id` is a unique session id.

If logout is successful the following reply will be returned:

    {
        "status": "ok"
    }

Otherwise, if the session id is not known about:

    {
        "status": "error"
    }

### Authorise

Authorise a session id.

To authorise, send a JSON message to the address given by the main address of the busmod + `.authorise`. For example if the main address is `test.authManager`, you send the message to `test.authManager.authorise`.

The JSON message should have the following structure:

    {
        "sessionID": <session_id>,
        "module_name": <access_module_name>
    }

Where `session_id` is a unique session id.

If the session is valid the following reply will be returned:

    {
        "status": "ok",
        "username": <username>,
        "config": <json_config_object>
    }

Where `username` is the username of the user.

Otherwise, if the session is not valid. I.e. it has expired or never existed in the first place.

    {
        "status": "denied"
    }
