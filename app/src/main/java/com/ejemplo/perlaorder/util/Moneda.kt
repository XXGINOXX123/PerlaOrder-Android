package com.ejemplo.perlaorder.util

import java.util.Locale

fun Long.comoSoles(): String = String.format(Locale.US, "S/ %.2f", this / 100.0)