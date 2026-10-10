package com.ejemplo.perlaorder.data.network.dto

data class LoginRequest(val usuario: String, val contrasena: String)

data class LoginResponse(val token: String, val expiraEn: Long, val usuario: String, val rol: String)