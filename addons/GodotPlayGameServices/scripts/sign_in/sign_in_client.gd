@icon("res://addons/GodotPlayGameServices/assets/icons/sign_in_client.svg")
class_name PlayGamesSignInClient extends Node
## Client with sign in functionality.
##
## This autoload exposes methods and signals to control the player sign in process.[br]
## [br]
## If the player is already signed in into Google Play Games, the plugin does
## a check at startup, so usually you don't have to use these methods. Use them only 
## to provide a manual way for the user to sign in.

## Signal emitted after calling the [method is_authenticated] or [method sign_in] method.[br]
## [br]
## [param is_authenticated]: Indicates if the user is authenticated or not.
signal user_authenticated(is_authenticated: bool)

## Signal emitted when [method is_authenticated] or [method sign_in] fails or returns an unauthenticated result.
## Emitted immediately before [signal user_authenticated] with [code]false[/code].[br]
## [br]
## [param status_code]: Google Play [code]ApiException[/code] status code (e.g. [code]10[/code] for DEVELOPER_ERROR, [code]7[/code] for NETWORK_ERROR),
## [code]0[/code] when [code]reason == "not_authenticated"[/code], or [code]-1[/code] for non-API task exceptions/cancellation.[br]
## [param reason]: Machine-readable category: [code]"api_exception"[/code], [code]"task_exception"[/code], [code]"task_canceled"[/code], or [code]"not_authenticated"[/code].[br]
## [param message]: Human-readable diagnostic description including exception class, status name, and localized message.[br]
## [param source]: Originating method name ([code]"isAuthenticated"[/code] or [code]"signIn"[/code]).
signal user_authentication_failed(status_code: int, reason: String, message: String, source: String)

## Signal emitted after calling the [method request_server_side_access] method.[br]
## [br]
## [param token]: The OAuth 2.0 authorization code as a string.
signal server_side_access_requested(token:String)

func _ready() -> void:
	_connect_signals()

func _connect_signals() -> void:
	if GodotPlayGameServices.android_plugin:
		GodotPlayGameServices.android_plugin.userAuthenticated.connect(func(is_authenticated: bool):
			user_authenticated.emit(is_authenticated)
		)
		if GodotPlayGameServices.android_plugin.has_signal("userAuthenticationFailed"):
			GodotPlayGameServices.android_plugin.userAuthenticationFailed.connect(
				func(status_code: int, reason: String, message: String, source: String):
					user_authentication_failed.emit(status_code, reason, message, source)
			)
		GodotPlayGameServices.android_plugin.serverSideAccessRequested.connect(func(token: String):
			server_side_access_requested.emit(token)
		)

## Use this method to check if the user is already authenticated. If the user is authenticated,
## a popup will be shown on screen.[br]
## [br]
## The method emits the [signal user_authenticated] signal, and on failure or unauthenticated result
## also emits [signal user_authentication_failed] immediately beforehand.
func is_authenticated() -> void:
	if GodotPlayGameServices.android_plugin:
		GodotPlayGameServices.android_plugin.isAuthenticated()

## Use this method to provide a manual way to the user for signing in.[br]
## [br]
## The method emits the [signal user_authenticated] signal, and on failure or unauthenticated result
## also emits [signal user_authentication_failed] immediately beforehand.
func sign_in() -> void:
	if GodotPlayGameServices.android_plugin:
		GodotPlayGameServices.android_plugin.signIn()

## Returns diagnostic information about Google Play Services availability and the installed
## Google Play Games app ([code]com.google.android.play.games[/code]) on the current device.[br]
## [br]
## Returns an empty [Dictionary] when the Android plugin singleton is unavailable (e.g. desktop/editor).[br]
## Keys when available: [code]play_services_status_code[/code] (int), [code]play_services_status_name[/code] (String),
## [code]play_services_available[/code] (bool), [code]play_services_resolvable[/code] (bool),
## [code]play_games_app_installed[/code] (bool), [code]play_games_app_enabled[/code] (bool),
## [code]play_games_app_version_name[/code] (String), and [code]play_games_app_version_code[/code] (int).
func get_play_services_diagnostics() -> Dictionary:
	if GodotPlayGameServices.android_plugin and GodotPlayGameServices.android_plugin.has_method("getPlayServicesDiagnostics"):
		return GodotPlayGameServices.android_plugin.getPlayServicesDiagnostics()
	return {}

## Requests server-side access to Play Games Services for the currently signed-in player.
## When requested, an authorization code is returned that can be used by your server to exchange
## for an access token that can be used by your server to access the Play Games Services web APIs.[br]
## [br]
## The method emits the [signal server_side_access_requested] signal.[br]
## [br]
## [param server_client_id]: The client ID of the server that will perform the authorization code flow exchange.[br]
## [param force_refresh_token]: If true, when the returned authorization code is exchanged, a refresh
## token will be included in addition to an access token.
func request_server_side_access(server_client_id: String, force_refresh_token: bool) -> void:
	if GodotPlayGameServices.android_plugin:
		GodotPlayGameServices.android_plugin.requestServerSideAccess(server_client_id, force_refresh_token)
