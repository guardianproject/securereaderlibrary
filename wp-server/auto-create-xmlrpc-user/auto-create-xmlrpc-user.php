<?php
/**
 * @package auto-create-xmlrpc-user
 * @version 0.1
 */
/*
Plugin Name: Auto Create XML-RPC User
Plugin URI: http://nothere.com
Description: Automatically create users for people publishing via XML-RPC
Author: No One
Version: 0.1
Author URI: http://nothere.com/
*/

function acxu_createUser($args) 
{
	global $wp_xmlrpc_server;
	$wp_xmlrpc_server->escape( $args );

	//$nickname = $args[0];
	$nickname = $args;
	//$password = $args[1];

	//if ( ! $user = $wp_xmlrpc_server->login( $username, $password ) )
	//    return $wp_xmlrpc_server->error;

	$user_name = time() . "_" . rand(1000,9999);
	$user_email = $user_name . "@bbuser.org";
	if (!username_exists($user_name) && !email_exists($user_email)) {
		$random_password = wp_generate_password( $length=12, $include_standard_special_chars=false );
		$user_id = wp_create_user( $user_name, $random_password, $user_email );

		if ($nickname == "") {
			$nickname = $user_email;
		}

		// Update the user to set the nickname
		wp_update_user(
  			array(
    				'ID' => $user_id,
    				'nickname' => $nickname
  			)
		);

		// Get the user object to set the user's role
		$wp_user_object = new WP_User($user_id);

		//http://en.support.wordpress.com/user-roles/
		$wp_user_object->set_role('author');

		return $user_name . " " . $random_password;
	} else {
		return "ERROR: User Name or Email Already Exists";
	}
}

function acxu_new_xmlrpc_methods($methods) 
{
	$methods['acxu.createUser'] = 'acxu_createUser';
	return $methods;   
}

add_filter( 'xmlrpc_methods', 'acxu_new_xmlrpc_methods');
?>
